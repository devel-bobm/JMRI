package jmri.jmrix.loconet.accy7thgen;

import jmri.jmrix.loconet.LnConstants;

import javax.swing.JButton;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

/**
 * Accessory 7th Generation Device.
 * 
 * @author Bob Milhaupt
 */
public class Accy7thGenDevice extends java.beans.Beans {
    private String device;
    private int serNum;
    private int baseAddr;
    private int  firstOpSws;
    private Integer turnoutAddrStart;
    private Integer turnoutAddrEnd;
    private Integer sensorAddrStart;
    private Integer sensorAddrEnd;
    private Integer reportersAddrStart;
    private Integer reportersAddrEnd;
    private Integer aspectAddrStart;
    private Integer aspectAddrEnd;
    private Integer powerAddrStart;
    public JButton action;
    
    /**
     * Constructor
     * @param device - integer device type (0-7f)
     * @param serNum - Integer serial number (0-0xFFFF or 0-0x3FFF)
     * @param baseAddr - Integer base address (0-2045)
     * @param firstOps - Integer first Ops (0-255)
     */
    public Accy7thGenDevice(int device, int serNum, int baseAddr, int firstOps) {
        setDevice(device);
        this.serNum = serNum;
        this.baseAddr = baseAddr;
        this.firstOpSws = firstOps;
        turnoutAddrStart = gitTurnoutAddressStart();
        turnoutAddrEnd = gitTurnoutAddressEnd();
        sensorAddrStart = gitSensorAddressStart();
        sensorAddrEnd = gitSensorAddressEnd();
        reportersAddrStart = gitReportersStart();
        reportersAddrEnd = gitReportersEnd();
        aspectAddrStart = gitAspectStart();
        aspectAddrEnd = gitAspectEnd();
        powerAddrStart = gitPowerStart();
        action = new JButton("Change Base Addr");
        action.setActionCommand("changeAddr");

    } 
               
    public JButton getAction() {
        return action;
    }

    public void setAction(JButton action) {
        this.action = action;
    }

    public String getDevice() {
        return device;
    }

    public final void setDevice(int device) {
        String s = LnConstants.IPL_NAME(device);
        switch (s) {
            case "SE74":
            case "PM74":
            case "DS78V":
            case "DS74":
                break;
            default:
                s = "Unknown - "+Integer.toString(device);
                            }
        this.device = s;
    }

    public void setDevice(String s) {
        String dev = s;
        switch (dev) {
            case "SE74":
            case "PM74":
            case "DS78V":
            case "DS74":
                break;
            default:
                dev = "Unknown - "+s;
        }
        this.device = dev;
    }

    public int getSerNum() {
        return serNum;
    }

    public void setSerNum(int serNum) {
        this.serNum = serNum;
    }

    public int getBaseAddr() {
        return baseAddr;
    }

    public void setBaseAddr(int baseAddr) {
        this.baseAddr = baseAddr;
    }

    public String gitFirstOpSws() {
        return "0x"+Integer.toHexString(firstOpSws);
    }

    public void satFirstOpSws(int firstOps) {
        this.firstOpSws = firstOps;
    }
    
    private Integer gitTurnoutAddressStart() {
        // TODO: needs to be updated to handle the end-of-turnouts addresss.
        
        if (baseAddr < 1 || baseAddr > 2048) {
            return -1;
        }
        
        switch (device) {
            case "DS74":
            case "DS78V":
            case "SE74":
                return baseAddr;
            default:
                return -1;
        }
    }

    private Integer gitTurnoutAddressEnd() {
        int ret;
        // TODO: needs to be updated to handle the end-of-turnouts addresss.
        switch (device) {
            case "DS74":
                if ((firstOpSws & 0x1e) == 0xa) {
                    // Lights mode
                    ret = baseAddr+7;
                } else {
                    ret = baseAddr + 3;
                }
                break;
            case "DS78V":
                if ((firstOpSws & 0x1e) == 0x0C) {
                    // four position mode
                    ret = baseAddr + 15;
                } else {
                    ret = baseAddr+7;
                }
                break;
            case "SE74":
                int i = baseAddr+35;
                if ((firstOpSws & 0x20) == 0x20) {
                    ret = baseAddr+3;
                } else {
                    ret = i;
                }
                break;
            default:
                ret = -1;
        }
        
        if (ret > 2048) {
            ret = 2048;
        }
        return ret;
    }
    
    private Integer gitSensorAddressStart() {
        // TODO: needs to be updated to handle the end-of-sensors addresss.
        switch (device) {
            case "DS78V":
            case "DS74":
            case "SE74":
            case "PM74":
                return (2 * baseAddr) - 1;
            default:
                return -1;
        }
    }

