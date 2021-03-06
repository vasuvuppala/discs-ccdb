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
package org.openepics.discs.ccdb.gui.views;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.openepics.discs.ccdb.model.ComponentType;
import org.openepics.discs.ccdb.model.ConfigurationEntity;
import org.openepics.discs.ccdb.model.Device;
import org.openepics.discs.ccdb.model.NamedEntity;
import org.openepics.discs.ccdb.model.Slot;
import org.openepics.discs.ccdb.model.Tag;
import org.openepics.discs.ccdb.core.util.UnhandledCaseException;

/**
 * @author <a href="mailto:miha.vitorovic@cosylab.com">Miha Vitorovič</a>
 *
 * @param <E> the type of the view parent entity
 */
public class EntityAttrTagView<E extends ConfigurationEntity & NamedEntity> extends EntityAttributeView<E> {
    private static final long serialVersionUID = 1L;

    private Tag entity;

    /**
     * @param entity the {@link Tag}
     * @param viewParent the view parent of the {@link Tag} (the one selected in the table)
     * @param tagParent the actual parent of the {@link Tag} (usually {@link ComponentType})
     * @param <P> the type of the of the actual parent
     */
    public <P extends ConfigurationEntity & NamedEntity> EntityAttrTagView(Tag entity, E viewParent, P tagParent) {
        super(viewParent, tagParent != null ? tagParent.getName() : "");
        this.entity = entity;
        setKind(tagParent == null ? getEntityKind(viewParent) : getEntityKind(tagParent));
    }

    /**
     * @param entity the {@link Tag}
     * @param viewParent the {@link Tag} parent
     */
    public EntityAttrTagView(Tag entity, E viewParent) {
        this(entity, viewParent, null);
    }

    /** Constructor for new {@link Tag}
     * @param parent the {@link Tag} parent
     */
    public EntityAttrTagView(E parent) {
        super(parent);
        entity = new Tag();
        setKind(getEntityKind(parent));
    }

    /** @return The value of the tag */
    @NotNull
    @Size(min = 1, max = 255, message = "Tag can have at most 255 characters.")
    public String getTag() {
        return entity.getName();
    }

    /** Called by the UI input control to set the value.
     * @param tag The value of the tag
     */
    public void setTag(String tag) {
        entity.setName(tag);
    }

    @Override
    public Tag getEntity() {
        return entity;
    }

    @Override
    public String getId() {
        return "TAG_" + entity.getName();
    }

    @Override
    public String getName() {
        return entity.getName();
    }

    @Override
    public String getValue() {
        return null;
    }

    private <P extends ConfigurationEntity> EntityAttributeViewKind getEntityKind(P entity) {
        if (entity instanceof ComponentType) return EntityAttributeViewKind.DEVICE_TYPE_TAG;
        if (entity instanceof Slot) {
            if (((Slot) entity).isHostingSlot()) {
                return EntityAttributeViewKind.INSTALL_SLOT_TAG;
            } else {
                return EntityAttributeViewKind.CONTAINER_SLOT_TAG;
            }
        }
        if (entity instanceof Device) return EntityAttributeViewKind.DEVICE_TAG;
        throw new UnhandledCaseException();
    }
}
