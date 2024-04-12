package jmri.jmrix.loconet.lnsvf2;
import jmri.jmrit.roster.RosterEntry;
import jmri.jmrit.decoderdefn.DecoderFile;

/**
 * A class to hold LocoNet SV Format 2 device identity information.
 * 
 * Tracks a LocoNet SV Format 2 device's destination address, manufacturer ID, 
 * device ID, product ID, serial number, etc. 
 * 
 * Can track an associated JMRI decoder definition, and can track an associated 
 * JMRI Roster entry.
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
 * @author B. Milhaupt
 */
    public class Sv2Device {
        private int mfgID;
        private int developerID;
        private int productID;
        private int destinationAddr;
        private int serNum;
        private String deviceName;
        private String rosterName;
        private int swVersion;
        private RosterEntry rosterEntry;
        private DecoderFile decoderFile;

        public Sv2Device(int mfgID, int developerID, int productID, int destAddr, int serNum,
                String deviceName, String rosterName, int swVersion) {
            this.mfgID = mfgID;
            this.developerID = developerID;
            this.productID = productID;
            this.destinationAddr = destAddr;
            this.serNum = serNum;
            this.deviceName = deviceName;
            this.rosterName = rosterName;
            this.swVersion = swVersion;
        }
        public int getManufacturerID() {return mfgID;}
        public int getDeveloperID() {return developerID;}
        public int getProductID() {return productID;}
        public int getDestAddr() {return destinationAddr;}
        public String getDeviceName() {return deviceName;}
        public String getRosterName() {return rosterName;}
        public int getSerialNum() {return serNum;}
        public int getSwVersion() {return swVersion;}

        /**
         * Set the table view of the device's destination address.
         *
         * This routine does _not_ program the device's destination address!
         * @param destAddr device destination address
         */
        public void setDestAddr(int destAddr) {this.destinationAddr = destAddr;}
        public void setDevName(String s) {deviceName = s;}
        public void setRosterName(String s) {rosterName = s;}
        public void setSwVersion(int version) {swVersion = version;}
        public DecoderFile getDecoderFile() {
            return decoderFile;
        }
        public void setDecoderFile(DecoderFile f) {
            decoderFile = f;
        }
        public RosterEntry getRosterEntry() {
            return rosterEntry;
        }
        public void setRosterEntry(RosterEntry e) {
            rosterEntry = e;
            setRosterName(e.getId());
        }
    }
