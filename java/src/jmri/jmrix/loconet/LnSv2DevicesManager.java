package jmri.jmrix.loconet;

import java.beans.PropertyChangeListener;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;

import jmri.Programmer;
import jmri.ProgrammingMode;
import jmri.beans.PropertyChangeSupport;
import jmri.jmrit.decoderdefn.DecoderFile;
import jmri.jmrit.roster.Roster;
import jmri.jmrit.roster.RosterEntry;
import jmri.jmrit.symbolicprog.tabbedframe.PaneOpsProgFrame;
import jmri.jmrix.loconet.lnsvf2.LnSv2MessageContents;
import jmri.jmrix.loconet.lnsvf2.Sv2Device;
import jmri.jmrix.loconet.lnsvf2.Sv2Devices;
import jmri.jmrix.loconet.lnsvf2.Sv2ProgrammingTool;
import jmri.managers.DefaultProgrammerManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LocoNet SV2 Devices Manager
 *
 * A centralized resource to help identify LocoNet "System Variable Format 2"
 * (SVF2) devices and "manage" them.
 *
 * Supports the following features:
 *  - SVF2 "discovery" process
 *  - SVF2 Device "destination address" change
 *  - SVF2 Device "reconfigure/reset"
 *  - identification of devices with conflicting "destination address"es
 *  - identification of a matching JMRI "decoder definition" for each discovered
 *    device, if an appropriate definition exists
 *  - identification of matching JMRI "roster entry" which matches each
 *    discovered device, if an appropriate roster entry exists
 *  - ability to open a symbolic programmer for a given discovered device, if
 *    an appropriate roster entry exists
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
 *
 */

