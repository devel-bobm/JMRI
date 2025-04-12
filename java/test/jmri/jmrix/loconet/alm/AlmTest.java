package jmri.jmrix.loconet.alm;

import jmri.jmrix.loconet.LocoNetMessage;
import jmri.util.JUnitUtil;

import org.junit.Assert;
import org.junit.jupiter.api.*;

/**
 *
 * @author B. Milhaupt  Copyright (C) 2022
 */
public class AlmTest {


    @Test
    public void testInterpret7thGenStuff() {

        LocoNetMessage l;

        l = new LocoNetMessage(new int[] {0xE6, 0x10, 2, 0, 2, 0, 1, 2, 2, 
            0x43, 0, 0, 0, 0, 0, 0});
        Assert.assertEquals("check bdl716 'routes' info.", true,
                Alm.isBdl716CapsRpt(l));

        l = new LocoNetMessage(new int[] {0xE6, 0x11, 2, 0, 2, 0, 1, 2, 2, 
            0x43, 0, 0, 0, 0, 0, 0});
        Assert.assertEquals("check bdl716 'routes' info bad 1.", false,
                Alm.isBdl716CapsRpt(l));

        l = new LocoNetMessage(new int[] {0xE6, 0x10, 3, 0, 2, 0, 1, 2, 2, 
            0x43, 0, 0, 0, 0, 0, 0});
        Assert.assertEquals("check bdl716 'routes' info bad 2.", false,
                Alm.isBdl716CapsRpt(l));

        l = new LocoNetMessage(new int[] {0xE6, 0x10, 2, 1, 2, 0, 1, 2, 2, 
            0x43, 0, 0, 0, 0, 0, 0});
        Assert.assertEquals("check bdl716 'routes' info bad 3.", false,
                Alm.isBdl716CapsRpt(l));

        l = new LocoNetMessage(new int[] {0xE6, 0x10, 2, 0, 1, 0, 1, 2, 2, 
            0x43, 0, 0, 0, 0, 0, 0});
        Assert.assertEquals("check bdl716 'routes' info bad 4.", false,
                Alm.isBdl716CapsRpt(l));

        l = new LocoNetMessage(new int[] {0xE6, 0x10, 2, 0, 2, 1, 1, 2, 2, 
            0x43, 0, 0, 0, 0, 0, 0});
        Assert.assertEquals("check bdl716 'routes' info bad 5.", false,
                Alm.isBdl716CapsRpt(l));

        l = new LocoNetMessage(new int[] {0xE6, 0x10, 2, 0, 2, 0, 2, 2, 2, 
            0x43, 0, 0, 0, 0, 0, 0});
        Assert.assertEquals("check bdl716 'routes' info bad 6.", false,
                Alm.isBdl716CapsRpt(l));

        l = new LocoNetMessage(new int[] {0xE6, 0x10, 2, 0, 2, 0, 1, 0, 2, 
            0x43, 0, 0, 0, 0, 0, 0});
        Assert.assertEquals("check bdl716 'routes' info bad 7", false,
                Alm.isBdl716CapsRpt(l));

        l = new LocoNetMessage(new int[] {0xE6, 0x10, 2, 0, 2, 0, 1, 2, 0, 
            0x43, 0, 0, 0, 0, 0, 0});
        Assert.assertEquals("check bdl716 'routes' info bad 8.", false,
                Alm.isBdl716CapsRpt(l));

        l = new LocoNetMessage(new int[] {0xE6, 0x10, 2, 0, 2, 0, 1, 2, 2, 
            0x42, 0, 0, 0, 0, 0, 0});
        Assert.assertEquals("check bdl716 'routes' info bad 9.", false,
                Alm.isBdl716CapsRpt(l));

        l = new LocoNetMessage(new int[] {0xE6, 0x10, 2, 0, 2, 0, 1, 2, 2, 
            0x44, 0, 0, 0, 0, 0, 0});
        Assert.assertEquals("check bdl716 'routes' info bad 10.", false,
                Alm.isBdl716CapsRpt(l));

        l = new LocoNetMessage(new int[] {0xE6, 0x10, 2, 0, 2, 0, 1, 2, 2, 
            0x45, 1, 0, 0, 0, 0, 0});
        Assert.assertEquals("check bdl716 'routes' info bad 11.", false,
                Alm.isBdl716CapsRpt(l));

    }

    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();
    }

    @AfterEach
    public void tearDown() {
        JUnitUtil.tearDown();
    }

}
