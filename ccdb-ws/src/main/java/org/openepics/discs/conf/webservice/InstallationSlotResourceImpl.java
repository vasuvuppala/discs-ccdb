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
package org.openepics.discs.conf.webservice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.openepics.discs.ccdb.core.ejb.ChecklistEJB;
import org.openepics.discs.ccdb.core.ejb.ComptypeEJB;
import org.openepics.discs.ccdb.core.ejb.InstallationEJB;
import org.openepics.discs.ccdb.core.ejb.SlotEJB;
import org.openepics.discs.ccdb.model.ComponentType;
import org.openepics.discs.ccdb.model.ComptypePropertyValue;
import org.openepics.discs.ccdb.model.DevicePropertyValue;
import org.openepics.discs.ccdb.model.InstallationRecord;
import org.openepics.discs.ccdb.model.Property;
import org.openepics.discs.ccdb.model.Slot;
import org.openepics.discs.ccdb.model.SlotPair;
import org.openepics.discs.ccdb.model.SlotPropertyValue;
import org.openepics.discs.ccdb.model.SlotRelationName;
import org.openepics.discs.ccdb.jaxb.InstallationSlot;
import org.openepics.discs.ccdb.jaxb.PropertyKind;
import org.openepics.discs.ccdb.jaxb.PropertyValue;
import org.openepics.discs.ccdb.jaxrs.InstallationSlotResource;
import org.openepics.discs.ccdb.core.util.UnhandledCaseException;
import org.openepics.discs.ccdb.jaxb.ProcessStatusRep;
import org.openepics.discs.ccdb.jaxb.RelationshipRep;
import org.openepics.discs.ccdb.model.cl.ProcessStatus;

/**
 * An implementation of the InstallationSlotResource interface.
 *
 * @author <a href="mailto:sunil.sah@cosylab.com">Sunil Sah</a>
 */
public class InstallationSlotResourceImpl implements InstallationSlotResource {
    @Inject private SlotEJB slotEJB;
    @Inject private ComptypeEJB compTypeEJB;
    @Inject private ChecklistEJB clEJB;
    @Inject private InstallationEJB installationEJB;
    
    @FunctionalInterface
    private interface RelatedSlotExtractor {
        public Slot getRelatedSlot(final SlotPair pair);
    }

//    @Override
//    public List<InstallationSlot> getInstallationSlots(String deviceType) {
//        // Get all slots
//        if ("undefined".equals(deviceType)) {
//            return slotEJB.findAll().stream().
//                filter(slot -> slot!=null && slot.isHostingSlot()).
//                map(slot -> createInstallationSlot(slot)).
//                collect(Collectors.toList());
//        } else {
//            // Get them filtered by deviceType
//            return getInstallationSlotsForType(deviceType);
//        }
//    }

    
    @Override
    public List<InstallationSlot> searchSlots(String name, String type, String tag, String detail) {
        // Get mattching slots
           
        return slotEJB.searchSlots(name, type).stream().
                filter(slot -> slot != null && slot.isHostingSlot()).
                map(slot -> createInstallationSlot(slot, detail)).
                collect(Collectors.toList());
    }
    
    @Override
    public InstallationSlot getInstallationSlot(String name) {
        final Slot installationSlot = slotEJB.findByName(name);
        if (installationSlot == null || !installationSlot.isHostingSlot()) {
            return null;
        }
        return createInstallationSlot(installationSlot, InstallationSlotResource.DETAIL_PROPERTY + InstallationSlotResource.DETAIL_STATUS);
    }
    
    @Override
    public PropertyValue getSlotPropertyValue(String name, String property) {
        final Slot slot = slotEJB.findByName(name);
        if (slot == null || property == null) {
            return null;
        }
        List<org.openepics.discs.ccdb.model.SlotPropertyValue> slotProps = new ArrayList<>(slot.getSlotPropertyList());
        return  slotProps.stream().filter(prop -> property.equals(prop.getProperty().getName()))
                .map(propValue -> createPropertyValue(propValue))
                       .collect(Collectors.toList()).get(0); // TODO: improve use findFirst()
    }

//    private List<InstallationSlot> getInstallationSlotsForType(String deviceType) {
//        if (StringUtils.isEmpty(deviceType)) {
//            return new ArrayList<>();
//        }
//
//        final ComponentType ct = compTypeEJB.findByName(deviceType);
//        if (ct == null) {
//            return new ArrayList<>();
//        }
//
//        return slotEJB.findByComponentType(ct).stream().
//                map(slot -> createInstallationSlot(slot)).
//                collect(Collectors.toList());
//    }