public class LnSv2DevicesManager extends PropertyChangeSupport
        implements LocoNetListener {
    private LocoNetSystemConnectionMemo memo;
    private volatile Sv2Devices sv2Devices;
    private List<Sv2ReadAddrTime> readSv2AddressList;
    private java.util.TimerTask delayTask = null;
    private volatile boolean waitingForDiscoveryReplies;

    public LnSv2DevicesManager(LocoNetSystemConnectionMemo memo) {
        this.memo = memo;
        if (memo.getLnTrafficController() == null) {
            log.error("No LocoNet connection available, this tool cannot function"); // NOI18N
        }
        sv2Devices = new Sv2Devices();
        readSv2AddressList = new ArrayList<Sv2ReadAddrTime>();
        waitingForDiscoveryReplies = false;
        memo.getLnTrafficController().addLocoNetListener(~0, this);

    }

    public Sv2Devices getDeviceList() {
        return sv2Devices;
    }

    public void clearDevicesList() {
        if (sv2Devices.size() > 0) {
            // cancel reading sv#2 values from all devices,
            // since devices are no longer defined
            waitingForDiscoveryReplies = false;
            readSv2AddressList.clear();
            sv2Devices.removeAllDevices();
        }


        jmri.util.ThreadingUtil.runOnLayoutEventually( ()->{
        firePropertyChange("DeviceListChanged", true, false);
            });
    }

    public void sendSv2DiscoveryRequest() {
        memo.getLnTrafficController().sendLocoNetMessage(
                LnSv2MessageContents.createSvDiscoverQueryMessage());
    }

    public void message(LocoNetMessage m) {
        if (LnSv2MessageContents.isLnMessageASpecificSv2Command(m,
                LnSv2MessageContents.Sv2Command.SV2_DISCOVER_DEVICE_REPORT)) {
            // deal with a discovery reply!
            LnSv2MessageContents sv2c = new LnSv2MessageContents(m);

            int mfgId = sv2c.getSv2ManufacturerID();
            int prodId = sv2c.getSv2ProductID();
            int devId = sv2c.getSv2DeveloperID();
            int serNum = sv2c.getSv2SerialNum();
            int addr = sv2c.getDestAddr();

            sv2Devices.addDevice(new Sv2Device(mfgId, devId, prodId, addr, serNum, "", "", -1));
            log.debug("new sv2device added");
            firePropertyChange("DeviceListChanged", true, false);
            waitingForDiscoveryReplies = true;
            if (delayTask == null) {
                delayTask = new java.util.TimerTask() {
                    @Override
                    public void run() {
                        log.debug("triggered delay task {}, {}",waitingForDiscoveryReplies, readSv2AddressList.size());
                        if (!waitingForDiscoveryReplies) {
                            int sz;
                            if ((sz = readSv2AddressList.size()) > 0) {
                                if (readSv2AddressList.get(sz-1).timeSinceAdded() > 500L)
                                    // at least 500 mSec has elapsed since last discovery report
                                    querySv2Values();
                            }
                        } else {
                            waitingForDiscoveryReplies = false;
                        }
                    }};
                jmri.util.TimerUtil.scheduleAtFixedRateOnLayoutThread(delayTask, 200, 200);
            } else {
                delayTask.cancel();
                jmri.util.TimerUtil.scheduleAtFixedRateOnLayoutThread(delayTask, 500, 500);
            }
            if (addr > 0) {
                readSv2AddressList.add(new Sv2ReadAddrTime(addr));
            }
        } else if (LnSv2MessageContents.isSupportedSv2Message(m)) {
            LnSv2MessageContents svMsg = new LnSv2MessageContents(m);
            switch (LnSv2MessageContents.extractMessageType(m)) {
                case SV2_REPORT_ONE:
                    if (svMsg.getSVNum() == 2) {
                        int addr = svMsg.getDestAddr();
                        int val = svMsg.getSv2D1();
                         log.debug("SVF2 read one reply: device address {} read of SV 2 returns {}", addr, val);
                       // Annotate the discovered device SV2 data based on address
                        int count = sv2Devices.size();
                        for (int i = 0; i < count; ++ i) {
                            Sv2Device d = sv2Devices.getDevice(i);
                            if (d.getDestAddr() == addr) {
                                d.setSwVersion(val);

                                // need to find a corresponding roster entry?
                                if(d.getRosterName().length() == 0) {
                                    // Yes. Try to find a roster entry which matches the device characteristics
                                    List<RosterEntry> l = Roster.getDefault().matchingList(null,
                                        null,
                                        Integer.toString(d.getDestAddr()),
                                        null,
                                        null,
                                        null,
                                        null,
                                        Integer.toString(d.getDeveloperID()),
                                        Integer.toString(d.getManufacturerID()),
                                        Integer.toString(d.getProductID()));
                                    if (l.size() == 0) {
                                        log.debug("Did not find a corresponding roster entry");
                                    } else if (l.size() == 1) {
                                        log.debug("Found a matching roster entries.");
                                        d.setRosterEntry(l.get(0));
                                    } else {
                                        log.info("Found multiple matching roster entries. "
                                                + "Cannot associate any one to this device.");
                                    }
                                }
                                // notify listeners of pertinent change to device
                                firePropertyChange("DeviceListChanged", true, false);
                            }
                        }
                    }
                    break;
                case SV2_CHANGE_DEVICE_ADDRESS_REPLY:
                    int destAddr = svMsg.getDestAddr();
                    int serNum = svMsg.getSv2SerialNum();
                    int mfgId = svMsg.getSv2ManufacturerID();
                    int develId = svMsg.getSv2DeveloperID();
                    int prodId = svMsg.getSv2ProductID();
                    log.debug("got chg addr reply for mfg {}, prod {}, devel {}, serNum {}, destAddr {}",
                            mfgId, prodId, develId, serNum, destAddr);
                    Sv2Device foundDevice = new Sv2Device(mfgId, develId,
                            prodId, -1, serNum, null, null, -1);

                    int index = sv2Devices.isDeviceExistant(foundDevice);
                    if (index >=0) {
                        log.debug("found device {}, setting destAddr {}", index, destAddr);
                        sv2Devices.getDevice(index).setDestAddr(destAddr);
                        firePropertyChange("DeviceListChanged", true, false);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void querySv2Values() {
        if (readSv2AddressList.size() > 0) {
            int addr = readSv2AddressList.get(0).destAddr;
            readSv2AddressList.remove(0);
            memo.getLnTrafficController().sendLocoNetMessage(
                    LnSv2MessageContents.createSvReadRequest(addr,2));
        }
    }

    public void reconfigResetDevice(Sv2Device dev) {
        memo.getLnTrafficController().sendLocoNetMessage(
                LnSv2MessageContents.createSv2Message(
                        0,
                        LnSv2MessageContents.Sv2Command.SV2_RECONFIGURE_DEVICE.getCmd(),
                        dev.getDestAddr(),
                        0, 0, 0, 0, 0));
    }
    public void reprogramDeviceAddress(Sv2Device dev, int addr) {
        log.warn("reprogramDeviceAddress");
        int mfg = dev.getManufacturerID();
        int developer = dev.getDeveloperID();
        int prod = dev.getProductID();
        int serNum = dev.getSerialNum();
        memo.getLnTrafficController().sendLocoNetMessage(
                LnSv2MessageContents.createChangeAddressMessage(
            mfg, prod, developer, serNum, addr));

    }

    public ProgrammingResult prepareForSymbolicProgrammer(Sv2Device dev, Sv2ProgrammingTool t) {

        if (sv2Devices.isDeviceExistant(dev) < 0) {
            return ProgrammingResult.FAIL_NO_SUCH_DEVICE;
        }
        int destAddr = dev.getDestAddr();
        if (destAddr == 0) {
            return ProgrammingResult.FAIL_DESTINATION_ADDRESS_IS_ZERO;
        }
        int deviceCount = 0;
        for (Sv2Device d: sv2Devices.getDevices()) {
            if (destAddr == d.getDestAddr()) {
                deviceCount++;
            }
        }
        if (deviceCount > 1) {
            return ProgrammingResult.FAIL_MULTIPLE_DEVICES_SAME_DESTINATION_ADDRESS;
        }

        if ((dev.getRosterName()==null) || (dev.getRosterName().length()==0)) {
            return ProgrammingResult.FAIL_NO_MATCHING_ROSTER_ENTRY;
        }

        DefaultProgrammerManager pm = memo.getProgrammerManager();
        if (pm == null) {
            return ProgrammingResult.FAIL_NO_APPROPRIATE_PROGRAMMER;
        }
        Programmer p = pm.getAddressedProgrammer(false, dev.getDestAddr());
        if (p == null) {
            return ProgrammingResult.FAIL_NO_ADDRESSED_PROGRAMMER;
        }

        if (!p.getSupportedModes().contains(LnProgrammerManager.LOCONETSV2MODE)) {
            return ProgrammingResult.FAIL_NO_LNSV_PROGRAMMER;
        }
        p.setMode(LnProgrammerManager.LOCONETSV2MODE);
        ProgrammingMode prgMode = p.getMode();
        if (!prgMode.equals(LnProgrammerManager.LOCONETSV2MODE)) {
            return ProgrammingResult.FAIL_NO_LNSV_PROGRAMMER;
        }

        RosterEntry re = Roster.getDefault().entryFromTitle(dev.getRosterName());
        String name = re.getId();

        t.openPaneOpsProgFrame(re, name, "programmers/Comprehensive.xml", p); // NOI18N
        return ProgrammingResult.SUCCESS_PROGRAMMER_OPENED;
    }

    public enum ProgrammingResult {
        SUCCESS_PROGRAMMER_OPENED,
        FAIL_NO_SUCH_DEVICE,
        FAIL_NO_APPROPRIATE_PROGRAMMER,
        FAIL_NO_MATCHING_ROSTER_ENTRY,
        FAIL_DESTINATION_ADDRESS_IS_ZERO,
        FAIL_MULTIPLE_DEVICES_SAME_DESTINATION_ADDRESS,
        FAIL_NO_ADDRESSED_PROGRAMMER,
        FAIL_NO_LNSV_PROGRAMMER
    }

    public void dispose() {
        if(readSv2AddressList != null) {
            readSv2AddressList.clear();
        }

        if (delayTask != null) {
            delayTask.cancel();
        }
        PropertyChangeListener[] pcl = getPropertyChangeListeners();
        for (PropertyChangeListener l : pcl) {
            removePropertyChangeListener(l);
        }
    }

    private static class Sv2ReadAddrTime {
        int destAddr;
        Instant timeVal;
        public Sv2ReadAddrTime(int destAddr) {
            this.destAddr = destAddr;
            this.timeVal = Instant.now();
        }

        public long timeSinceAdded() {
            Duration d = Duration.between(timeVal, java.time.Instant.now());
            return d.toMillis();
        }
    }

    private final static Logger log = LoggerFactory.getLogger(LnSv2DevicesManager.class);
    }
