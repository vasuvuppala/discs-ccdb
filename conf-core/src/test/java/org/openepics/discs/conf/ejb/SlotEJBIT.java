package org.openepics.discs.conf.ejb;

import static org.junit.Assert.*;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.persistence.ApplyScriptAfter;
import org.jboss.arquillian.persistence.ApplyScriptBefore;
import org.jboss.arquillian.persistence.UsingDataSet;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openepics.discs.conf.ent.Slot;
import org.openepics.discs.conf.ent.Tag;
import org.openepics.discs.conf.util.TestUtility;

/**
 *
 * @author Miroslav Pavleski <miroslav.pavleski@cosylab.com>
 *
 */
@RunWith(Arquillian.class)
public class SlotEJBIT {
    @Inject SlotEJB slotService;
    @Inject ComptypeEJB compTypeService;


    @Inject private TestUtility testUtility;

    @Deployment()
    public static WebArchive createDeployment() {
        return TestUtility.createWebArchive();
    }

    @Before
    public void setUp() throws Exception {
        testUtility.loginForTests();
    }

    @Test
    @UsingDataSet(value={"component_type.xml", "unit.xml", "property.xml", "comptype_property_value.xml", "slot.xml"})
    @ApplyScriptBefore(value={"update_sequences.sql"})
    public void testFindAll() {
        final List<Slot> slots = slotService.findAll();
        assertNotEquals(slots.size(), 0);
    }

    @Test
    @UsingDataSet(value={"component_type.xml", "unit.xml", "property.xml", "comptype_property_value.xml", "slot.xml"})
    @ApplyScriptBefore(value={"update_sequences.sql"})
    public void testFindById() {
        final Slot slot = slotService.findById( slotService.findByName("FE").getId() );
        assertNotNull(slot);
        assertEquals("FE", slot.getName());
    }

    @Test
    @UsingDataSet(value={"component_type.xml", "unit.xml", "property.xml", "comptype_property_value.xml", "slot.xml"})
    @ApplyScriptBefore(value={"update_sequences.sql"})
    public void testByIdInvalid() {
        final Slot slot = slotService.findById(12321321321L);
        assertNull(slot);
    }

    @Test
    @UsingDataSet(value={"component_type.xml", "unit.xml", "property.xml", "comptype_property_value.xml", "slot.xml"})
    @ApplyScriptBefore(value={"update_sequences.sql"})
    public void testFindByName() {
        final Slot slot = slotService.findByName("FE");
        assertNotNull(slot);
        assertEquals("FE", slot.getName());
    }

    @Test
    @UsingDataSet(value={"component_type.xml", "unit.xml", "property.xml", "comptype_property_value.xml", "slot.xml"})
    @ApplyScriptBefore(value={"update_sequences.sql"})
    public void testFindByNameInvalid() {
        final Slot slot = slotService.findByName("R4nd0m_Stuff");
        assertNull(slot);
    }

    @Test
    @UsingDataSet(value={"component_type.xml", "unit.xml", "property.xml", "comptype_property_value.xml", "slot.xml"})
    @ApplyScriptBefore(value={"update_sequences.sql"})
    @ApplyScriptAfter(value={"delete_tags.sql"})
    public void testAdd() {
        final Slot slot = new Slot("ABrandNewSlot", true);

        slot.setDescription("some Description");
        slot.setBeamlinePosition(553.5);
        slot.setComment("comment");
        slot.setComponentType( compTypeService.findByName("QM1") );
        slot.setDescription("A description");
        slot.getPositionInformation().setGlobalPitch(90.0);
        slot.getPositionInformation().setGlobalRoll(180.0);
        slot.getPositionInformation().setGlobalYaw(0.0);
        slot.getPositionInformation().setGlobalX(1.0);
        slot.getPositionInformation().setGlobalY(2.0);
        slot.getPositionInformation().setGlobalZ(3.0);
        final Tag NEW_TAG = new Tag(UUID.randomUUID().toString());
        slot.getTags().add(NEW_TAG);

        slotService.add(slot);

        final Slot newSlot = slotService.findByName("ABrandNewSlot");
        assertNotNull(newSlot);
        assertEquals("ABrandNewSlot", newSlot.getName());
        assertEquals("A description", newSlot.getDescription());
        assertEquals("comment", newSlot.getComment());
        assertEquals("QM1", newSlot.getComponentType().getName());
        assertEquals((Double)553.5, newSlot.getBeamlinePosition());
        assertTrue(newSlot.getTags().contains(NEW_TAG));
        assertEquals((Double)90.0, newSlot.getPositionInformation().getGlobalPitch());
        assertEquals((Double)180.0, newSlot.getPositionInformation().getGlobalRoll());
        assertEquals((Double)0.0, newSlot.getPositionInformation().getGlobalYaw());
        assertEquals((Double)1.0, newSlot.getPositionInformation().getGlobalX());
        assertEquals((Double)2.0, newSlot.getPositionInformation().getGlobalY());
        assertEquals((Double)3.0, newSlot.getPositionInformation().getGlobalZ());
    }

