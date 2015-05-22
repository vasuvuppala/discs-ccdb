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
package org.openepics.discs.conf.ui.common;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;

import javax.ejb.EJBException;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.event.ValueChangeEvent;
import javax.faces.model.SelectItem;
import javax.faces.validator.ValidatorException;
import javax.inject.Inject;

import org.apache.commons.io.FilenameUtils;
import org.openepics.discs.conf.ejb.DAO;
import org.openepics.discs.conf.ejb.DataTypeEJB;
import org.openepics.discs.conf.ejb.TagEJB;
import org.openepics.discs.conf.ent.Artifact;
import org.openepics.discs.conf.ent.ComptypeArtifact;
import org.openepics.discs.conf.ent.ComptypePropertyValue;
import org.openepics.discs.conf.ent.ConfigurationEntity;
import org.openepics.discs.conf.ent.DataType;
import org.openepics.discs.conf.ent.DevicePropertyValue;
import org.openepics.discs.conf.ent.Property;
import org.openepics.discs.conf.ent.PropertyValue;
import org.openepics.discs.conf.ent.SlotPropertyValue;
import org.openepics.discs.conf.ent.Tag;
import org.openepics.discs.conf.ent.values.Value;
import org.openepics.discs.conf.util.BlobStore;
import org.openepics.discs.conf.util.BuiltInDataType;
import org.openepics.discs.conf.util.Conversion;
import org.openepics.discs.conf.util.PropertyValueNotUniqueException;
import org.openepics.discs.conf.util.PropertyValueUIElement;
import org.openepics.discs.conf.util.UnhandledCaseException;
import org.openepics.discs.conf.util.Utility;
import org.openepics.discs.conf.views.EntityAttributeView;
import org.openepics.discs.conf.views.EntityAttributeViewKind;
import org.primefaces.component.datatable.DataTable;
import org.primefaces.context.RequestContext;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;

/**
 * Parent class for all classes that handle entity attributes manipulation
 *
 * @param <T> There are 4 property value tables in the database, but all have the same columns and interface.
 * @param <S> There are 4 artifact tables in the database, but all have the same columns and interface.
 *
 * @author <a href="mailto:andraz.pozar@cosylab.com">Andraž Požar</a>
 * @author <a href="mailto:miha.vitorovic@cosylab.com">Miha Vitorovič</a>
 */
public abstract class AbstractAttributesController<T extends PropertyValue, S extends Artifact> implements Serializable {
    private static final long serialVersionUID = 523935015308933240L;

    private static final String MULTILINE_DELIMITER = "(\\r\\n)|\\r|\\n";

    private static final int MIN_ELEMENT_SIZE = 20;
    private static final int MAX_ELEMENT_SIZE = 40;
    private static final int ELEMENT_SIZE_PADDING = 8;
    private static final int LO_CONTENT_LEN = MIN_ELEMENT_SIZE - ELEMENT_SIZE_PADDING;
    private static final int HI_CONTENT_LEN = MAX_ELEMENT_SIZE - ELEMENT_SIZE_PADDING;

    protected static enum DefinitionTarget { SLOT, DEVICE }

    @Inject protected BlobStore blobStore;
    @Inject protected TagEJB tagEJB;
    @Inject protected DataTypeEJB dataTypeEJB;

    protected Property property;
    private List<Property> selectedProperties;
    private List<Property> selectionPropertiesFiltered;
    protected Value propertyValue;
    protected List<String> enumSelections;
    protected List<Property> filteredProperties;
    private boolean propertyNameChangeDisabled;
    protected PropertyValueUIElement propertyValueUIElement;
    protected boolean isPropertyDefinition;
    protected DefinitionTarget definitionTarget;

    private String tag;
    private List<String> tagsForAutocomplete;

    protected String artifactName;
    protected String artifactDescription;
    protected boolean isArtifactInternal;
    protected String artifactURI;
    protected boolean isArtifactBeingModified;

    protected List<EntityAttributeView> attributes;
    private List<EntityAttributeView> filteredAttributes;
    private final List<SelectItem> attributeKinds = Utility.buildAttributeKinds();
    protected EntityAttributeView selectedAttribute;

    protected byte[] importData;
    protected String importFileName;

    private DAO<? extends ConfigurationEntity> dao;
    private Class<T> propertyValueClass;
    private Class<S> artifactClass;

