/**
 * Copyright (c) 2014 European Spallation Source
 * Copyright (c) 2014 Cosylab d.d.
 *
 * This file is part of Controls Configuration Database.
 * Controls Configuration Database is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 2 of the License, or any newer version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see https://www.gnu.org/licenses/gpl-2.0.txt
 */
package org.openepics.discs.conf.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;

import org.openepics.discs.conf.ejb.ComptypeEJB;
import org.openepics.discs.conf.ejb.DeviceEJB;
import org.openepics.discs.conf.ejb.PropertyEJB;
import org.openepics.discs.conf.ejb.SlotEJB;
import org.openepics.discs.conf.ent.ComponentType;
import org.openepics.discs.conf.ent.ComptypeArtifact;
import org.openepics.discs.conf.ent.ComptypePropertyValue;
import org.openepics.discs.conf.ent.Device;
import org.openepics.discs.conf.ent.DevicePropertyValue;
import org.openepics.discs.conf.ent.Property;
import org.openepics.discs.conf.ent.PropertyAssociation;
import org.openepics.discs.conf.ent.Slot;
import org.openepics.discs.conf.ent.SlotPropertyValue;
import org.openepics.discs.conf.ent.Tag;
import org.openepics.discs.conf.ui.common.AbstractAttributesController;
import org.openepics.discs.conf.views.EntityAttributeView;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

/**
 * Controller bean for manipulation of {@link ComponentType} attributes
 *
 * @author Andraz Pozar <andraz.pozar@cosylab.com>
 * @author Miha Vitorovič <miha.vitorovic@cosylab.com>
 *
 */
@Named
@ViewScoped
public class ComptypeAttributesController extends AbstractAttributesController<ComptypePropertyValue, ComptypeArtifact> {

    @Inject private ComptypeEJB comptypeEJB;
    @Inject private PropertyEJB propertyEJB;
    @Inject private SlotEJB slotEJB;
    @Inject private DeviceEJB deviceEJB;

    private ComponentType compType;

    @PostConstruct
    public void init() {
        final Long id = Long.parseLong(((HttpServletRequest)FacesContext.getCurrentInstance().getExternalContext().getRequest()).getParameter("id"));
        compType = comptypeEJB.findById(id);
        super.setArtifactClass(ComptypeArtifact.class);
        super.setPropertyValueClass(ComptypePropertyValue.class);
        super.setDao(comptypeEJB);
        populateAttributesList();
        filterProperties();
    }

    /**
     * Redirection back to view of all {@link ComponentType}s
     */
    public void deviceTypeRedirect() {
        try {
            FacesContext.getCurrentInstance().getExternalContext().redirect("device-types-manager.xhtml");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addNewPropertyValue() {
        super.addNewPropertyValue();

        if (propertyValue == null) {
            for (Slot slot : slotEJB.findByComponentType(compType)) {
                final SlotPropertyValue newSlotProperty = new SlotPropertyValue();
                newSlotProperty.setProperty(propertyValueInstance.getProperty());
                newSlotProperty.setSlot(slot);
                slotEJB.addChild(newSlotProperty);
            }

            for (Device device : deviceEJB.findDevicesByComponentType(compType)) {
                final DevicePropertyValue newDeviceProperty = new DevicePropertyValue();
                newDeviceProperty.setProperty(propertyValueInstance.getProperty());
                newDeviceProperty.setDevice(device);
                deviceEJB.addChild(newDeviceProperty);
            }
        }
    }

    @Override
    protected void deletePropertyValue() {
        super.deletePropertyValue();

        final ComptypePropertyValue propValue = (ComptypePropertyValue) selectedAttribute.getEntity();
        if (propValue.getPropValue() == null) {
            for (Slot slot : slotEJB.findByComponentType(compType)) {
                for (SlotPropertyValue slotPropValue : slot.getSlotPropertyList()) {
                    if (slotPropValue.getProperty().equals(propValue.getProperty()) && slotPropValue.getPropValue() == null) {
                        slotEJB.deleteChild(slotPropValue);
                    }
                }
            }

            for (Device device : deviceEJB.findDevicesByComponentType(compType)) {
                for (DevicePropertyValue devicePropValue : device.getDevicePropertyList()) {
                    if (devicePropValue.getProperty().equals(propValue.getProperty()) && devicePropValue.getPropValue() == null) {
                        deviceEJB.deleteChild(devicePropValue);
                    }
                }
            }
        }
    }

    @Override
    protected void populateAttributesList() {
        attributes = new ArrayList<>();
        // refresh the component type from database. This refreshes all related collections as well.
        compType = comptypeEJB.findById(this.compType.getId());
        for (ComptypePropertyValue prop : compType.getComptypePropertyList()) {
            attributes.add(new EntityAttributeView(prop));
        }

        for (ComptypeArtifact art : compType.getComptypeArtifactList()) {
            attributes.add(new EntityAttributeView(art));
        }

        for (Tag tag : compType.getTags()) {
            attributes.add(new EntityAttributeView(tag));
        }
    }

    @Override
    protected void filterProperties() {
        filteredProperties = ImmutableList.copyOf(Collections2.filter(propertyEJB.findAll(), new Predicate<Property>() {
            @Override
            public boolean apply(Property property) {
                final PropertyAssociation propertyAssociation = property.getAssociation();
                return propertyAssociation == PropertyAssociation.ALL || propertyAssociation == PropertyAssociation.TYPE || propertyAssociation == PropertyAssociation.TYPE_DEVICE || propertyAssociation == PropertyAssociation.TYPE_SLOT;
            }
        }));
    }

    /**
     * Returns {@link ComponentType} for which attributes are being manipulated
     */
    public ComponentType getDeviceType() {
        return compType;
    }

    @Override
    protected void setPropertyValueParent(ComptypePropertyValue child) {
        child.setComponentType(compType);
    }

    @Override
    protected void setArtifactParent(ComptypeArtifact child) {
        child.setComponentType(compType);
    }

    @Override
    public void setTagParent(Tag tag) {
        final Set<Tag> existingTags = compType.getTags();
        if (!existingTags.contains(tag)) {
            existingTags.add(tag);
            comptypeEJB.save(compType);
        }
    }

    @Override
    protected void deleteTagFromParent(Tag tag) {
        compType.getTags().remove(tag);
        comptypeEJB.save(compType);
    }

}