    private InstallationSlot createInstallationSlot(final Slot slot, String detail) {
        if (slot == null) {
            return null;
        }

        final InstallationSlot installationSlot = new InstallationSlot();
        installationSlot.setName(slot.getName());
        installationSlot.setDescription(slot.getDescription());
        installationSlot.setDeviceType(DeviceTypeResourceImpl.getDeviceType(slot.getComponentType()));

        if (detail.contains(InstallationSlotResource.DETAIL_RELATIONSHIP)) installationSlot.setRelationships(getRelationships(slot));
        if (detail.contains(InstallationSlotResource.DETAIL_STATUS)) installationSlot.setStatuses(getStatus(slot));
        if (detail.contains(InstallationSlotResource.DETAIL_PROPERTY)) installationSlot.setProperties(getPropertyValues(slot));
        
        return installationSlot;
    }
    
//    private InstallationSlot createInstallationSlot(final Slot slot) {
//        if (slot == null) {
//            return null;
//        }
//
//        final InstallationSlot installationSlot = new InstallationSlot();
//        installationSlot.setName(slot.getName());
//        installationSlot.setDescription(slot.getDescription());
//        installationSlot.setDeviceType(DeviceTypeResourceImpl.getDeviceType(slot.getComponentType()));
//
//        installationSlot.setParents(
//                getRelatedSlots(slot.getPairsInWhichThisSlotIsAChildList().stream(),
//                        SlotRelationName.CONTAINS,
//                        pair -> pair.getParentSlot()));
//        installationSlot.setChildren(
//                getRelatedSlots(slot.getPairsInWhichThisSlotIsAParentList().stream(),
//                        SlotRelationName.CONTAINS,
//                        pair -> pair.getChildSlot()));
//
//        installationSlot.setPoweredBy(
//                getRelatedSlots(slot.getPairsInWhichThisSlotIsAChildList().stream(),
//                        SlotRelationName.POWERS,
//                        pair -> pair.getParentSlot()));
//        installationSlot.setPowers(
//                getRelatedSlots(slot.getPairsInWhichThisSlotIsAParentList().stream(),
//                        SlotRelationName.POWERS,
//                        pair -> pair.getChildSlot()));
//
//        installationSlot.setControlledBy(
//                getRelatedSlots(slot.getPairsInWhichThisSlotIsAChildList().stream(),
//                        SlotRelationName.CONTROLS,
//                        pair -> pair.getParentSlot()));
//        installationSlot.setControls(
//                getRelatedSlots(slot.getPairsInWhichThisSlotIsAParentList().stream(),
//                        SlotRelationName.CONTROLS,
//                        pair -> pair.getChildSlot()));
//      
//        installationSlot.setProperties(getPropertyValues(slot));
//        return installationSlot;
//    }

    private List<ProcessStatusRep> getStatus(Slot slot) {
        List<ProcessStatus> statuses = clEJB.findStatuses(slot);
              
        return statuses == null? null: statuses.stream().map(p -> ProcessStatusRep.newInstance(p)).collect(Collectors.toList());
    }
    
    private List<RelationshipRep> getRelationships(Slot slot) {
        List<RelationshipRep> rels = new ArrayList<>();
        List<SlotPair> pairs = new ArrayList<>(slot.getPairsInWhichThisSlotIsAChildList());        
        
        rels.addAll(pairs.stream().map(p -> new RelationshipRep(p.getSlotRelation().getName().toString(), p.getParentSlot().getName())).collect(Collectors.toList()));
        
        pairs.clear();
        pairs.addAll(slot.getPairsInWhichThisSlotIsAParentList());        
        rels.addAll(slot.getPairsInWhichThisSlotIsAParentList().stream().map(p -> new RelationshipRep(p.getSlotRelation().getName().toString(), p.getChildSlot().getName())).collect(Collectors.toList()));
        
        return rels;
    }
    