    protected List<ComptypePropertyValue> parentProperties;
    protected List<ComptypeArtifact> parentArtifacts;
    protected Set<Tag> parentTags;
    protected T propertyValueInstance;

    protected String entityName;

    protected void resetFields() {
        property = null;
        propertyValue = null;
        tag = null;
        artifactDescription = null;
        isArtifactInternal = false;
        artifactURI = null;
        importData = null;
        importFileName = null;
        enumSelections = null;
        propertyValueUIElement = PropertyValueUIElement.NONE;
    }

    /**
     * Adds new {@link PropertyValue} to parent {@link ConfigurationEntity}
     * defined in {@link AbstractAttributesController#setPropertyValueParent(PropertyValue)}
     */
    public void addNewPropertyValue() {
        try {
            propertyValueInstance = propertyValueClass.newInstance();
            propertyValueInstance.setInRepository(false);
            propertyValueInstance.setProperty(property);
            propertyValueInstance.setPropValue(propertyValue);
            setPropertyValueParent(propertyValueInstance);

            dao.addChild(propertyValueInstance);
            internalPopulateAttributesList();

            Utility.showMessage(FacesMessage.SEVERITY_INFO, Utility.MESSAGE_SUMMARY_SUCCESS,
                    "New property has been created");
        } catch (EJBException e) {
            if (Utility.causedBySpecifiedExceptionClass(e, PropertyValueNotUniqueException.class)) {
                FacesContext.getCurrentInstance().addMessage("uniqueMessage",
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, Utility.MESSAGE_SUMMARY_ERROR,
                                "Value is not unique."));
                FacesContext.getCurrentInstance().validationFailed();
            } else {
                throw e;
            }
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A method that adds either installation slot or device instance properties. It adds the definition to the device
     * type and property values to already existing installation slots or device instances.
     */
    public void addNewPropertyValueDefs() {
        for (Property selectedProperty : selectedProperties) {
            try {
                final T newPropertyValueInstance = propertyValueClass.newInstance();
                newPropertyValueInstance.setInRepository(false);
                newPropertyValueInstance.setProperty(selectedProperty);
                newPropertyValueInstance.setPropValue(null);
                setPropertyValueParent(newPropertyValueInstance);

                if ((newPropertyValueInstance instanceof ComptypePropertyValue) && isPropertyDefinition) {
                    final ComptypePropertyValue ctPropValueInstance = (ComptypePropertyValue) newPropertyValueInstance;
                    ctPropValueInstance.setPropertyDefinition(true);
                    if (definitionTarget == DefinitionTarget.SLOT) {
                        ctPropValueInstance.setDefinitionTargetSlot(true);
                    } else {
                        ctPropValueInstance.setDefinitionTargetDevice(true);
                    }
                }
                dao.addChild(newPropertyValueInstance);
                addPropertyValueBasedOnDef(newPropertyValueInstance);
                populateAttributesList();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected void addPropertyValueBasedOnDef(T definition) {
        // redefined in descendant
    }

    /**
     * Adds new {@link PropertyValue} to parent {@link ConfigurationEntity}
     * defined in {@link AbstractAttributesController#setArtifactParent(Artifact)}
     *
     * @throws IOException thrown if file in the artifact could not be stored on the file system
     */
    public void addNewArtifact() throws IOException {
        if (importData != null) {
            artifactURI = blobStore.storeFile(new ByteArrayInputStream(importData));
        }

        final S artifactInstance;
        try {
            artifactInstance = artifactClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        artifactInstance.setName(importData != null ? importFileName : artifactName);
        artifactInstance.setInternal(isArtifactInternal);
        artifactInstance.setDescription(artifactDescription);
        artifactInstance.setUri(artifactURI);

        setArtifactParent(artifactInstance);

        try {
            dao.addChild(artifactInstance);
            Utility.showMessage(FacesMessage.SEVERITY_INFO, Utility.MESSAGE_SUMMARY_SUCCESS,
                                                                        "New artifact has been created");
        } finally {
            internalPopulateAttributesList();
        }
    }

    /** Adds new {@link Tag} to parent {@link ConfigurationEntity} */
    public void addNewTag() {
        final String normalizedTag = tag.trim();
        Tag existingTag = tagEJB.findById(normalizedTag);
        if (existingTag == null) {
            existingTag = new Tag(normalizedTag);
        }
        setTagParent(existingTag);
        internalPopulateAttributesList();

        Utility.showMessage(FacesMessage.SEVERITY_INFO, "Tag added", tag);
    }

    /**
     * Deletes attribute from parent {@link ConfigurationEntity}.
     * This attribute can be {@link Tag}, {@link PropertyValue} or {@link Artifact}
     * @throws IOException attribute deletion failure
     */
    public void deleteAttribute() throws IOException {
        Preconditions.checkState(selectedAttribute != null);
        Preconditions.checkState(propertyValueClass != null);
        Preconditions.checkState(artifactClass != null);
        Preconditions.checkState(dao != null);
        try {
            if (selectedAttribute.getEntity().getClass().equals(propertyValueClass)) {
                deletePropertyValue();
            } else if (selectedAttribute.getEntity().getClass().equals(artifactClass)) {
                deleteArtifact();
            } else if (selectedAttribute.getEntity().getClass().equals(Tag.class)) {
                final Tag tagAttr = (Tag) selectedAttribute.getEntity();
                deleteTagFromParent(tagAttr);
                Utility.showMessage(FacesMessage.SEVERITY_INFO, "Tag removed", tagAttr.getName());
            } else {
                throw new UnhandledCaseException();
            }
        } finally {
            selectedAttribute = null;
            internalPopulateAttributesList();
        }
    }

    @SuppressWarnings("unchecked")
    protected void deletePropertyValue() {
        final T propValue = (T) selectedAttribute.getEntity();
        dao.deleteChild(propValue);
        Utility.showMessage(FacesMessage.SEVERITY_INFO, Utility.MESSAGE_SUMMARY_SUCCESS,
                                                                        "Property value has been deleted");
    }

    @SuppressWarnings("unchecked")
    private void deleteArtifact() throws IOException {
        final S artifact = (S) selectedAttribute.getEntity();
        if (artifact.isInternal()) {
            blobStore.deleteFile(artifact.getUri());
        }
        dao.deleteChild(artifact);
        Utility.showMessage(FacesMessage.SEVERITY_INFO, Utility.MESSAGE_SUMMARY_SUCCESS,
                                                                        "Device type artifact has been deleted");
    }

    /**
     * The main method that prepares the fields for any of the following dialogs:
     * <ul>
     * <li>the dialog to modify a property value</li>
     * <li>the dialog to modify an artifact data</li>
     *</ul>
     */
    public void prepareModifyPropertyPopUp() {
        Preconditions.checkState(selectedAttribute != null);
        Preconditions.checkState(propertyValueClass != null);
        Preconditions.checkState(artifactClass != null);
        if (selectedAttribute.getEntity().getClass().equals(propertyValueClass)) {
            prepareModifyPropertyValuePopUp();
        } else if (selectedAttribute.getEntity().getClass().equals(artifactClass)) {
            prepareModifyArtifactPopUp();
        } else {
            throw new UnhandledCaseException();
        }
    }

    @SuppressWarnings("unchecked")
    private void prepareModifyPropertyValuePopUp() {
        final T selectedPropertyValue = (T) selectedAttribute.getEntity();
        property = selectedPropertyValue.getProperty();
        propertyValue = selectedPropertyValue.getPropValue();

        if (selectedAttribute.getEntity() instanceof PropertyValue) {
            propertyNameChangeDisabled = selectedAttribute.getEntity() instanceof DevicePropertyValue
                    || selectedAttribute.getEntity() instanceof SlotPropertyValue
                    || isPropertyValueInherited((PropertyValue)selectedAttribute.getEntity()) ;
        }

        propertyValueUIElement = Conversion.getUIElementFromProperty(property);
        if (Conversion.getBuiltInDataType(property.getDataType()) == BuiltInDataType.USER_DEFINED_ENUM) {
            // if it is an enumeration, get the list of its options from the data type definition field
            enumSelections = Conversion.prepareEnumSelections(property.getDataType());
        } else {
            enumSelections = null;
        }

        // prepare the property selection list
        filterProperties();

        RequestContext.getCurrentInstance().update("modifyPropertyValueForm:modifyPropertyValue");
        RequestContext.getCurrentInstance().execute("PF('modifyPropertyValue').show();");
    }

    @SuppressWarnings("unchecked")
    private void prepareModifyArtifactPopUp() {
        final S selectedArtifact = (S) selectedAttribute.getEntity();
        if (selectedArtifact.isInternal()) {
            importFileName = selectedArtifact.getName();
            artifactName = null;
        } else {
            artifactName = selectedArtifact.getName();
            importFileName = null;
        }
        importData = null;
        artifactDescription = selectedArtifact.getDescription();
        isArtifactInternal = selectedArtifact.isInternal();
        artifactURI = selectedArtifact.getUri();
        isArtifactBeingModified = true;

        RequestContext.getCurrentInstance().update("modifyArtifactForm:modifyArtifact");
        RequestContext.getCurrentInstance().execute("PF('modifyArtifact').show();");
    }

    /** Modifies {@link PropertyValue} */
    @SuppressWarnings("unchecked")
    public void modifyPropertyValue() {
        final T selectedPropertyValue = (T) selectedAttribute.getEntity();
        selectedPropertyValue.setProperty(property);
        selectedPropertyValue.setPropValue(propertyValue);

        try {
            dao.saveChild(selectedPropertyValue);
            Utility.showMessage(FacesMessage.SEVERITY_INFO, Utility.MESSAGE_SUMMARY_SUCCESS,
                                                                        "Property value has been modified");
        } catch (EJBException e) {
            if (Utility.causedBySpecifiedExceptionClass(e, PropertyValueNotUniqueException.class)) {
                FacesContext.getCurrentInstance().addMessage("uniqueMessage",
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, Utility.MESSAGE_SUMMARY_ERROR,
                                "Value is not unique."));
                FacesContext.getCurrentInstance().validationFailed();
            } else {
                throw e;
            }
        } finally {
            internalPopulateAttributesList();
        }
    }

    /** Modifies {@link Artifact} */
    @SuppressWarnings("unchecked")
    public void modifyArtifact() {
        final S selectedArtifact = (S) selectedAttribute.getEntity();
        selectedArtifact.setDescription(artifactDescription);
        selectedArtifact.setUri(artifactURI);
        if (!selectedArtifact.isInternal()) {
            selectedArtifact.setName(artifactName);
        }

        try {
            dao.saveChild(selectedArtifact);
            Utility.showMessage(FacesMessage.SEVERITY_INFO, Utility.MESSAGE_SUMMARY_SUCCESS,
                                                                        "Artifact has been modified");
        } finally {
            internalPopulateAttributesList();
        }
    }

    /**
     * Finds artifact file that was uploaded on the file system and returns it to be downloaded
     *
     * @return Artifact file to be downloaded
     * @throws FileNotFoundException Thrown if file was not found on file system
     */
    @SuppressWarnings("unchecked")
    public StreamedContent getDownloadFile() throws FileNotFoundException {
        final S selectedArtifact = (S) selectedAttribute.getEntity();
        final String filePath = blobStore.getBlobStoreRoot() + File.separator + selectedArtifact.getUri();
        final String contentType = FacesContext.getCurrentInstance().getExternalContext().getMimeType(filePath);

        return new DefaultStreamedContent(new FileInputStream(filePath), contentType, selectedArtifact.getName());
    }

    /** This method determines whether the entity attribute should have the "pencil" icon displayed in the UI.
     * @param attributeView The object containing the UI info for the attribute table row.
     * @return <code>true</code> if the attribute can be edited, <code>false</code> otherwise.
     */
    public boolean canEdit(EntityAttributeView attributeView) {
        final Object attribute = attributeView.getEntity();
        return (attribute instanceof PropertyValue && !(attribute instanceof ComptypePropertyValue))
                    || (attribute instanceof Artifact && !(attribute instanceof ComptypeArtifact));
    }

    /** This method determines whether the entity attribute should have the "trash can" icon displayed in the UI.
     * @param attributeView The object containing the UI info for the attribute table row.
     * @return <code>true</code> if the attribute can be deleted, <code>false</code> otherwise.
     */
    public boolean canDelete(EntityAttributeView attributeView) {
        final Object attribute = attributeView.getEntity();
        return (attribute instanceof Artifact && !(attribute instanceof ComptypeArtifact))
                || (attribute instanceof PropertyValue && !isPropertyValueInherited((PropertyValue)attribute))
                || (attribute instanceof Tag && attributeView.getKind() != EntityAttributeViewKind.DEVICE_TYPE_TAG);
    }

    private boolean isPropertyValueInherited(PropertyValue propertyValue) {
        if (parentProperties == null) {
            return false;
        }

        final String propertyName = propertyValue.getProperty().getName();
        for (final PropertyValue inheritedPropVal : parentProperties) {
            if (propertyName.equals(inheritedPropVal.getProperty().getName())) {
                return true;
            }
        }
        return false;
    }

    protected abstract void setPropertyValueParent(T child);

    protected abstract void setArtifactParent(S child);

    protected abstract void setTagParent(Tag tag);

    protected abstract void deleteTagFromParent(Tag tag);

    /** Filters a list of possible properties to attach to the entity based on the association type. */
    protected abstract void filterProperties();

    protected abstract void populateAttributesList();

    protected abstract void populateParentTags();

    private void internalPopulateAttributesList() {
        fillTagsAutocomplete();
        populateAttributesList();
    }

    private void fillTagsAutocomplete() {
        tagsForAutocomplete = ImmutableList.copyOf(Lists.transform(tagEJB.findAllSorted(), new Function<Tag, String>() {

            @Override
            public String apply(Tag input) {
                return input.getName();
            }
        }));
    }

    /**
     * Returns list of all attributes for current {@link ConfigurationEntity}
     * @return the list of attributes
     */
    public List<EntityAttributeView> getAttributes() {
        return attributes;
    }

    /** Prepares data for addition of {@link PropertyValue} */
    public void prepareForPropertyValueAdd() {
        propertyNameChangeDisabled = false;
        property = null;
        propertyValue = null;
        enumSelections = null;
        selectedAttribute = null;
        propertyValueUIElement = PropertyValueUIElement.NONE;
        selectedProperties = null;
        selectionPropertiesFiltered = null;
        filterProperties();
    }

    /** Prepares the UI data for addition of {@link Tag} */
    public void prepareForTagAdd() {
        fillTagsAutocomplete();
        tag = null;
    }

    /** Prepares the UI data for addition of {@link Artifact} */
    public void prepareForArtifactAdd() {
        artifactName = null;
        artifactDescription = null;
        isArtifactInternal = false;
        artifactURI = null;
        isArtifactBeingModified = false;
        importData = null;
        importFileName = null;
    }

    /**
     * Uploads file to be saved in the {@link Artifact}
     * @param event the {@link FileUploadEvent}
     */
    public void handleImportFileUpload(FileUploadEvent event) {
        try (InputStream inputStream = event.getFile().getInputstream()) {
            this.importData = ByteStreams.toByteArray(inputStream);
            this.importFileName = FilenameUtils.getName(event.getFile().getFileName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** If user changes the type of the artifact, any previously uploaded file gets deleted */
    public void artifactTypeChanged() {
        importData = null;
        importFileName = null;
    }

    /** @return The name of the imported file. */
    public String getImportFileName() {
        return importFileName;
    }

    /** Called by the UI input control to set the value.
     * @param property The property
     */
    public void setProperty(Property property) {
        this.property = property;
    }
    /** @return The property associated with the property value */
    public Property getProperty() {
        return property;
    }

    /** The method called to convert user input into {@link Value} when the user presses "Save" button in the dialog.
     * Called by the UI input control to set the value.
     * @param propertyValue String representation of the property value.
     */
    public void setPropertyValue(String propertyValue) {
        final DataType dataType = selectedAttribute != null ? selectedAttribute.getType() : property.getDataType();
        this.propertyValue = Conversion.stringToValue(propertyValue, dataType);
    }
    /** @return String representation of the property value. */
    public String getPropertyValue() {
        return Conversion.valueToString(propertyValue);
    }

    /** @return The value of the tag */
    public String getTag() {
        return tag;
    }
    /** Called by the UI input control to set the value.
     * @param tag The value of the tag
     */
    public void setTag(String tag) {
        this.tag = tag;
    }

    /** @return The list of {@link Property} entities the user can select from the drop-down control. */
    public List<Property> getFilteredProperties() {
        return filteredProperties;
    }

    /** @return The user specified {@link Artifact} name. */
    public String getArtifactName() {
        return artifactName;
    }
    /** Called by the UI input control to set the value.
     * @param artifactName The user specified {@link Artifact} name.
     */
    public void setArtifactName(String artifactName) {
        this.artifactName = artifactName;
    }

    /** @return The user specified {@link Artifact} description. */
    public String getArtifactDescription() {
        return artifactDescription;
    }
    /** Called by the UI input control to set the value.
     * @param artifactDescription The user specified {@link Artifact} description.
     */
    public void setArtifactDescription(String artifactDescription) {
        this.artifactDescription = artifactDescription;
    }

    /** @return <code>true</code> if the {@link Artifact} is a file attachment, <code>false</code> if its an URL. */
    public boolean isArtifactInternal() {
        return isArtifactInternal;
    }
    /** Called by the UI input control to set the value.
     * @param isArtifactInternal <code>true</code> if the {@link Artifact} is a file attachment, <code>false</code> if its an URL.
     */
    public void setArtifactInternal(boolean isArtifactInternal) {
        this.isArtifactInternal = isArtifactInternal;
    }

    /** @return The URL the user stored in the database. */
    public String getArtifactURI() {
        return artifactURI;
    }
    /** Called by the UI input control to set the value.
     * @param artifactURI The URL to store into the database.
     */
    public void setArtifactURI(String artifactURI) {
        this.artifactURI = artifactURI.trim();
    }

    /** @return <code>true</code> if a "Modify artifact" dialog is open, <code>false</code> otherwise. */
    public boolean isArtifactBeingModified() {
        return isArtifactBeingModified;
    }

    /** @see AbstractAttributesController#setSelectedAttribute(EntityAttributeView)
     * @return the table row (UI view presentation) the user selected
     */
    public EntityAttributeView getSelectedAttribute() {
        return selectedAttribute;
    }
    /** This method is called to store a reference to the attribute the user selected for the action. When the user
     * clicks on the "Action" icon in the attribute table, the reference to the entity represented by that line is
     * stored first, than a generic handler is called which acts on the selected entity.
     *
     * @param selectedAttribute A property value, a tag or an artifact.
     */
    public void setSelectedAttribute(EntityAttributeView selectedAttribute) {
        this.selectedAttribute = selectedAttribute;
    }

    /** @see AbstractAttributesController#setSelectedAttribute(EntityAttributeView)
     * @return A property value, a tag or an artifact.
     */
    public EntityAttributeView getSelectedAttributeToModify() {
        return selectedAttribute;
    }
    /** Equal to {@link AbstractAttributesController#setSelectedAttribute(EntityAttributeView)}, but when modifying
     * an attribute some additional actions need to be performed.
     *
     * @param selectedAttribute the selected attribute
     */
    public void setSelectedAttributeToModify(EntityAttributeView selectedAttribute) {
        this.selectedAttribute = selectedAttribute;
        prepareModifyPropertyPopUp();
    }

    protected void setDao(DAO<? extends ConfigurationEntity> dao) {
        this.dao = dao;
    }

    protected void setPropertyValueClass(Class<T> propertyValueClass) {
        this.propertyValueClass = propertyValueClass;
    }

    protected void setArtifactClass(Class<S> artifactClass) {
        this.artifactClass = artifactClass;
    }

    /**
     * @return <code>true</code> if the property can also be changed when modifying the property value,
     * <code>false</code> otherwise.
     */
    public boolean isPropertyNameChangeDisabled() {
        return propertyNameChangeDisabled;
    }

    /** Used by the {@link Tag} input value control to display the list of auto-complete suggestions. The list contains
     * the tags already stored in the database.
     * @param query The text the user typed so far.
     * @return The list of auto-complete suggestions.
     */
    public List<String> tagAutocompleteText(String query) {
        final List<String> resultList = new ArrayList<String>();
        final String queryUpperCase = query.toUpperCase();
        for (final String element : tagsForAutocomplete) {
            if (element.toUpperCase().startsWith(queryUpperCase))
                resultList.add(element);
        }

        return resultList;
    }

    /** Called when the user selects a new {@link Property} in the dialog drop-down control.
     * @param event {@link javax.faces.event.ValueChangeEvent}
     */
    public void propertyChangeListener(ValueChangeEvent event) {
        // get the newly selected property
        if (event.getNewValue() instanceof Property) {
            final Property newProperty = (Property) event.getNewValue();
            propertyValueUIElement = Conversion.getUIElementFromProperty(newProperty);
            propertyValue = null;
            if (Conversion.getBuiltInDataType(newProperty.getDataType()) == BuiltInDataType.USER_DEFINED_ENUM) {
                // if it is an enumeration, get the list of its options from the data type definition field
                enumSelections = Conversion.prepareEnumSelections(newProperty.getDataType());
            } else {
                enumSelections = null;
            }
        }
    }

    /** @return The type of the UI control to use depending on the {@link PropertyValue} {@link DataType} */
    public PropertyValueUIElement getPropertyValueUIElement() {
        return propertyValueUIElement;
    }
    /**
     * @param propertyValueUIElement The type of the UI control to use depending on the {@link PropertyValue} {@link DataType}
     */
    public void setPropertyValueUIElement(PropertyValueUIElement propertyValueUIElement) {
        this.propertyValueUIElement = propertyValueUIElement;
    }

    /** @return The list of values the user can select a value from if the {@link DataType} is an enumeration. */
    public List<String> getEnumSelections() {
        return enumSelections;
    }
    /**
     * @param enumSelections The list of values the user can select a value from if the {@link DataType} is an enumeration.
     */
    public void setEnumSelections(List<String> enumSelections) {
        this.enumSelections = enumSelections;
    }

    /** The validator for the UI input area when the UI control accepts a matrix of double precision numbers or a list
     * of values for input.
     * Called when saving {@link PropertyValue}
     * @param ctx {@link javax.faces.context.FacesContext}
     * @param component {@link javax.faces.component.UIComponent}
     * @param value The value
     * @throws ValidatorException {@link javax.faces.validator.ValidatorException}
     */
    public void areaValidator(FacesContext ctx, UIComponent component, Object value) throws ValidatorException {
        if (value == null) {
            throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    Utility.MESSAGE_SUMMARY_ERROR, "No value to parse."));
        }
        if (property == null) {
            throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    Utility.MESSAGE_SUMMARY_ERROR, "You must select a property first."));
        }

        final DataType dataType = property != null ? property.getDataType() : selectedAttribute.getType();
        validateMultiLine(value.toString(), dataType);
    }

    protected void validateMultiLine(final String strValue, final DataType dataType) {
        switch (Conversion.getBuiltInDataType(dataType)) {
            case DBL_TABLE:
                validateTable(strValue);
                break;
            case DBL_VECTOR:
                validateDblVector(strValue);
                break;
            case INT_VECTOR:
                validateIntVector(strValue);
                break;
            case STRING_LIST:
                break;
            default:
                throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        Utility.MESSAGE_SUMMARY_ERROR, "Incorrect property data type."));
        }
    }

    private void validateTable(final String value) throws ValidatorException {
        try (Scanner lineScanner = new Scanner(value)) {
            lineScanner.useDelimiter(Pattern.compile(MULTILINE_DELIMITER));

            int lineLength = -1;
            while (lineScanner.hasNext()) {
                // replace unicode no-break spaces with normal ones
                final String line = lineScanner.next().replaceAll("\u00A0", " ");

                try (Scanner valueScanner = new Scanner(line)) {
                    valueScanner.useDelimiter(",\\s*");
                    int currentLineLength = 0;
                    while (valueScanner.hasNext()) {
                        final String dblValue = valueScanner.next().trim();
                        currentLineLength++;
                        try {
                            Double.valueOf(dblValue);
                        } catch (NumberFormatException e) {
                            throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                                    Utility.MESSAGE_SUMMARY_ERROR, "Incorrect value: " + dblValue));
                        }
                    }
                    if (lineLength < 0) {
                        lineLength = currentLineLength;
                    } else if (currentLineLength != lineLength) {
                        throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                                Utility.MESSAGE_SUMMARY_ERROR, "All rows must contain the same number of elements."));
                    }
                }
            }
        }
    }

    private void validateIntVector(final String value) throws ValidatorException {
        try (Scanner scanner = new Scanner(value)) {
            scanner.useDelimiter(Pattern.compile(MULTILINE_DELIMITER));

            while (scanner.hasNext()) {
                String intValue = "<error>";
                try {
                    // replace unicode no-break spaces with normal ones
                    intValue = scanner.next().replaceAll("\\u00A0", " ").trim();
                    Integer.parseInt(intValue);
                } catch (NumberFormatException e) {
                    throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            Utility.MESSAGE_SUMMARY_ERROR, "Incorrect value: " + intValue));
                }
            }
        }
    }

    private void validateDblVector(final String value) throws ValidatorException {
        try (Scanner scanner = new Scanner(value)) {
            scanner.useDelimiter(Pattern.compile(MULTILINE_DELIMITER));

            while (scanner.hasNext()) {
                String dblValue = "<error>";
                try {
                    // replace unicode no-break spaces with normal ones
                    dblValue = scanner.next().replaceAll("\\u00A0", " ").trim();
                    Double.parseDouble(dblValue);
                } catch (NumberFormatException e) {
                    throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            Utility.MESSAGE_SUMMARY_ERROR, "Incorrect value: " + dblValue));
                }
            }
        }
    }

    /** The validator for the UI input field when UI control accepts a double precision number, and integer number or a
     * string for input.
     * Called when saving {@link PropertyValue}
     * @param ctx {@link javax.faces.context.FacesContext}
     * @param component {@link javax.faces.component.UIComponent}
     * @param value The value
     * @throws ValidatorException {@link javax.faces.validator.ValidatorException}
     */
    public void inputValidator(FacesContext ctx, UIComponent component, Object value) throws ValidatorException {
        if (value == null) {
            throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    Utility.MESSAGE_SUMMARY_ERROR, "No value to parse."));
        }

        if (property == null) {
            throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    Utility.MESSAGE_SUMMARY_ERROR, "You must select a property first."));
        }

        final DataType dataType = property != null ? property.getDataType() : selectedAttribute.getType();

        validateSingleLine(value.toString(), dataType);
    }

    protected void validateSingleLine(final String strValue, final DataType dataType) {
        switch (Conversion.getBuiltInDataType(dataType)) {
            case DOUBLE:
                try {
                    Double.parseDouble(strValue.trim());
                } catch (NumberFormatException e) {
                    throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            Utility.MESSAGE_SUMMARY_ERROR, "Not a double value."));
                }
                break;
            case INTEGER:
                try {
                    Integer.parseInt(strValue.trim());
                } catch (NumberFormatException e) {
                    throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            Utility.MESSAGE_SUMMARY_ERROR, "Not an integer number."));
                }
                break;
            case STRING:
                break;
            case TIMESTAMP:
                try {
                    Conversion.toTimestamp(strValue);
                } catch (RuntimeException e) {
                    throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            Utility.MESSAGE_SUMMARY_ERROR, e.getMessage()), e);
                }
                break;
            default:
                throw new ValidatorException(new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        Utility.MESSAGE_SUMMARY_ERROR, "Incorrect property data type."));
        }
    }

    /**  @return The name of the entity displayed in the header */
    public String getEntityName() {
        return entityName;
    }
    /**  @param entityName The name of the entity displayed in the header */
    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    /**
     * @return The width of the UI input control for setting the entity name. The width of the element is calculated
     * to be 8 characters more than its current value, but not more than 40 characters and not less than 20 characters.
     */
    public String getNameElementSize() {
        if ((entityName == null) || (entityName.length() < LO_CONTENT_LEN)) {
            return Integer.toString(MIN_ELEMENT_SIZE);
        }
        final int size = entityName.length() < HI_CONTENT_LEN ? entityName.length() + ELEMENT_SIZE_PADDING
                                : MAX_ELEMENT_SIZE;
        return Integer.toString(size);
    }

    /** @return the filteredAttributes */
    public List<EntityAttributeView> getFilteredAttributes() {
        return filteredAttributes;
    }

    /** @param filteredAttributes the filteredAttributes to set */
    public void setFilteredAttributes(List<EntityAttributeView> filteredAttributes) {
        this.filteredAttributes = filteredAttributes;
    }

    /** @return the attributeKinds */
    public List<SelectItem> getAttributeKinds() {
        return attributeKinds;
    }

    /** @return the selectedProperties */
    public List<Property> getSelectedProperties() {
        return selectedProperties;
    }

    /** @param selectedProperties the selectedProperties to set */
    public void setSelectedProperties(List<Property> selectedProperties) {
        this.selectedProperties = selectedProperties;
    }

    /** @return the selectionPropertiesFiltered */
    public List<Property> getSelectionPropertiesFiltered() {
        return selectionPropertiesFiltered;
    }

    /** @param selectionPropertiesFiltered the selectionPropertiesFiltered to set */
    public void setSelectionPropertiesFiltered(List<Property> selectionPropertiesFiltered) {
        this.selectionPropertiesFiltered = selectionPropertiesFiltered;
    }

    /** This method is called from the UI and resets a table with the implicit ID "propertySelect" in the form
     * indicated by the parameter.
     * @param id the ID of the from containing a table #propertySelect
     */
    public void resetPropertySelection(final String id) {
        final DataTable dataTable = (DataTable) FacesContext.getCurrentInstance().getViewRoot()
                .findComponent(id + ":propertySelect");
        dataTable.setSortBy(null);
        dataTable.setFirst(0);
        dataTable.setFilteredValue(null);
        dataTable.setFilters(null);
    }
}
