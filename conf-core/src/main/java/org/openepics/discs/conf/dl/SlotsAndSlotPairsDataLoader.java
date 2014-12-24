/*
 * Copyright (c) 2014 European Spallation Source
 * Copyright (c) 2014 Cosylab d.d.
 *
 * This file is part of Controls Configuration Database.
 *
 * Controls Configuration Database is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the License,
 * or any newer version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see https://www.gnu.org/licenses/gpl-2.0.txt
 */
package org.openepics.discs.conf.dl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.openepics.discs.conf.dl.common.AbstractDataLoader;
import org.openepics.discs.conf.dl.common.DataLoaderResult;
import org.openepics.discs.conf.dl.common.ErrorMessage;
import org.openepics.discs.conf.dl.common.ValidationMessage;
import org.openepics.discs.conf.ejb.ComptypeEJB;
import org.openepics.discs.conf.ejb.PropertyEJB;
import org.openepics.discs.conf.ejb.SlotEJB;
import org.openepics.discs.conf.ejb.SlotPairEJB;
import org.openepics.discs.conf.ejb.SlotRelationEJB;
import org.openepics.discs.conf.ent.ComponentType;
import org.openepics.discs.conf.ent.PositionInformation;
import org.openepics.discs.conf.ent.Property;
import org.openepics.discs.conf.ent.Slot;
import org.openepics.discs.conf.ent.SlotPair;
import org.openepics.discs.conf.ent.SlotPropertyValue;
import org.openepics.discs.conf.ent.SlotRelation;
import org.openepics.discs.conf.ent.SlotRelationName;
import org.openepics.discs.conf.util.Conversion;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;


@Stateless
public class SlotsAndSlotPairsDataLoader extends AbstractDataLoader {
    @Inject private SlotEJB slotEJB;
    @Inject private SlotRelationEJB slotRelationEJB;
    @Inject private SlotPairEJB slotPairEJB;
    @Inject private PropertyEJB propertyEJB;

    @Inject private ComptypeEJB comptypeEJB;
    private List<Slot> newSlots;
    private Set<Slot> newSlotPairChildren;
    private int nameIndex, compTypeIndex, isHostingIndex, descriptionIndex, blpIndex;
    private int globalXIndex, globalYIndex, globalZIndex, globalRollIndex, globalYawIndex, globalPitchIndex;
    private int asmCommentIndex, asmPositionIndex, commentIndex;
    private int relationIndex, parentIndex, childIndex;
    private DataLoaderResult slotsLoaderResult;
    private DataLoaderResult slotPairsLoaderResult;

    public DataLoaderResult loadDataToDatabase(List<List<String>> slotsFileRows, List<List<String>> slotPairsFileRows,
            String slotsFileName, String slotPairsFileName) {

        loaderResult = new DataLoaderResult();
        newSlots = new ArrayList<>();
        newSlotPairChildren = new HashSet<>();

        if (slotsFileRows != null && !slotsFileRows.isEmpty()) {
            slotsLoaderResult = new DataLoaderResult();
            slotsLoaderResult.addMessage(new ValidationMessage(slotsFileName));
            loadSlots(slotsFileRows);
            if (slotsLoaderResult.isError()) {
                loaderResult.addResult(slotsLoaderResult);
            }
        }

        if (slotPairsFileRows != null && !slotPairsFileRows.isEmpty() && !loaderResult.isError()) {
            slotPairsLoaderResult = new DataLoaderResult();
            slotPairsLoaderResult.addMessage(new ValidationMessage(slotPairsFileName));
            loadSlotPairs(slotPairsFileRows);
            if (slotPairsLoaderResult.isError()) {
                loaderResult.addResult(slotPairsLoaderResult);
            }
        }

        if (!loaderResult.isError()) {
            checkForRelationConsistency();
        }

        return loaderResult;
    }

