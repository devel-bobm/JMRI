package jmri.jmrix.loconet.lnsvf2.swing;

import java.awt.Component;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.List;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;

import jmri.*;
import jmri.jmrit.decoderdefn.DecoderFile;
import jmri.jmrit.decoderdefn.DecoderIndexFile;
import jmri.jmrit.roster.Roster;
import jmri.jmrit.roster.RosterEntry;
import jmri.jmrit.symbolicprog.CvTableModel;
import jmri.jmrit.symbolicprog.VariableTableModel;
import jmri.jmrit.symbolicprog.tabbedframe.PaneOpsProgFrame;
import jmri.jmrit.symbolicprog.DccAddressVarHandler;

import jmri.jmrix.loconet.LnSv2DevicesManager;
import jmri.jmrix.loconet.lnsvf2.Sv2Device;
import jmri.jmrix.loconet.lnsvf2.Sv2ProgrammingTool;
import jmri.jmrix.loconet.LocoNetSystemConnectionMemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data model for table which displays LocoNet SV2 devices.
 *
 * Provides mechanisms to
 * - Perform "Discovery" of SV2 devices
 * - Change the "Destination address" of an SV2 device
 * - Perform "Reconigure/Reset" operation on an SV2 device
 * - Relate the SV2 device data to a decoder definition
 * - Relate the SV2 device data to a Roster entry
 * - Open the "symbolic programmer" associated with the Roster
 *      entry which has been associated with an SV2 device
 * <hr>
 * This file is part of JMRI.
 * <p>
 * JMRI is free software; you can redistribute it and/or modify it under the
 * terms of version 2 of the GNU General Public License as published by the Free
 * Software Foundation. See the "COPYING" file for a copy of this license.
 * <p>
 * JMRI is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * @author B. Milhaupt Copyright (c) 2020
 */