    private Integer gitSensorAddressEnd() {
        int ret;
        switch (device) {
            case "DS78V":
                if ((firstOpSws & 0x1e) == 0x0C) {
                    ret =  (2 * baseAddr) + 30;
                } else {
                    ret = (2 * baseAddr)+14;
                }
                break;
            case "SE74":
            case "DS74":
                ret = (2 * baseAddr) + 6;
                break;
            case "PM74":
                ret = ((2 * (baseAddr -1)) + 1) + 7;
                break;
            default:
                ret = -1;
                break;
        }
        if (ret > 4096) {
            ret = 4096;
        }
        return ret;
    }

    private Integer gitReportersStart() {
        // TODO: needs to be updated to handle the end-of-reporters addresss.
        switch (device) {
            case "PM74":
                int i = baseAddr - 1;
                int j = baseAddr;
                int k = baseAddr + 1;
                int l = baseAddr + 2;
                int st = i;
                if ((st & 128) == 0) {
                    return (st % 128) + 1;
                }
                st = j;
                if ((st & 128) == 0) {
                    return (st % 128) + 1;
                }
                st = k;
                if ((st & 128) == 0) {
                    return (st % 128) + 1;
                }
                st = l;
                if ((st & 128) == 0) {
                    return (st % 128) + 1;
                }
                return -1;
            default:
                return -1;
        }
    }

    private Integer gitReportersEnd() {
        // TODO: needs to be updated to handle the end-of-reporters addresss.
        switch (device) {
            case "PM74":
                int i = baseAddr - 1;
                int j = baseAddr;
                int k = baseAddr + 1;
                int l = baseAddr + 2;
                int st = l;
                if ((st & 128) == 0) {
                    return (st % 128) + 1;
                }
                st = k;
                if ((st & 128) == 0) {
                    return (st % 128) + 1;
                }
                st = j;
                if ((st & 128) == 0) {
                    return (st % 128) + 1;
                }
                st = i;
                if ((st & 128) == 0) {
                    return (st % 128) + 1;
                }
                return -1;
            default:
                return -1;
        }
    }

    private Integer gitAspectStart() {
        // TODO: needs to be updated to handle the end-of-aspects addresss.
        switch (device) {
            case "SE74":
                if ((firstOpSws & 0x20) == 0x20) {
                    return baseAddr;
                } else {
                    return 0;
                }
            default:
                return -1;
        }
    }

    private Integer gitAspectEnd() {
        // TODO: needs to be updated to handle the end-of-aspects addresss.
        switch (device) {
            case "SE74":
                int i = 0;
                if ((firstOpSws & 0x20) == 0x20) {
                    i = baseAddr+15;
                }
                if (i > 2048) {
                    i = 2048;
                }
                return i;
            default:
                return -1;
        }
    }

    private Integer gitPowerStart() {
        // TODO: needs to be updated to handle the end-of-powers addresss.
        switch (device) {
            case "PM74":
                int i = ((baseAddr - 1) & 0xFF) + 1;
                return i;
            default:
                return -1;
        }
    }

    public boolean gitTurnoutsBroadcast() {
        switch (device) {
            case "SE74":
                return true;
            default:
                return false;
        }
    }

    public String getTurnouts() {
        if (turnoutAddrStart > 0) {
            return Integer.toString(turnoutAddrStart)+"-"+
                    Integer.toString(turnoutAddrEnd);
            
        } else {
            return "";
        }
    }
    
    public String getSensors() {
        if (sensorAddrStart > 0) {
            return Integer.toString(sensorAddrStart)+"-"+Integer.toString(sensorAddrEnd);
        } else {
            return "";
        }
    }
    
    public String getReporters() {
        String res ;
        if ((reportersAddrStart > 0) && (reportersAddrEnd > 0)) {
            res = Integer.toString(reportersAddrStart)+"-"+Integer.toString(reportersAddrEnd);
        } else if ((reportersAddrStart > 0) && (reportersAddrEnd <= 0)) {
            res = Integer.toString(reportersAddrStart);
        } else if ((reportersAddrStart <= 0) && (reportersAddrEnd > 0)) {
            res = Integer.toString(reportersAddrEnd);
        } else {
            res = "";
        }
        return res;
    }
    
    public boolean gitAspectsBroadcast() {
        switch (device) {
            case "SE74":
                return (aspectAddrStart > 0) ? true: false;
            default:
                return false;
        }
    }

    public String getAspects() {
        if (aspectAddrStart > 0) {
            return Integer.toString(aspectAddrStart)+"-"+Integer.toString(aspectAddrEnd);
        } else {
            return "";
        }
    }
    
    public String getPowers() {
        if (powerAddrStart >= 0) {
            // only one power address (for 4 sub-addresses) for PM74
            return Integer.toString(powerAddrStart);
        } else {
            return "";
        }
    }   
    
    // initialize logging
    // private static final Logger log = LoggerFactory.getLogger(Accy7thGenDevice.class);

}