    private void loadSlots(List<List<String>> inputRows) {
        final List<String> fields = ImmutableList.of("NAME", "CTYPE", "DESCRIPTION", "IS-HOSTING-SLOT", "BLP", "GCX",
                "GCY", "GCZ", "GL-ROLL", "GL-YAW", "GL-PITCH", "ASM-COMMENT", "ASM-POSITION", "COMMENT");
        /*
         * List does not contain any rows that do not have a value (command)
         * in the first column. There should be no commands before "HEADER".
         */
        List<String> headerRow = inputRows.get(0);

        checkForDuplicateHeaderEntries(headerRow);
        if (rowResult.isError()) {
            slotsLoaderResult.addResult(rowResult);
            return;
        }
        setUpIndexesForFields(headerRow);
        HashMap<String, Integer> indexByPropertyName = indexByPropertyName(fields, headerRow);
        checkPropertyAssociation(indexByPropertyName, headerRow.get(0));

        if (rowResult.isError()) {
            slotsLoaderResult.addResult(rowResult);
            return;
        } else {
            for (List<String> row : inputRows.subList(1, inputRows.size())) {
                final String rowNumber = row.get(0);
                slotsLoaderResult.addResult(rowResult);
                rowResult = new DataLoaderResult();
                if (Objects.equal(row.get(commandIndex), CMD_HEADER)) {
                    headerRow = row;
                    checkForDuplicateHeaderEntries(headerRow);
                    if (rowResult.isError()) {
                        slotsLoaderResult.addResult(rowResult);
                        return;
                    }
                    setUpIndexesForFields(headerRow);
                    indexByPropertyName = indexByPropertyName(fields, headerRow);
                    checkPropertyAssociation(indexByPropertyName, rowNumber);
                    if (rowResult.isError()) {
                        return;
                    } else {
                        continue; // skip the rest of the processing for
                        // HEADER row
                    }
                } else if (row.get(1).equals(CMD_END)) {
                    break;
                }

                final String command = Preconditions.checkNotNull(row.get(commandIndex).toUpperCase());
                final @Nullable String name = row.get(nameIndex);
                final @Nullable String description = descriptionIndex == -1 ? null : row.get(descriptionIndex);
                final @Nullable String componentType = row.get(compTypeIndex);
                final @Nullable String isHostingString = row.get(isHostingIndex);
                final @Nullable String asmComment = asmCommentIndex == -1 ? null : row.get(asmCommentIndex);
                final @Nullable String asmPosition = asmPositionIndex == -1 ? null : row.get(asmPositionIndex);
                final @Nullable String comment = commentIndex == -1 ? null : row.get(commentIndex);

                final @Nullable Double blp = parseDouble(blpIndex == -1 ? null : row.get(blpIndex), blpIndex == -1 ? null : headerRow.get(blpIndex), rowNumber);
                final @Nullable Double globalX = parseDouble(globalXIndex == -1 ? null : row.get(globalXIndex), globalXIndex == -1 ? null : headerRow.get(globalXIndex), rowNumber);
                final @Nullable Double globalY = parseDouble(globalYIndex == -1 ? null : row.get(globalYIndex), globalYIndex == -1 ? null : headerRow.get(globalYIndex), rowNumber);
                final @Nullable Double globalZ = parseDouble(globalZIndex == -1 ? null : row.get(globalZIndex), globalZIndex == -1 ? null : headerRow.get(globalZIndex), rowNumber);
                final @Nullable Double globalRoll = parseDouble(globalRollIndex == -1 ? null : row.get(globalRollIndex), globalRollIndex == -1 ? null : headerRow.get(globalRollIndex), rowNumber);
                final @Nullable Double globalPitch = parseDouble(globalPitchIndex == -1 ? null : row.get(globalPitchIndex), globalPitchIndex == -1 ? null : headerRow.get(globalPitchIndex), rowNumber);
                final @Nullable Double globalYaw = parseDouble(globalYawIndex == -1 ? null : row.get(globalYawIndex), globalYawIndex == -1 ? null : headerRow.get(globalYawIndex), rowNumber);

                if (name == null) {
                    rowResult.addMessage(new ValidationMessage(ErrorMessage.REQUIRED_FIELD_MISSING, rowNumber, headerRow.get(nameIndex)));
                } else if (isHostingString == null && !command.equals(CMD_RENAME) && !command.equals(CMD_DELETE)) {
                    rowResult.addMessage(new ValidationMessage(ErrorMessage.REQUIRED_FIELD_MISSING, rowNumber, headerRow.get(isHostingIndex)));
                } else if (componentType == null && !command.equals(CMD_RENAME) && !command.equals(CMD_DELETE)) {
                    rowResult.addMessage(new ValidationMessage(ErrorMessage.REQUIRED_FIELD_MISSING, rowNumber, headerRow.get(compTypeIndex)));
                }

                if (rowResult.isError()) {
                    continue;
                }

                final boolean isHosting;

                if (isHostingString.equalsIgnoreCase(Boolean.TRUE.toString())) {
                    isHosting = true;
                } else if (isHostingString.equalsIgnoreCase(Boolean.FALSE.toString())) {
                    isHosting = false;
                } else {
                    isHosting = false;
                    rowResult.addMessage(new ValidationMessage(ErrorMessage.SHOULD_BE_BOOLEAN_VALUE, rowNumber, headerRow.get(isHostingIndex)));
                }

                if (!rowResult.isError()) {
                    switch (command) {
                    case CMD_UPDATE:
                        if (slotEJB.findByName(name) != null) {
                            final @Nullable ComponentType compType = comptypeEJB.findByName(componentType);
                            if (compType == null) {
                                rowResult.addMessage(new ValidationMessage(ErrorMessage.ENTITY_NOT_FOUND, rowNumber, headerRow.get(compTypeIndex)));
                                continue;
                            } else {
                                try {
                                    if (compType.getName().equals(SlotEJB.ROOT_COMPONENT_TYPE)) {
                                        rowResult.addMessage(new ValidationMessage(ErrorMessage.NOT_AUTHORIZED, rowNumber, headerRow.get(commandIndex)));
                                        continue;
                                    }
                                    final Slot slotToUpdate = slotEJB.findByName(name);
                                    addOrUpdateSlot(slotToUpdate, compType, isHosting, description, blp, globalX, globalY, globalZ, globalRoll, globalPitch, globalYaw, asmComment, asmPosition, comment);
                                    addOrUpdateProperties(slotToUpdate, indexByPropertyName, row, rowNumber);
                                    if (rowResult.isError()) {
                                        continue;
                                    }
                                } catch (Exception e) {
                                    rowResult.addMessage(new ValidationMessage(ErrorMessage.NOT_AUTHORIZED, rowNumber, headerRow.get(commandIndex)));
                                }
                            }
                        } else {
                            final @Nullable ComponentType compType = comptypeEJB.findByName(componentType);
                            if (compType == null) {
                                rowResult.addMessage(new ValidationMessage(ErrorMessage.ENTITY_NOT_FOUND, rowNumber, headerRow.get(compTypeIndex)));
                                continue;
                            } else {
                                try {
                                    if (compType.getName().equals(SlotEJB.ROOT_COMPONENT_TYPE)) {
                                        rowResult.addMessage(new ValidationMessage(ErrorMessage.NOT_AUTHORIZED, rowNumber, headerRow.get(commandIndex)));
                                        continue;
                                    }
                                    final Slot newSlot = new Slot(name, isHosting);
                                    addOrUpdateSlot(newSlot, compType, isHosting, description, blp, globalX, globalY, globalZ, globalRoll, globalPitch, globalYaw, asmComment, asmPosition, comment);
                                    slotEJB.addSlotToParentWithPropertyDefs(newSlot,null, true);
                                    addOrUpdateProperties(newSlot, indexByPropertyName, row, rowNumber);
                                    newSlots.add(newSlot);
                                    if (rowResult.isError()) {
                                        continue;
                                    }
                                } catch (Exception e) {
                                    rowResult.addMessage(new ValidationMessage(ErrorMessage.NOT_AUTHORIZED, rowNumber, headerRow.get(commandIndex)));
                                }
                            }
                        }
                        break;
                    case CMD_DELETE:
                        final @Nullable Slot slotToDelete = slotEJB.findByName(name);
                        try {
                            if (slotToDelete == null) {
                                rowResult.addMessage(new ValidationMessage(ErrorMessage.ENTITY_NOT_FOUND, rowNumber, headerRow.get(nameIndex)));
                                continue;
                            } else {
                                final ComponentType compType = slotToDelete.getComponentType();
                                if (compType.getName().equals(SlotEJB.ROOT_COMPONENT_TYPE)) {
                                    rowResult.addMessage(new ValidationMessage(ErrorMessage.NOT_AUTHORIZED, rowNumber, headerRow.get(commandIndex)));
                                    continue;
                                }
                                slotEJB.delete(slotToDelete);
                            }
                        } catch (Exception e) {
                            rowResult.addMessage(new ValidationMessage(ErrorMessage.NOT_AUTHORIZED, rowNumber, headerRow.get(commandIndex)));
                        }
                        break;
                    case CMD_RENAME:
                        try {
                            final int startOldNameMarkerIndex = name.indexOf("[");
                            final int endOldNameMarkerIndex = name.indexOf("]");
                            if (startOldNameMarkerIndex == -1 || endOldNameMarkerIndex == -1) {
                                rowResult.addMessage(new ValidationMessage(ErrorMessage.RENAME_MISFORMAT, rowNumber, headerRow.get(nameIndex)));
                                continue;
                            }

                            final String oldName = name.substring(startOldNameMarkerIndex + 1, endOldNameMarkerIndex).trim();
                            final String newName = name.substring(endOldNameMarkerIndex + 1).trim();

                            final Slot slotToRename = slotEJB.findByName(oldName);
                            if (slotToRename != null) {
                                if (slotEJB.findByName(newName) != null) {
                                    rowResult.addMessage(new ValidationMessage(ErrorMessage.NAME_ALREADY_EXISTS, rowNumber, headerRow.get(nameIndex)));
                                    continue;
                                } else {
                                    final ComponentType compType = slotToRename.getComponentType();
                                    if (compType.getName().equals(SlotEJB.ROOT_COMPONENT_TYPE)) {
                                        rowResult.addMessage(new ValidationMessage(ErrorMessage.NOT_AUTHORIZED, rowNumber, headerRow.get(commandIndex)));
                                        continue;
                                    }
                                    slotToRename.setName(newName);
                                    slotEJB.save(slotToRename);
                                }
                            } else {
                                rowResult.addMessage(new ValidationMessage(ErrorMessage.ENTITY_NOT_FOUND, rowNumber, headerRow.get(nameIndex)));
                                continue;
                            }
                        } catch (Exception e) {
                            rowResult.addMessage(new ValidationMessage(ErrorMessage.NOT_AUTHORIZED, rowNumber, headerRow.get(commandIndex)));
                        }
                        break;
                    default:
                        rowResult.addMessage(new ValidationMessage(ErrorMessage.COMMAND_NOT_VALID, rowNumber, headerRow.get(commandIndex)));
                    }
                }
            }
            slotsLoaderResult.addResult(rowResult);
        }
    }

