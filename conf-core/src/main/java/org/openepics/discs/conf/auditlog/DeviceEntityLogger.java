/**
 * Copyright (c) 2014 European Spallation Source
 * Copyright (c) 2014 Cosylab d.d.
 *
 * This file is part of Controls Configuration Database.
 * Controls Configuration Database is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 2 of the License, or any newer version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see https://www.gnu.org/licenses/gpl-2.0.txt
 */
package org.openepics.discs.conf.auditlog;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.ejb.Stateless;

import org.openepics.discs.conf.ent.AuditRecord;
import org.openepics.discs.conf.ent.Device;
import org.openepics.discs.conf.ent.DeviceArtifact;
import org.openepics.discs.conf.ent.DevicePropertyValue;
import org.openepics.discs.conf.ent.EntityType;
import org.openepics.discs.conf.ent.EntityTypeOperation;

import com.google.common.collect.ImmutableList;

/**
 * {@link AuditRecord} maker for {@link Device}
 *
 * @author Andraz Pozar <andraz.pozar@cosylab.com>
 *
 */
@Stateless
public class DeviceEntityLogger implements EntityLogger {

    @Override
    public Class<?> getType() {
        return Device.class;
    }

    @Override
    public List<AuditRecord> auditEntries(Object value, EntityTypeOperation operation) {
        final Device device = (Device) value;

        final Map<String, String> propertiesMap = new TreeMap<>();
        if (device.getDevicePropertyList() != null) {
            for (DevicePropertyValue propValue : device.getDevicePropertyList()) {
                propertiesMap.put(propValue.getProperty().getName(), propValue.getPropValue());
            }
        }

        final Map<String, String> artifactsMap = new TreeMap<>();
        if (device.getDeviceArtifactList() != null) {
            for (DeviceArtifact artifact : device.getDeviceArtifactList()) {
                artifactsMap.put(artifact.getName(), artifact.getUri());
            }
        }

        return ImmutableList.of((new AuditLogUtil(device).
                removeTopProperties(Arrays.asList("id", "modifiedAt", "modifiedBy", "version", "serialNumber", "componentType")).
                addStringProperty("componentType", device.getComponentType() != null ? device.getComponentType().getName() : null).
                addArrayOfMappedProperties("devicePropertyList", propertiesMap).
                addArrayOfMappedProperties("deviceArtifactList", artifactsMap).
                auditEntry(operation, EntityType.DEVICE, device.getSerialNumber(), device.getId())));
    }
}