public class Sv2DeviceDataModel extends AbstractTableModel
         implements PropertyChangeListener, Sv2ProgrammingTool {
    // Table'rosterEntryName Column numbers
    static public final int ITEMCOLUMN = 0;
    static public final int ADDRESSCOLUMN = 1;
    static public final int DEVICENAMECOLUMN = 2;
    static public final int MFGCOLUMN = 3;
    static public final int PRODUCTIDCOLUMN = 4;
    static public final int DEVELOPERIDCOLUMN = 5;
    static public final int SERIALNUMCOLUMN = 6;
    static public final int SOFTWAREVERCOLUMN = 7;
    static public final int ROSTERENTRYCOLUMN = 8;
    static public final int CHGADDRBUTTONCOLUMN = 9;
    static public final int RECFGRESETCOLUMN = 10;
    static public final int OPENPRGMRBUTTONCOLUMN = 11;
    static public final int NUMCOLUMNS = 12;

    // other useful fields
    private final transient LocoNetSystemConnectionMemo memo;
    protected Roster _roster;
    protected Component parentComponent;
    protected LnSv2DevicesManager lnsv2dm;

    String address = "3";
    boolean shortAddr = false;

    /**
     * Create an Sv2DeviceDataModel.
     *
     * @param memo LocoNetSystemConnection memo for the connection
     */
    public Sv2DeviceDataModel(LocoNetSystemConnectionMemo memo) {
        super();
        this.memo = memo;
        _roster = Roster.getDefault();
        memo.getSv2DevicesManager().addPropertyChangeListener(this);
        this.lnsv2dm = memo.getSv2DevicesManager();
    }

    /**
     * Return the number of rows to be displayed. This can vary depending on
     * whether only active rows are displayed, and whether the system slots
     * should be displayed.
     * <p>
     * This should probably use a local cache instead of counting/searching each
     * time.
     *
     * @return the number of rows
     */
    @Override
    public int getRowCount() {

        if (memo == null) {
            log.debug("getRowCount() memo is null!");
            return 0;
        }
        return memo.getSv2DevicesManager().getDeviceList().size();
    }

    @Override
    public int getColumnCount() {
        return NUMCOLUMNS;
    }

    @Override
    public String getColumnName(int col) {
        switch (col) {
            case ITEMCOLUMN:
                return "Entry";
            case ADDRESSCOLUMN:
                return "Address";
            case DEVICENAMECOLUMN:
                return "Device Name";
            case MFGCOLUMN:
                return "Manufacture #";
            case PRODUCTIDCOLUMN:
                return "Product ID #";
            case DEVELOPERIDCOLUMN:
                return "Developer ID #";
            case ROSTERENTRYCOLUMN:
                return "Roster Entry";
            case SERIALNUMCOLUMN:
                return "Serial #";
            case SOFTWAREVERCOLUMN:
                return "S/W Version #";
            case CHGADDRBUTTONCOLUMN:
                return "Change Address";
            case RECFGRESETCOLUMN:
                return "Reconfig/Reset";
            case OPENPRGMRBUTTONCOLUMN:
                return "Configuration";
            default:
                return null;
        }
    }

    @Override
    public Class<?> getColumnClass(int col) {
        switch (col) {
            case ITEMCOLUMN:
            case MFGCOLUMN:
            case PRODUCTIDCOLUMN:
            case DEVELOPERIDCOLUMN:
            case SERIALNUMCOLUMN:
            case SOFTWAREVERCOLUMN:
                return Integer.class;
            case ADDRESSCOLUMN:
            case DEVICENAMECOLUMN:
            case ROSTERENTRYCOLUMN:
                return String.class;
            case CHGADDRBUTTONCOLUMN:
            case RECFGRESETCOLUMN:
            case OPENPRGMRBUTTONCOLUMN:
                return JButton.class;
            default:
                return null;
        }
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return ((col == CHGADDRBUTTONCOLUMN) || (col == RECFGRESETCOLUMN) ||
                (col == OPENPRGMRBUTTONCOLUMN));
    }

    @Override
    public Object getValueAt(int row, int col) {
        if (getRowCount() == 0) {
            return null;
        }
        Sv2Device dev = memo.getSv2DevicesManager().getDeviceList().getDevice(row);
        switch (col) {
            case ITEMCOLUMN:
                return row+1;
            case MFGCOLUMN:
                return dev.getManufacturerID();
            case PRODUCTIDCOLUMN:
                return dev.getProductID();
            case DEVELOPERIDCOLUMN:
                return dev.getDeveloperID();
            case SERIALNUMCOLUMN:
                return dev.getSerialNum();
            case ADDRESSCOLUMN:
                return dev.getDestAddr();
            case DEVICENAMECOLUMN:
                if (dev.getDeviceName().length() == 0) {
                    log.trace("dev.getDevName() = {}",dev.getDeviceName());
                            List<DecoderFile> l =
                                    InstanceManager.getDefault(
                                            DecoderIndexFile.class).
                                            matchingDecoderList(
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    String.valueOf(dev.getDeveloperID()),
                                                    String.valueOf(dev.getManufacturerID()),
                                                    String.valueOf(dev.getProductID())
                                            );
                            log.trace("found {} possible matches", l.size());
                            String lastModelName="";
                            DecoderFile foundd = null;
                            if (l.size()>0) {
                                for (DecoderFile d: l) {
                                    if (d.getModel().equals("")) {
                                        log.debug("model is empty");
                                    }
                                    foundd = d;
                                    log.debug("\tfound title {}, model {}, family {}",
                                            foundd.titleString(), foundd.getModel(),
                                        foundd.getFamily());
                                }
                                log.debug("Using: title {}, model {}, family {}, taking the last entry...",
                                        foundd.titleString(), foundd.getModel(),
                                        foundd.getFamily());
                                lastModelName=foundd.getModel();
                                dev.setDevName(lastModelName);
                                dev.setDecoderFile(l.get(l.size()-1));
                            }
                            return lastModelName;
                }
                return dev.getDeviceName();
            case ROSTERENTRYCOLUMN:
                return dev.getRosterName();
            case SOFTWAREVERCOLUMN:
                return dev.getSwVersion();
            case CHGADDRBUTTONCOLUMN:
                return "Change Addr.";
            case RECFGRESETCOLUMN:
                return "Recfg/Reset";
            case OPENPRGMRBUTTONCOLUMN:
                if (dev.getRosterName().length() == 0) {
                    return "Create Roster Entry";
                }
                return "Program Device's SVs";
            default:
                return null;
        }
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        log.debug("executing setvalueat for row {}, col {}, value {}", row, col, value);
        if (getRowCount() <= row) {
            // prevent update of a row that does not yet exist
            return;
        }
        Sv2Device dev = memo.getSv2DevicesManager().getDeviceList().getDevice(row);
        switch(col) {
            case ADDRESSCOLUMN:
                if (value.getClass().equals(String.class)) {
                    int addr = Integer.parseInt((String)value);
                    dev.setDestAddr(addr);
                }
                break;
            case DEVICENAMECOLUMN:
                dev.setDevName((String)value);
                break;
            case ROSTERENTRYCOLUMN:
                dev.setRosterName((String)value);
                break;
            case CHGADDRBUTTONCOLUMN:
                setNewAddress(row);
                break;
            case RECFGRESETCOLUMN:
                memo.getSv2DevicesManager().reconfigResetDevice(dev);
                break;
            case OPENPRGMRBUTTONCOLUMN:
                if (((String)getValueAt(row, OPENPRGMRBUTTONCOLUMN)).
                        compareTo("Create Roster Entry")==0) {
                    createRosterEntry(dev, row);
                    if (dev.getRosterEntry() != null) {
                        setValueAt(dev.getRosterName(),row, col);
                    }
                } else if (((String)getValueAt(row,
                        OPENPRGMRBUTTONCOLUMN))
                        .compareTo("Program Device's SVs")==0) {
                    openProgrammer(row);
                }
                break;
            default:
                // no change, so do not fire a property change event
                return;
        }
        if (getRowCount() >= 1) {
            this.fireTableRowsUpdated(row, row);
        }
    }

    private void setNewAddress(int row) {
        Sv2Device dev = memo.getSv2DevicesManager().getDeviceList().getDevice(row);
        boolean gotValidAddr = false;
        int newVal = -1;
        while (!gotValidAddr) {
            String s = (String)JOptionPane.showInputDialog(
                    null,
                    "Enter the new address.  Valid addresses \n"+
                            "are whole numbers between 1 and \n"+
                            "65535, inclusive.",
                    "Change Board Address",
                    JOptionPane.OK_CANCEL_OPTION,
                    null,
                    null,
                    Integer.toString(dev.getDestAddr()));
            if (s == null) {
                log.debug("Address change canceled");
                // user hit "cancel" button
                return;
            }
            if ((s.length() >=1) && (s.length() <= 5)) {
                try {
                    int i = Integer.parseInt(s);
                    if ((i > 0) && (i < 65536)) {
                        newVal = i;
                        gotValidAddr = true;
                    }
                } catch (NumberFormatException e) {
                    log.warn("invalid value '{}'", s);
                }
            }
        }
        log.debug("request to set address for row {}, dev {}, mfgID {}, prodID {},"
                + " develID {} to address {}.",
                row, dev.getDeviceName(), dev.getManufacturerID(),
                dev.getProductID(), dev.getDeveloperID(), newVal);
        // Do not simply set the device data structure to the new value.  Instead, send the
        // appropriate LocoNet message, and wait for device reply to update the data structure.
        memo.getSv2DevicesManager().reprogramDeviceAddress(dev, newVal);
    }

    /*
     * Process the "property change" events from Sv2DevicesManager
     * @param evt event
     *
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // these messages can arrive without a complete
        // GUI, in which case we just ignore them
        log.debug("property change received event: name {} old {} new {}",
                evt.getPropertyName(),evt.getOldValue(), evt.getNewValue());

        /* always use fireTableDataChanged() because it does not always
            resize columns to "preferred" widths!
            This may slow things down, but that is a small price to pay!
        */
        fireTableDataChanged();
    }

    /**
     * Set the parent component
     * @param c parent Component
     */
    public void setParent (Component c) {
        this.parentComponent = c;
    }

    private void openProgrammer(int row) {
        Sv2Device dev = memo.getSv2DevicesManager().getDeviceList().getDevice(row);

        LnSv2DevicesManager.ProgrammingResult result = lnsv2dm.prepareForSymbolicProgrammer(dev, this);
        switch (result) {
            case SUCCESS_PROGRAMMER_OPENED:
                return;
            case FAIL_NO_SUCH_DEVICE:
                JOptionPane.showMessageDialog(parentComponent,
                        "Device no found on LocoNet. Re-try the &quot;SV2 "
                                + "Device Discovery&quot' process and try again. "
                                + "Cannot open the programmer!",
                        "Open Roster Entry", 0);
                return;
            case FAIL_NO_APPROPRIATE_PROGRAMMER:
                JOptionPane.showMessageDialog(parentComponent,
                        "No suitable programmer available for this LocoNet "
                                + "connection.  Cannot open the programmer!",
                        "Open Roster Entry", 0);
                return;
            case FAIL_NO_MATCHING_ROSTER_ENTRY:
                JOptionPane.showMessageDialog(parentComponent,
                        "There does not appear to be a roster entry for this "
                        + "device.  Cannot open the programmer!",
                        "Open Roster Entry", 0);
                return;
            case FAIL_DESTINATION_ADDRESS_IS_ZERO:
                JOptionPane.showMessageDialog(parentComponent,
                        "Device is at address 0.  Re-configure device address "
                                + "to a non-zero value before programming! "
                                + "Canceling operation!", "Open Roster Entry", 0);
                return;
            case FAIL_MULTIPLE_DEVICES_SAME_DESTINATION_ADDRESS:
                JOptionPane.showMessageDialog(parentComponent,
                        "Should not program as there are multiple devices with "
                                + "device address "+dev.getDestAddr()
                                +" present on LocoNet.  Canceling operation!",
                        "Open Roster Entry", 0);
                return;
            case FAIL_NO_ADDRESSED_PROGRAMMER:
                JOptionPane.showMessageDialog(parentComponent,
                        "No addressed programmer available for this LocoNet "
                                + "connection.  Cannot open the programmer!",
                        "Open Roster Entry", 0);
                return;
            case FAIL_NO_LNSV_PROGRAMMER:
                log.debug("failed. no lnsv2 programmer...");
                JOptionPane.showMessageDialog(parentComponent,
                        "LNSV2 programming mode is not available on this "
                                + "connection.  Cannot open the programmer!",
                        "Open Roster Entry", 0);
                return;
            default:
                JOptionPane.showMessageDialog(parentComponent,
                        "Unknown error occured.  Cannot open programmer."
                                + " Cannot open the programmer!",
                        "Open Roster Entry", 0);
                return;

        }
    }

    public void openPaneOpsProgFrame(RosterEntry re, String name,
            String programmerFile, Programmer p) {
        log.debug("Attempting to open programmer, re={}, name={}, prorammerFile={}, programmer={}",
                re, name, programmerFile, p);

        DecoderFile decoderFile = InstanceManager.getDefault(
                DecoderIndexFile.class).fileFromTitle(re.getDecoderModel());

        PaneOpsProgFrame progFrame =
                new PaneOpsProgFrame(decoderFile, re, name, programmerFile, p);

        progFrame.pack();
        progFrame.setVisible(true);
    }

    public boolean anyOtherRowsAddressSame( int row) {
        int targetAddress = (Integer)getValueAt(row,ADDRESSCOLUMN);
        if (targetAddress == 0) {
            return false; // always return false if address 0.
        }
        for (int i = 0; i < getRowCount(); ++i) {
            if (i != row) {
                // only check rows other then the row that is targeted.
                int checkAddress = (Integer)getValueAt(i, ADDRESSCOLUMN);
                if (checkAddress == targetAddress) {
                    return true;
                }
            }
        }
        return false;
    }

    private void createRosterEntry(Sv2Device dev, int row) {
        CvTableModel cvModel = null;
        VariableTableModel variableModel;

        if (dev.getDestAddr() == 0) {
            JOptionPane.showMessageDialog(parentComponent,
                    "Cannot create a roster entry when the destination address"
                            + " is 0.  Canceling operation."
                    , "Create Roster Entry", 0);
            return;
        }

        String rosterEntryName = null;
        while (rosterEntryName == null) {
            rosterEntryName = JOptionPane.showInputDialog(parentComponent,
                "Enter a name for the roster entry", "");
            if (rosterEntryName==null) {
                // cancel button hit
                return;
            }
        }

        log.debug("got here to create the data entry");
        if ((dev.getDecoderFile() == null)) {
            log.warn("cannot create Roster Entry account null decoderfile");
            return;
        }
        
        if ((dev.getDecoderFile().getFileName() == null)) {
            log.warn("cannot create Roster Entry account null file name");
            return;
        }

        RosterEntry re = new RosterEntry();
        
        log.warn("re is null; creating RosterEntry");
        re.setDecoderFamily(dev.getDecoderFile().getFamily());
        re.setDecoderModel(dev.getDecoderFile().getModel());
        re.setId(rosterEntryName);
        re.setDeveloperID(dev.getDecoderFile().getDeveloperID());
        re.setManufacturerID(dev.getDecoderFile().getManufacturerID());
        re.setProductID(dev.getDecoderFile().getProductID());

        _roster.addEntry(re);
        updateDccAddress(dev);

        if (checkDuplicate(re)) {
            synchronized (this) {
                jmri.util.swing.JmriJOptionPane.showMessageDialog(
                        //progPane, 
                        parentComponent,
                        jmri.jmrit.symbolicprog.SymbolicProgBundle.getMessage(
                                "ErrorDuplicateID")); // NOI18N
            }
            return;
        }

        // Create new roster entry with file name derived from the entry's 
        // "ID" (i.e. the name entered by the user).


        // Get the decoder element and pass it to the RosterEntry creator
//    This is the old way.          RosterEntry re = new RosterEntry();
        // TODO: is this right???
        // RosterEntry re = new RosterEntry(dev.getDecoderFile().getModelElement());
//        re.setId(rosterEntryName);
//
//        log.warn("setting DCC address with {}",dev.getDestAddr());
//        re.setDecoderModel(dev.getDecoderFile().getModel());
//        re.setManufacturerID(Integer.toString(dev.getManufacturerID()));
//        re.setDeveloperID(Integer.toString(dev.getDeveloperID()));
//        re.setProductID(Integer.toString(dev.getProductID()));
//        re.setDecoderFamily(dev.getDecoderFile().getFamily());
//        re.setLongAddress(true);
//        re.ensureFilenameExists();

//        // TODO: pre-configure the roster entry's "long address"
//        // with this device's SV2 device address.
//        jmri.jmrit.symbolicprog.CvTableModel cvtm;
//        // CvTableModel(JLabel jl, Programmer pProgrammer);
//        cvtm = new jmri.jmrit.symbolicprog.CvTableModel(null, null);
//
//        re.loadCvModel(null, cvtm);
////            re.setDccAddress(Integer.toString(dev.getDestAddr()));
//        re.setDccAddress("" + Integer.toString(dev.getDestAddr()));
//        re.setProtocol(LocoAddress.Protocol.DCC_LONG);
//
//        // TODO: just a temporary trial
////            cvtm.setValueAt("43", 0,1);
//
//        // cvtm.getCvToVariableMapping(rosterEntryName);
//
        
        // if there isn't a filename, store using the id
        re.ensureFilenameExists();
        String filename = re.getFileName();

        // create the RosterEntry to its file
        log.debug("setting DCC address {} {}", dev.getDestAddr(), 0);
        JLabel statusLabel = new JLabel("");

        synchronized (this) {
            re.setDccAddress("" + dev.getDestAddr());  // NOI18N
            re.setLongAddress(true);
            jmri.jmrit.Programmer mProgrammer = new jmri.Programmer();
            cvModel = new CvTableModel(statusLabel, mProgrammer);
            variableModel = new VariableTableModel(statusLabel, 
                    new String[]{"Name", "Value"}, cvModel);
            mProgrammer.s
            variableModel.setValueAt(re, row, row);
            mProgrammer.setVariableValue("Long Address", longAddress); // NOI18N

            re.writeFile(cvModel, variableModel);

            // mark this as a success
            variableModel.setFileDirty(false);
        }
        // and store an updated roster file
        jmri.util.FileUtil.createDirectory(
                jmri.util.FileUtil.getUserFilesPath());
        Roster.getDefault().writeRoster();

//        re.writeFile(null, null);

        /*
        // This is the code from the roster:
        log.warn("saveRosterEntry entered");
        if (rosterIdField.getText().equals(SymbolicProgBundle.getMessage("LabelNewDecoder"))) { // NOI18N
            synchronized (this) {
                JmriJOptionPane.showMessageDialog(progPane, SymbolicProgBundle.
                        getMessage("PromptFillInID")); // NOI18N
            }
            throw new JmriException("No Roster ID"); // NOI18N
        }
        if (checkDuplicate()) {
            synchronized (this) {
                JmriJOptionPane.showMessageDialog(progPane, SymbolicProgBundle.getMessage("ErrorDuplicateID")); // NOI18N
            }
            throw new JmriException("Duplicate ID"); // NOI18N
        }

        if (re == null) {
            log.debug("re null, creating RosterEntry");
            re = new RosterEntry();
            re.setDecoderFamily(decoderFile.getFamily());
            re.setDecoderModel(decoderFile.getModel());
            re.setId(rosterIdField.getText());
            re.setDeveloperID(decoderFile.getDeveloperID());
            re.setManufacturerID(decoderFile.getManufacturerID());
            re.setProductID(decoderFile.getProductID());
            Roster.getDefault().addEntry(re);
        }

        updateDccAddress();

        // if there isn't a filename, store using the id
        re.ensureFilenameExists();
        String filename = re.getFileName();

        // create the RosterEntry to its file
        log.debug("setting DCC address {} {}", address, shortAddr);
        synchronized (this) {
            re.setDccAddress("" + address);  // NOI18N
            re.setLongAddress(!shortAddr);
            re.writeFile(cvModel, variableModel);

            // mark this as a success
            variableModel.setFileDirty(false);
        }
        // and store an updated roster file
        FileUtil.createDirectory(FileUtil.getUserFilesPath());
        Roster.getDefault().writeRoster();

        // show OK status
        statusLabel.setText(MessageFormat.format(
                SymbolicProgBundle.getMessage("StateSaveOK"), // NOI18N
                filename));
        log.warn("saveRosterEntry completed!");
        }

    void updateDccAddress() {
        log.warn("updateDccAddress entry.");
        // wrapped in isDebugEnabled test to prevent overhead of assembling message
        if (log.isDebugEnabled()) {
            log.debug("updateDccAddress: short {} long {} mode {}",
                    (primaryAddr == null ? "<null>" : primaryAddr.getValueString()),
                    (extendAddr == null ? "<null>" : extendAddr.getValueString()),
                    (addMode == null ? "<null>" : addMode.getValueString()));
        }
        new DccAddressVarHandler(primaryAddr, extendAddr, addMode) {
            @Override
            protected void doPrimary() {
                longMode = false;
                if (primaryAddr != null && !primaryAddr.getValueString().equals("")) {
                    newAddr = primaryAddr.getValueString();
                }
            }

            @Override
            protected void doExtended() {
                // long address
                if (!extendAddr.getValueString().equals("")) {
                    longMode = true;
                    newAddr = extendAddr.getValueString();
                }
            }
        };
        // update if needed
        if (newAddr != null) {
            synchronized (this) {
                // store DCC address, type
                address = newAddr;
                shortAddr = !longMode;
            }
        }
        log.warn("updateDccAddress completion!");
    }


    
        */





        log.debug("Created a valid roster entry called '{}' for decoder file "
                + "'{}'", re.getId(), dev.getDecoderFile().getFileName() );

        dev.setRosterEntry(re);
        this.fireTableRowsUpdated(row, row);
    }

    /**
     *
     * @return true if the value in the id JTextField is a duplicate of some
     *         other RosterEntry in the roster
     * 
     */
    private boolean checkDuplicate(RosterEntry re) {
        // check its not a duplicate
        List<RosterEntry> l = Roster.getDefault().
                matchingList(null, null, null, null, null, null, 
                        re.getId());
        boolean found = false;
        for (RosterEntry rosterEntry : l) {
            if (re != rosterEntry) {
                found = true;
                break;
            }
        }
        return found;
    }

    void updateDccAddress(Sv2Device dev) {
        boolean longMode;
        String newAddr;
        log.warn("updateDccAddress entry.: {}",dev.getDestAddr());
        // wrapped in isDebugEnabled test to prevent overhead of assembling message
        if (log.isDebugEnabled()) {
            log.debug("updateDccAddress: short {} long {} mode {}",
                    "<null>" , dev.getDestAddr() );
        }
        newAddr = Integer.toString(dev.getDestAddr());
        longMode = true;
        // update if needed
        synchronized (this) {
            // store DCC address, type
            address = newAddr;
            shortAddr = !longMode;
        }
        log.warn("updateDccAddress completion!");
    }
    public void dispose() {
        if ((memo != null) && (memo.getSv2DevicesManager() != null)) {
            memo.getSv2DevicesManager().removePropertyChangeListener(this);
        }
    }

    private final static Logger log = LoggerFactory.getLogger(Sv2DeviceDataModel.class);
}