    private void addOrUpdateSlot(Slot slotToAddOrUpdate, ComponentType compType, boolean isHosting, String description,
            Double blp, Double globalX, Double globalY, Double globalZ, Double globalRoll, Double globalPitch,
            Double globalYaw, String asmComment, String asmPosition, String comment) {
        slotToAddOrUpdate.setModifiedAt(new Date());
        slotToAddOrUpdate.setComponentType(compType);
        slotToAddOrUpdate.setDescription(description);
        slotToAddOrUpdate.setHostingSlot(isHosting);
        slotToAddOrUpdate.setBeamlinePosition(blp);
        slotToAddOrUpdate.setAssemblyComment(asmComment);
        slotToAddOrUpdate.setAssemblyPosition(asmPosition);
        slotToAddOrUpdate.setComment(comment);
        final PositionInformation positionInfo = slotToAddOrUpdate.getPositionInformation();
        positionInfo.setGlobalX(globalX);
        positionInfo.setGlobalY(globalY);
        positionInfo.setGlobalZ(globalZ);
        positionInfo.setGlobalRoll(globalRoll);
        positionInfo.setGlobalPitch(globalPitch);
        positionInfo.setGlobalYaw(globalYaw);
    }

    private void loadSlotPairs(List<List<String>> inputRows) {
        /*
         * List does not contain any rows that do not have a value (command)
         * in the first column. There should be no commands before "HEADER".
         */
        List<String> headerRow = inputRows.get(0);

        checkForDuplicateHeaderEntries(headerRow);
        if (rowResult.isError()) {
            slotPairsLoaderResult.addResult(rowResult);
            return;
        }
        setUpIndexesForSlotPairFields(headerRow);
        if (rowResult.isError()) {
            slotPairsLoaderResult.addResult(rowResult);
            return;
        } else {
            for (List<String> row : inputRows.subList(1, inputRows.size())) {
                final String rowNumber = row.get(0);
                slotPairsLoaderResult.addResult(rowResult);
                rowResult = new DataLoaderResult();
                if (Objects.equal(row.get(1), CMD_HEADER)) {
                    headerRow = row;
                    checkForDuplicateHeaderEntries(headerRow);
                    if (rowResult.isError()) {
                        slotPairsLoaderResult.addResult(rowResult);
                        return;
                    }
                    setUpIndexesForFields(headerRow);
                    if (rowResult.isError()) {
                        return;
                    } else {
                        continue; // skip the rest of the processing for
                        // HEADER row
                    }
                } else if (row.get(1).equals(CMD_END)) {
                    break;
                }

                final String command = Preconditions.checkNotNull(row.get(1).toUpperCase());
                final @Nullable String parent = row.get(parentIndex);
                final @Nullable String child = row.get(childIndex);
                final @Nullable String relation = row.get(relationIndex);

                if (parent == null) {
                    rowResult.addMessage(new ValidationMessage(ErrorMessage.REQUIRED_FIELD_MISSING, rowNumber, headerRow.get(parentIndex)));
                } else if (child == null) {
                    rowResult.addMessage(new ValidationMessage(ErrorMessage.REQUIRED_FIELD_MISSING, rowNumber, headerRow.get(childIndex)));
                } else if (relation == null) {
                    rowResult.addMessage(new ValidationMessage(ErrorMessage.REQUIRED_FIELD_MISSING, rowNumber, headerRow.get(relationIndex)));
                }

                if (!rowResult.isError()) {
                    final List<Slot> childrenSlots = slotEJB.findSlotByNameContainingString(child);
                    final Slot parentSlot = slotEJB.findByName(parent);
                    final SlotRelationName slotRelationName;

                    if (childrenSlots == null || childrenSlots.size() == 0) {
                        rowResult.addMessage(new ValidationMessage(ErrorMessage.ENTITY_NOT_FOUND, rowNumber, headerRow.get(childIndex)));
                    } else if (parentSlot == null) {
                        rowResult.addMessage(new ValidationMessage(ErrorMessage.ENTITY_NOT_FOUND, rowNumber, headerRow.get(parentIndex)));
                    }

                    if (relation.equalsIgnoreCase(SlotRelationName.CONTAINS.name())) {
                        slotRelationName = SlotRelationName.CONTAINS;
                    } else if (relation.equalsIgnoreCase(SlotRelationName.POWERS.name())) {
                        slotRelationName = SlotRelationName.POWERS;
                    } else if (relation.equalsIgnoreCase(SlotRelationName.CONTROLS.name())) {
                        slotRelationName = SlotRelationName.CONTROLS;
                    } else {
                        slotRelationName = null;
                        rowResult.addMessage(new ValidationMessage(ErrorMessage.ENTITY_NOT_FOUND, rowNumber, headerRow.get(relationIndex)));
                    }

                    if (rowResult.isError()) {
                        continue;
                    } else {
                        final SlotRelation slotRelation = slotRelationEJB.findBySlotRelationName(slotRelationName);
                        switch (command) {
                        case CMD_UPDATE:
                            for (Slot childSlot : childrenSlots) {
                                if (newSlots.contains(childSlot)) {
                                    try {
                                        if (!childSlot.getName().equals(parentSlot.getName())) {
                                            if (slotRelation.getName() == SlotRelationName.CONTAINS) {
                                                if (childSlot.getComponentType().getName().equalsIgnoreCase(SlotEJB.ROOT_COMPONENT_TYPE)) {
                                                    rowResult.addMessage(new ValidationMessage(ErrorMessage.CANT_ADD_PARENT_TO_ROOT, rowNumber));
                                                    continue;
                                                } else if (parentSlot.isHostingSlot() && !childSlot.isHostingSlot()) {
                                                    rowResult.addMessage(new ValidationMessage(ErrorMessage.INSTALL_CANT_CONTAIN_CONTAINER, rowNumber));
                                                    continue;
                                                } else {
                                                    final SlotPair newSlotPair = new SlotPair(childSlot, parentSlot, slotRelation);
                                                    slotPairEJB.add(newSlotPair);
                                                    newSlotPairChildren.add(newSlotPair.getChildSlot());
                                                }
                                            } else if (slotRelation.getName() == SlotRelationName.POWERS) {
                                                if (childSlot.isHostingSlot() && parentSlot.isHostingSlot()) {
                                                    slotPairEJB.add(new SlotPair(childSlot, parentSlot, slotRelation));
                                                } else {
                                                    rowResult.addMessage(new ValidationMessage(ErrorMessage.POWER_RELATIONSHIP_RESTRICTIONS, rowNumber));
                                                    continue;
                                                }
                                            } else if (slotRelation.getName() == SlotRelationName.CONTROLS) {
                                                if (childSlot.isHostingSlot() && parentSlot.isHostingSlot()) {
                                                    slotPairEJB.add(new SlotPair(childSlot, parentSlot, slotRelation));
                                                } else {
                                                    rowResult.addMessage(new ValidationMessage(ErrorMessage.CONTROL_RELATIONSHIP_RESTRICTIONS, rowNumber));
                                                    continue;
                                                }
                                            }
                                        } else {
                                            rowResult.addMessage(new ValidationMessage(ErrorMessage.SAME_CHILD_AND_PARENT, rowNumber));
                                        }
                                    } catch (Exception e) {
                                        rowResult.addMessage(new ValidationMessage(ErrorMessage.NOT_AUTHORIZED, rowNumber, headerRow.get(commandIndex)));
                                    }
                                }
                            }
                            break;
                        case CMD_DELETE:
                            final List<SlotPair> slotPairs = slotPairEJB.findSlotPairsByParentChildRelation(child, parent, slotRelationName);
                            if (slotPairs.size() != 0) {
                                try {
                                    for (SlotPair slotPair : slotPairs) {
                                        slotPairEJB.delete(slotPair);
                                    }
                                } catch (Exception e) {
                                    rowResult.addMessage(new ValidationMessage(ErrorMessage.NOT_AUTHORIZED, rowNumber, headerRow.get(commandIndex)));
                                }
                            } else {
                                rowResult.addMessage(new ValidationMessage(ErrorMessage.ENTITY_NOT_FOUND, rowNumber));
                            }
                            break;
                        default:
                            rowResult.addMessage(new ValidationMessage(ErrorMessage.COMMAND_NOT_VALID, rowNumber, headerRow.get(commandIndex)));
                        }
                    }
                }
            }
            slotPairsLoaderResult.addResult(rowResult);
        }
    }

