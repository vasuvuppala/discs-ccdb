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
package org.openepics.discs.ccdb.core.ejb;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.persistence.NoResultException;

import org.openepics.discs.ccdb.core.auditlog.Audit;
import org.openepics.discs.ccdb.model.ComponentType;
import org.openepics.discs.ccdb.model.Device;
import org.openepics.discs.ccdb.model.EntityTypeOperation;
import org.openepics.discs.ccdb.model.InstallationRecord;
import org.openepics.discs.ccdb.model.Slot;
import org.openepics.discs.ccdb.core.security.Authorized;
import org.openepics.discs.ccdb.core.util.CRUDOperation;

import com.google.common.base.Preconditions;

/**
 *
 * @author vuppala
 * @author <a href="mailto:miroslav.pavleski@cosylab.com">Miroslav Pavleski</a>
 */
@Stateless
public class InstallationEJB extends DAO<InstallationRecord> {
    private static final Logger LOGGER = Logger.getLogger(InstallationEJB.class.getCanonicalName());

    /**
     * @param slot the installation slot to find active installation record for.
     * @return The currently active installation record for slot (an installation record which has uninstall date <code>NULL</code>),
     * <code>null</code> otherwise.
     */
    public InstallationRecord getActiveInstallationRecordForSlot(Slot slot) {
        Preconditions.checkNotNull(slot);
        try {
            return em.createNamedQuery("InstallationRecord.activeRecordForSlot", InstallationRecord.class)
                .setParameter("slot", slot).getSingleResult();
        } catch (NoResultException e) { // NOSONAR
            // no result is not an exception
            return null;
        }
    }

    /**
     * @param device the device instance to find active installation record for.
     * @return The currently active installation record for device (an installation record which has uninstall date <code>NULL</code>),
     * <code>null</code> otherwise.
     */
    public InstallationRecord getActiveInstallationRecordForDevice(Device device) {
        Preconditions.checkNotNull(device);
        try {
            return em.createNamedQuery("InstallationRecord.activeRecordForDevice", InstallationRecord.class)
                .setParameter("device", device).getSingleResult();
        } catch (NoResultException e) { // NOSONAR
            // no result is not an exception
            return null;
        }
    }

    /**
     * @param slot the installation slot to find last installation record for.
     * @return The last installation record for slot (an installation record which has uninstall date <code>NULL</code>),
     * <code>null</code> otherwise.
     */
    public InstallationRecord getLastInstallationRecordForSlot(Slot slot) {
        Preconditions.checkNotNull(slot);
        try {
            return em.createNamedQuery("InstallationRecord.lastRecordForSlot", InstallationRecord.class)
                .setParameter("slot", slot).getSingleResult();
        } catch (NoResultException e) { // NOSONAR
            // no result is not an exception
            return null;
        }
    }

    /**
     * @param device the device to find last installation record for.
     * @return The last installation record for device (an installation record which has uninstall date <code>NULL</code>),
     * <code>null</code> otherwise.
     */
    public InstallationRecord getLastInstallationRecordForDevice(Device device) {
        Preconditions.checkNotNull(device);
        try {
            return em.createNamedQuery("InstallationRecord.lastRecordForDevice", InstallationRecord.class)
                .setParameter("device", device).getSingleResult();
        } catch (NoResultException e) { // NOSONAR
            // no result is not an exception
            return null;
        }
    }

    /**
     * @param componentType the device type for which we are requesting information.
     * @return The list of all device instances which are not installed into any installation slot.
     */
    public List<Device> getUninstalledDevices(ComponentType componentType) {
        Preconditions.checkNotNull(componentType);
        return em.createNamedQuery("Device.uninstalledDevicesByType", Device.class).
                setParameter("componentType", componentType).getResultList();
    }

    @Override
    @CRUDOperation(operation=EntityTypeOperation.CREATE)
    @Audit
    @Authorized
    public void add(InstallationRecord record) {
        final Device device = record.getDevice();
        final Slot slot = record.getSlot();
        Preconditions.checkNotNull(device);
        Preconditions.checkNotNull(slot);
        if (!slot.getComponentType().equals(device.getComponentType())) {
            LOGGER.log(Level.WARNING, "The device and installation slot device types do not match.");
            throw new RuntimeException("The device and installation slot device types do not match.");
        }
        // we must check whether the selected slot is already occupied or selected device is already installed
        final InstallationRecord slotCheck = getActiveInstallationRecordForSlot(slot);
        if (slotCheck != null) {
            LOGGER.log(Level.WARNING, "An attempt was made to install a device in an already occupied slot.");
            throw new RuntimeException("Slot already occupied.");
        }
        final InstallationRecord deviceCheck = getActiveInstallationRecordForDevice(device);
        if (deviceCheck != null) {
            LOGGER.log(Level.WARNING, "An attempt was made to install a device that is already installed.");
            throw new RuntimeException("Device already installed.");
        }

        device.getInstallationRecordList().add(record);
        slot.getInstallationRecordList().add(record);
        super.add(record);
    }

    @Override
    @CRUDOperation(operation=EntityTypeOperation.UPDATE)
    @Audit
    @Authorized
    public void save(InstallationRecord record) {
        super.save(record);
        getLastInstallationRecordForDevice(record.getDevice()).setUninstallDate(record.getUninstallDate());
        getLastInstallationRecordForSlot(record.getSlot()).setUninstallDate(record.getUninstallDate());
    }

    @Override
    protected Class<InstallationRecord> getEntityClass() {
        return InstallationRecord.class;
    }
}
