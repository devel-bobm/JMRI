package jmri.jmrit.operations.locations.gui;

import jmri.jmrit.operations.OperationsTestCase;
import org.junit.Assert;
import org.junit.jupiter.api.*;

/**
 *
 * @author Paul Bender Copyright (C) 2017
 */
public class SpurTableModelTest extends OperationsTestCase {

    @Test
    public void testCTor() {
        SpurTableModel t = new SpurTableModel();
        Assert.assertNotNull("exists",t);
    }

    // private final static Logger log = LoggerFactory.getLogger(SpurTableModelTest.class);

}