    private void checkForRelationConsistency() {
        for (Slot newSlot : newSlots) {
            if (!newSlotPairChildren.contains(newSlot) && !newSlot.getComponentType().getName().equals(SlotEJB.ROOT_COMPONENT_TYPE)) {
                final ValidationMessage orphanSlotMessage = new ValidationMessage(ErrorMessage.ORPHAN_SLOT);
                orphanSlotMessage.setOrphanSlotName(newSlot.getName());
                loaderResult.addMessage(orphanSlotMessage);
            }
        }
    }

    private void setUpIndexesForSlotPairFields(List<String> header) {
        final String rowNumber = header.get(0);
        relationIndex = header.indexOf("RELATION");
        parentIndex = header.indexOf("PARENT");
        childIndex = header.indexOf("CHILD");

        rowResult = new DataLoaderResult();
        if (relationIndex == -1) {
            rowResult.addMessage(new ValidationMessage(ErrorMessage.HEADER_FIELD_MISSING, rowNumber, "RELATION"));
        } else if (parentIndex == -1) {
            rowResult.addMessage(new ValidationMessage(ErrorMessage.HEADER_FIELD_MISSING, rowNumber, "PARENT"));
        } else if (childIndex == -1) {
            rowResult.addMessage(new ValidationMessage(ErrorMessage.HEADER_FIELD_MISSING, rowNumber, "CHILD"));
        }
    }