    @Test
    @UsingDataSet(value={"component_type.xml", "unit.xml", "property.xml", "comptype_property_value.xml", "slot.xml"})
    @ApplyScriptBefore(value={"update_sequences.sql"})
    @ApplyScriptAfter(value={"delete_tags.sql"})
    public void testSave() {
        final Slot slot = slotService.findByName("FS1_CSS");

        slot.setDescription("some Description");
        slot.setBeamlinePosition(553.5);
        slot.setComment("comment");
        slot.setComponentType( compTypeService.findByName("QM1") );
        slot.setDescription("A description");
        slot.getPositionInformation().setGlobalPitch(90.0);
        slot.getPositionInformation().setGlobalRoll(180.0);
        slot.getPositionInformation().setGlobalYaw(0.0);
        slot.getPositionInformation().setGlobalX(1.0);
        slot.getPositionInformation().setGlobalY(2.0);
        slot.getPositionInformation().setGlobalZ(3.0);
        final Tag NEW_TAG = new Tag(UUID.randomUUID().toString());
        slot.getTags().add(NEW_TAG);
        slotService.save(slot);

        final Slot newSlot = slotService.findByName("FS1_CSS");
        assertNotNull(newSlot);
        assertEquals("FS1_CSS", newSlot.getName());
        assertEquals("A description", newSlot.getDescription());
        assertEquals("comment", newSlot.getComment());
        assertEquals("QM1", newSlot.getComponentType().getName());
        assertEquals((Double)553.5, newSlot.getBeamlinePosition());
        assertTrue(newSlot.getTags().contains(NEW_TAG));
        assertEquals((Double)90.0, newSlot.getPositionInformation().getGlobalPitch());
        assertEquals((Double)180.0, newSlot.getPositionInformation().getGlobalRoll());
        assertEquals((Double)0.0, newSlot.getPositionInformation().getGlobalYaw());
        assertEquals((Double)1.0, newSlot.getPositionInformation().getGlobalX());
        assertEquals((Double)2.0, newSlot.getPositionInformation().getGlobalY());
        assertEquals((Double)3.0, newSlot.getPositionInformation().getGlobalZ());
    }

    @Test
    @UsingDataSet(value={"component_type.xml", "unit.xml", "property.xml", "comptype_property_value.xml", "slot.xml"})
    @ApplyScriptBefore(value={"update_sequences.sql"})
    @ApplyScriptAfter(value={"delete_tags.sql"})
    public void testDelete() {
    	final Slot slot = slotService.findByName("FS1_CSS");

    	slotService.delete(slot);

        assertNull( slotService.findByName("FS1_CSS") );
    }

    /*
    @Test
    @UsingDataSet(value={"component_type.xml", "unit.xml", "property.xml", "comptype_property_value.xml", "slot.xml"})
    @ApplyScriptBefore(value={"update_sequences.sql"})
    public void testAddSlotProp() {
        final ComponentType compType = slotService.findByName(SEARCH_COMP_TYPE_NAME);

        final SlotPropertyValue compValue = new SlotPropertyValue(false);
        compValue.setProperty( propertyService.findByName("CURRENT") );
        compValue.setComponentType(compType);
        compValue.setUnit( null );
        final String propValue = "33.45";
        compValue.setPropValue(propValue);

        slotService.addChild(compValue);

        final ComponentType newCompType = slotService.findByName(SEARCH_COMP_TYPE_NAME);
        assertNotNull(newCompType);
        assertTrue(newCompType.getSlotPropertyList().contains(compValue));
        final String newPropValue = newCompType.getSlotPropertyList().get( newCompType.getSlotPropertyList().indexOf(compValue) ).getPropValue();
        assertEquals(newPropValue, propValue);
    }

    @Test
    @UsingDataSet(value={"component_type.xml", "unit.xml", "property.xml", "comptype_property_value.xml"})
    @ApplyScriptBefore(value={"update_sequences.sql"})
    public void testSaveSlotProp() {
        final ComponentType compType = slotService.findByName(SEARCH_COMP_TYPE_NAME);
        final SlotPropertyValue compValue = compType.getSlotPropertyList().get(0);
        final String propValue = "22";
        compValue.setPropValue(propValue);
        slotService.saveChild(compValue);

        final ComponentType newCompType = slotService.findByName(SEARCH_COMP_TYPE_NAME);
        final String newPropValue = newCompType.getSlotPropertyList().get( newCompType.getSlotPropertyList().indexOf(compValue) ).getPropValue();
        assertEquals(propValue, newPropValue);
    }

    @Test
    @UsingDataSet(value={"component_type.xml", "unit.xml", "property.xml", "comptype_property_value.xml"})
    @ApplyScriptBefore(value={"update_sequences.sql"})
    public void testDeleteSlotProp() {
        final ComponentType compType = slotService.findByName(SEARCH_COMP_TYPE_NAME);
        final SlotPropertyValue compValue = compType.getSlotPropertyList().get(0);

        slotService.deleteChild(compValue);

        final ComponentType newCompType = slotService.findByName(SEARCH_COMP_TYPE_NAME);
        assertFalse(newCompType.getSlotPropertyList().contains(compValue));
    }*/

}