/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jmri.jmrix.loconet.lnsvf2;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for managing a collection of LocoNet SV Format 2 Devices.
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
    public class Sv2Devices {
        public Sv2Devices() {
            deviceList = new ArrayList<Sv2Device>();
        }

        private List<Sv2Device> deviceList;
        public void addDevice(Sv2Device d) {
            if (!deviceExists(d)) {
                deviceList.add(d);
                log.debug("added device with mfg {}, prod {}, dev {}, addr {}",
                        d.getManufacturerID(), d.getProductID(), d.getDeveloperID(), d.getDestAddr());
            } else {
                log.debug("device already in list: mfg {}, prod {}, dev {}, addr {}",
                        d.getManufacturerID(), d.getProductID(), d.getDeveloperID(), d.getDestAddr());
            }
        }

        public void removeAllDevices() {
            deviceList.clear();
        }

        /**
         * Returns index of device with matching Mfg, Prod, Devel, Ser Num and
         * Device Address
         *
         * Where a deviceToBeFound parameter is -1, that parameter is not compared.
         *
         * @param deviceToBeFound Device to try to find in known SV2 devices list
         * @return index of found device, else -1 if matching device not found
         */
        public int isDeviceExistant(Sv2Device deviceToBeFound) {
            log.debug("Looking for a known SV2 device which matches characteristics:"+
                    " mfgId {}, prodId {}, develId {}, addr {}, serNum {}.",
                    deviceToBeFound.getManufacturerID(), deviceToBeFound.getProductID(),
                    deviceToBeFound.getDeveloperID(), deviceToBeFound.getDestAddr(),
                    deviceToBeFound.getSerialNum());
            for (int i = 0; i < deviceList.size(); ++i) {
                Sv2Device dev = deviceList.get(i);
                log.trace("Comparing against known device:"+
                        " mfgId {}, prodId {}, develId {}, addr {}, serNum {}.",
                        dev.getManufacturerID(), dev.getProductID(),
                        dev.getDeveloperID(), dev.getDestAddr(),
                        dev.getSerialNum());
                if ((deviceToBeFound.getManufacturerID() == -1) ||
                        (dev.getManufacturerID() == deviceToBeFound.getManufacturerID())) {
                    if ((deviceToBeFound.getProductID() == -1) ||
                            (dev.getProductID() == deviceToBeFound.getProductID())) {
                        if ((deviceToBeFound.getDeveloperID() == -1) ||
                                (dev.getDeveloperID() == deviceToBeFound.getDeveloperID())) {
                            if ((deviceToBeFound.getSerialNum() == -1) ||
                                    (dev.getSerialNum() == deviceToBeFound.getSerialNum())) {
                                if ((deviceToBeFound.getDestAddr() == -1) ||
                                        (dev.getDestAddr() == deviceToBeFound.getDestAddr())) {
                log.debug("Match Found! Searched device matched against known device:"+
                        " mfgId {}, prodId {}, develId {}, addr {}, serNum {}.",
                        dev.getManufacturerID(), dev.getProductID(),
                        dev.getDeveloperID(), dev.getDestAddr(),
                        dev.getSerialNum());
                                    return i;
                                }
                            }
                        }
                    }
                }
            }
            log.debug("No matching known device was found!");
            return -1;
        }

        public boolean deviceExists(Sv2Device d) {
            int i = isDeviceExistant(d);
            return (i >= 0);
        }

        public Sv2Device getDevice(int index) {
            return deviceList.get(index);
        }

        public Sv2Device[] getDevices() {
            Sv2Device[] d = {};
            return deviceList.toArray(d);
        }
        public int size() {
            return deviceList.size();
        }

    private final static Logger log = LoggerFactory.getLogger(Sv2Devices.class);
    }