    private Double parseDouble(@Nullable String stringValue, @Nullable String columnName, String rowNumber) {
        if (stringValue == null) {
            return null;
        } else {
            try {
                double doubleValue = Double.parseDouble(stringValue);
                return doubleValue;
            } catch (NumberFormatException e) {
                rowResult.addMessage(new ValidationMessage(ErrorMessage.SHOULD_BE_NUMERIC_VALUE, rowNumber, columnName));
                return null;
            }
        }
    }

    @Override protected void setUpIndexesForFields(List<String> header) {
        final String rowNumber = header.get(0);
        nameIndex = header.indexOf("NAME");
        descriptionIndex = header.indexOf("DESCRIPTION");
        compTypeIndex = header.indexOf("CTYPE");
        isHostingIndex = header.indexOf("IS-HOSTING-SLOT");
        blpIndex = header.indexOf("BLP");
        globalXIndex = header.indexOf("GCX");
        globalYIndex = header.indexOf("GCY");
        globalZIndex = header.indexOf("GCZ");
        globalRollIndex = header.indexOf("GL-ROLL");
        globalYawIndex = header.indexOf("GL-YAW");
        globalPitchIndex = header.indexOf("GL-PITCH");
        asmCommentIndex = header.indexOf("ASM-COMMENT");
        asmPositionIndex = header.indexOf("ASM-POSITION");
        commentIndex = header.indexOf("COMMENT");

        rowResult = new DataLoaderResult();
        if (nameIndex == -1) {
            rowResult.addMessage(new ValidationMessage(ErrorMessage.HEADER_FIELD_MISSING, rowNumber, "NAME"));
        } else if (isHostingIndex == -1) {
            rowResult.addMessage(new ValidationMessage(ErrorMessage.HEADER_FIELD_MISSING, rowNumber, "IS-HOSTING-SLOT"));
        } else if (compTypeIndex == -1) {
            rowResult.addMessage(new ValidationMessage(ErrorMessage.HEADER_FIELD_MISSING, rowNumber, "CTYPE"));
        }
    }