    private List<String> getRelatedSlots(final Stream<SlotPair> relatedSlotPairs,
            final SlotRelationName relationName,
            final RelatedSlotExtractor extractor) {
        return relatedSlotPairs.
                filter(slotPair -> relationName.equals(slotPair.getSlotRelation().getName())).
                map(relatedSlotPair -> extractor.getRelatedSlot(relatedSlotPair)).
                filter(slot -> slot.isHostingSlot()).
                map(slot -> slot.getName()).
                collect(Collectors.toList());
    }

    /*
    
    private List<PropertyValue> getPropertyValues(final Slot slot) {
        final InstallationRecord record = installationEJB.getActiveInstallationRecordForSlot(slot);

        final Stream<? extends PropertyValue> externalProps = Stream.concat(
                            slot.getComponentType().getComptypePropertyList().stream().
                                filter(propValue -> !propValue.isPropertyDefinition()).
                                map(propValue -> createPropertyValue(propValue)),
                            record == null ? Stream.empty() :
                                record.getDevice().getDevicePropertyList().stream().
                                    map(propValue -> createPropertyValue(propValue)));

        return Stream.concat(slot.getSlotPropertyList().stream().map(propValue -> createPropertyValue(propValue)),
                                externalProps).
                        collect(Collectors.toList());
    }
    */

    private List<PropertyValue> getPropertyValues(final Slot slot) {
        final InstallationRecord record = installationEJB.getActiveInstallationRecordForSlot(slot);
       
//        return slotProps.stream().map(propValue -> createPropertyValue(propValue)).collect(Collectors.toList());
        
//         ToDo: Temporary workaround for bug in EcplseliNk 2.5: lazy fetch and streams do not work well together 
        List<org.openepics.discs.ccdb.model.ComptypePropertyValue> typeProps = new ArrayList<>(slot.getComponentType().getComptypePropertyList());
        // ToDo: Temporary workaround for bug in EcplseliNk 2.5: lazy fetch and streams do not work well together
        List<org.openepics.discs.ccdb.model.DevicePropertyValue> deviceProps = (record == null ? new ArrayList<>() : new ArrayList<>(record.getDevice().getDevicePropertyList()));
        final Stream<? extends PropertyValue> externalProps = Stream.concat(
                            typeProps.stream().
                                filter(propValue -> !propValue.isPropertyDefinition()).
                                map(propValue -> createPropertyValue(propValue)),
                            record == null ? Stream.empty() : deviceProps.stream().
                                    map(propValue -> createPropertyValue(propValue)));

        // ToDo: Temporary workaround for bug in EcplseliNk 2.5: lazy fetch and streams do not work well together 
        
        List<org.openepics.discs.ccdb.model.SlotPropertyValue> slotProps = new ArrayList<>(slot.getSlotPropertyList());
        return Stream.concat(slotProps.stream().map(propValue -> createPropertyValue(propValue)), externalProps)
                        .collect(Collectors.toList());
    }
    
    private PropertyValue createPropertyValue(final org.openepics.discs.ccdb.model.PropertyValue slotPropertyValue) {
        final PropertyValue propertyValue = new PropertyValue();
        final Property parentProperty = slotPropertyValue.getProperty();
        propertyValue.setName(parentProperty.getName());
        propertyValue.setDataType(parentProperty.getDataType() != null ? parentProperty.getDataType().getName() : null);
        propertyValue.setUnit(parentProperty.getUnit() != null ? parentProperty.getUnit().getName() : null);
        propertyValue.setValue(Objects.toString(slotPropertyValue.getPropValue()));
        if (slotPropertyValue instanceof ComptypePropertyValue) {
            propertyValue.setPropertyKind(PropertyKind.TYPE);
        } else if (slotPropertyValue instanceof SlotPropertyValue) {
            propertyValue.setPropertyKind(PropertyKind.SLOT);
        } else if (slotPropertyValue instanceof DevicePropertyValue) {
            propertyValue.setPropertyKind(PropertyKind.DEVICE);
        } else {
            throw new UnhandledCaseException();
        }
        return propertyValue;
    }
    
//    private List<ProcessStatus> slotStatus(Slot slot) {
//        List<Assignment> assignments = clEJB.findAssignments(slot);
//        if (assignments != null && ! assignments.isEmpty()) {
//            return assignments.get(0).getStatuses();
//        }
//        return null;
//    }
}
