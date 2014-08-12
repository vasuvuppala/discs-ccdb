package org.openepics.discs.conf.auditlog;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.openepics.discs.conf.ent.Unit;

public class UnitEntityLoggerTest {

    private Unit unit = new Unit("Ampre", "Current", "A", "BlahBlha", "Miki"); 
    private UnitEntityLogger entLogger = new UnitEntityLogger();
    
    @Before
    public void setUp() {
        unit.setModifiedAt(new Date());
    }
    
    @Test
    public void testGetType() {        
        assertTrue(Unit.class.equals(entLogger.getType()));
    }

    @Test
    public void testSerializeEntity() {
        System.out.println(entLogger.serializeEntity(unit));
    }

}