    private void checkPropertyAssociation(Map<String, Integer> properties, String rowNumber) {
        final Iterator<String> propertiesIterator = properties.keySet().iterator();
        while (propertiesIterator.hasNext()) {
            final String propertyName = propertiesIterator.next();
            final @Nullable Property property = propertyEJB.findByName(propertyName);
            if (property == null) {
                rowResult.addMessage(new ValidationMessage(ErrorMessage.PROPERTY_NOT_FOUND, rowNumber, propertyName));
            } else {
                if (!property.isSlotAssociation()) {
                    rowResult.addMessage(new ValidationMessage(ErrorMessage.PROPERTY_ASSOCIATION_FAILURE, rowNumber, propertyName));
                }
            }
        }
    }

    private void addOrUpdateProperties(Slot slot, Map<String, Integer> properties, List<String> row, String rowNumber) {
        final List<SlotPropertyValue> slotProperties = new ArrayList<>();
        if (slot.getSlotPropertyList() != null) {
            slotProperties.addAll(slot.getSlotPropertyList());
        }
        final Map<Property, SlotPropertyValue> slotPropertyByProperty = new HashMap<>();

        for (SlotPropertyValue slotProperty : slotProperties) {
            slotPropertyByProperty.put(slotProperty.getProperty(), slotProperty);
        }

        for(Entry<String, Integer> entry : properties.entrySet()) {
            final String propertyName = entry.getKey();
            final int propertyIndex = entry.getValue();
            final @Nullable Property property = propertyEJB.findByName(propertyName);
            final @Nullable String propertyValue = row.get(propertyIndex);
            if (slotPropertyByProperty.containsKey(property)) {
                final SlotPropertyValue slotPropertyToUpdate = slotPropertyByProperty.get(property);
                if (propertyValue == null) {
                    slotEJB.deleteChild(slotPropertyToUpdate);
                } else {
                    slotPropertyToUpdate.setPropValue(Conversion.stringToValue(propertyValue, property.getDataType()));
                    slotEJB.saveChild(slotPropertyToUpdate);
                }

            } else if (propertyValue != null) {
                final SlotPropertyValue slotPropertyToAdd = new SlotPropertyValue(false);
                slotPropertyToAdd.setProperty(property);
                slotPropertyToAdd.setPropValue(Conversion.stringToValue(propertyValue, property.getDataType()));
                slotPropertyToAdd.setSlot(slot);
                slotEJB.addChild(slotPropertyToAdd);
            }
        }
    }
}
