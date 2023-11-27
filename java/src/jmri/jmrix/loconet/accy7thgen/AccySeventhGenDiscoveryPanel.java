package jmri.jmrix.loconet.accy7thgen;

import java.awt.*;
import java.awt.event.ActionEvent;

import java.util.TimerTask;

import javax.swing.*;
import javax.swing.JButton;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.table.*;

import jmri.jmrix.loconet.*;
import jmri.jmrix.loconet.swing.LnPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovery and "change base address" tool for Digitrax 7th-Generation Accessory
 * decoders.
 *
 * This tool uses LocoNet operations to query the 7th-generation Accessory devices,
 * and their "base addresses", and displays a table showing each 7th-generation
 * Accessory device and each of its turnout, sensor, ... address usage (based on
 * the device's 'base address').
 *
 * When any 7th gen. accessory device "conflicts" with another 7th-gen. accessory
 * device, the tool will change the background to 'red'.
 *
 * Each entry includes a button which asks for a new "base address".  When a 7th-gen.
 * accessory device gets its base address changed using this tool, the tool will
 * re-query the LocoNet devices and update the table.  Conflicts will be updated,
 * if needed.
 */
public class AccySeventhGenDiscoveryPanel extends LnPanel implements LocoNetListener {
    JButton findButton;
    boolean gotTheFindMessage;
    boolean gotA7thGenReply;
    protected LnTrafficController tc;

    private BeanTableModel<Accy7thGenDevice> devicesModel;

    public ReorderableBeanTable devicesTable;
    private JScrollPane devicesScroll;

    String prefixTurnout;
    String prefixSensor;
    String prefixReporter;

    JButton button = new JButton();

    private int oldRowCount;
    private int requestCount;

    // set this boolean to true to cause this tool to auto-retry every second or so.
    private final boolean noFindRetry = false;
    private final int MORECOUNT = 2;

    public AccySeventhGenDiscoveryPanel() {
        super();
    }

    @Override
    public void initContext(Object context) {
        if (context instanceof LocoNetSystemConnectionMemo) {
            initComponents((LocoNetSystemConnectionMemo) context);
            if (devicesModel != null) {
                devicesModel.extraInit(((LocoNetSystemConnectionMemo) context).getLnTrafficController());
            } else {
                log.warn("Cannot initialize the traffic controller for LocoNet.");
            }
        }
    }

    @Override
    public void initComponents(final LocoNetSystemConnectionMemo memo) {
        super.initComponents(memo);
        tc = memo.getLnTrafficController();
        prefixTurnout = memo.getSystemPrefix()+"T";
        prefixSensor  = memo.getSystemPrefix()+"S";
        prefixReporter = memo.getSystemPrefix()+"R";
        tc = memo.getLnTrafficController();
        devicesModel.extraInit(tc) ;
        tc.addLocoNetListener(~0, this);
        oldRowCount = 0;
        requestCount = 0;

        find7thGenDevices();
    }

    @Override
    public void initComponents() {
        setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        findButton = new JButton(Bundle.getMessage("FINDLABEL"));
        add(findButton);
        devicesModel = new BeanTableModel<>(Accy7thGenDevice.class);

        devicesTable = new ReorderableBeanTable(devicesModel) {
            private final Color conflictingColor = Color.decode("#c00000");         // Dark Red
            private final Color cellsOrigBackColor = new JTable().getBackground();
            Color cellBackColor = cellsOrigBackColor;  // Original Cells Backgound color.
            private final Color selection = Color.decode("#7FB3D5");         // A lighter Blue

            private static final long serialVersionUID = 1L;
            // Here is where all the cell formatting is notYetBeenRun
            // based on 'Stack Overflow" web page  : java - JTable cell render based on content
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int rowIndex,
                    int columnIndex) {
                // Acquire the current cell component being Rendered. ALL cells get Rendered.
                JComponent component = (JComponent) super.prepareRenderer(renderer, rowIndex, columnIndex);

                boolean overlap = checkOverlappingResources(devicesTable, rowIndex, columnIndex);
                cellBackColor = (overlap)? conflictingColor: cellsOrigBackColor;

                component.setBackground(cellBackColor);
                component.setForeground(properTextColor(cellBackColor));

                // If a row is selected, maintain the conditional formatting...
                if (isRowSelected(rowIndex)) {
                    component.setBackground(selection);
                } else {
                    component.setBackground(cellBackColor);
                }
                return component;
            }
            @Override
            public Object getValueAt(int row, int column) {
                Object ret = super.getValueAt(row, column);
                return ret;
            }
            @Override
            public void setValueAt(Object o, int row, int column) {
                super.setValueAt(o, row, column);
            }
        };

         Action changeBaseAddrAction = new AbstractAction() {
             @Override
             public void actionPerformed(ActionEvent e) {
                 int modelRow = Integer.parseInt( e.getActionCommand() );

                 TableColumnModel model = devicesTable.getColumnModel();
                 int modelsDeviceColumn = model.getColumnIndex("Device");
                 int modelsSerNumColumn = model.getColumnIndex("Ser Num");

                 Object device =  devicesTable.getValueAt(modelRow,modelsDeviceColumn);
                 Object serNum = devicesTable.getValueAt(modelRow, modelsSerNumColumn);

                 String result;
                 result = JOptionPane.showInputDialog(
                         devicesTable,
                         "Enter the new 'Base Address', in decimal",
                         "New Base Address for "+device.toString()+ ", Serial Number "+serNum.toString() +".",
                          javax.swing.JOptionPane.QUESTION_MESSAGE);

                 int baseAddr = -1;
                 try {
                     baseAddr = Integer.parseInt(result);
                     if ((baseAddr <1) || (baseAddr > 2045)) {
                         baseAddr = -1;
                     }
                 } catch (java.lang.IllegalArgumentException e2) {
                     log.warn("wrong value!");
                 }

                 if ((baseAddr >=1) && (baseAddr <= 2045)) {
                     int devNum;
                     switch (device.toString()) {
                         case "DS74":
                             devNum = 0x74;
                             break;
                         case "DS78V":
                             devNum = 0x7c;
                             break;
                         case "PM74":
                             devNum = 74;
                             break;
                         case "SE74":
                             devNum = 70;
                             break;
                         default:
                             log.error("illegal device type {}", device.toString());
                             return;
                     }
                     int sn = Integer.parseInt(serNum.toString());
                     tc.sendLocoNetMessage(new LocoNetMessage(new int[] {
                         0xEE, 0x10, 0x02, 0x0f, 0,0, 0, 0, 0, devNum,
                         0, sn & 0x7f, (sn >> 7) & 0x7F, (baseAddr-1) & 0x7f,
                         ((baseAddr-1) >> 7) & 0x7f, 0}));
                     find7thGenDevices();
                 }
             }
         };

        TableColumnModel model = devicesTable.getColumnModel();

        ReorderableBeanTable.reorderColumns(devicesTable,
                "Device", "Ser Num", "Base Addr", "Turnouts", "Sensors",
                "Reporters", "Aspects", "Powers","Action","First Op Sws");
         int modelsActionColumn = model.getColumnIndex("Action");

        ButtonColumn buttonColumn = new ButtonColumn(devicesTable,
                changeBaseAddrAction, modelsActionColumn);

        devicesModel.setColumnClass(9, JButton.class);

        devicesTable.setName(this.getTitle());
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER );
        devicesTable.setDefaultRenderer(String.class, centerRenderer);

        TableCellRenderer tableRenderer;
        tableRenderer = devicesTable.getDefaultRenderer(JButton.class);
        devicesTable.setDefaultRenderer(JButton.class, new JTableButtonRenderer(tableRenderer));

        javax.swing.table.JTableHeader header = devicesTable.getTableHeader();
        header.setPreferredSize(new java.awt.Dimension(100,
            new javax.swing.JLabel("A").getPreferredSize().height * 3
            ));

        devicesScroll = new JScrollPane(devicesTable);
        add(devicesScroll);

        // install "read" button handler
        findButton.addActionListener((ActionEvent a) -> {
            find7thGenDevices();
        });
        gotTheFindMessage = false;
        gotA7thGenReply = false;
        add(new JSeparator());
        add(new JLabel(Bundle.getMessage("FOOTNOTEASE74")));
        add(new JLabel(Bundle.getMessage("FOOTNOTEBSE74")));
        add(new JLabel(Bundle.getMessage("FOOTNOTEPM74")));
        add(new JLabel(Bundle.getMessage("FOOTNOTEGENERAL")));

        sendWarningDialogBox();
     }

    private void sendWarningDialogBox() {
        JOptionPane.showMessageDialog(this.getParent(),
                Bundle.getMessage("INFONOTICETEXT"),
                "Informational Notice",
                JOptionPane.INFORMATION_MESSAGE);
    }

    static class JTableButtonRenderer implements TableCellRenderer {
        private TableCellRenderer defaultRenderer;
        public JTableButtonRenderer(TableCellRenderer renderer) {
            defaultRenderer = renderer;
        }
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            if (value instanceof Component) {
                return (Component)value;
            }
            return defaultRenderer.getTableCellRendererComponent(table, value,
                    isSelected, hasFocus, row, column);
        }
    }

    public void find7thGenDevices() {
        findButton.setEnabled(false);
        int rows = devicesModel.getRowCount();
        if (rows > 0) {
            devicesModel.removeRowRange(0, devicesModel.getRowCount()-1);
            devicesModel.fireTableRowsDeleted(0, rows);
        }
        this.repaint();
        jmri.util.ThreadingUtil.runOnLayoutEventually(() -> get7thGenDevices());
        delayFromRequest();
    }

    public void get7thGenDevices() {
        if (tc != null) {
            requestCount++;
            //log.warn("request #{}",requestCount);
            tc.sendLocoNetMessage(msgRoutesQuery());
            startRoutesResponseTimer();
        }
    }

    public boolean checkOverlappingResources(JTable table, Integer rowIndex, Integer columnIndex) {
                boolean overlap = false;
                if ((columnIndex == 3)  ||
                        (columnIndex == 4)  ||
                        (columnIndex == 5)  ||
                        (columnIndex == 6)  ||
                        (columnIndex == 7) ) {

                    String value = table.getValueAt(rowIndex, columnIndex).toString();
                    String value2 = value.replace(" **", "");
                    value2 = value2.replace(" *", "");
                    int val1;
                    int val2;
                    if (value2.contains("-")) {
                        int where = value2.indexOf('-');
                        val1 = Integer.parseInt(value2.substring(0, where));
                        val2 = Integer.parseInt(value2.substring(where + 1));
                    } else if (!value2.isEmpty()) {
                        val1 = Integer.parseInt(value2);
                        val2 = val1;
                    } else {
                        val1 = -2;
                        val2 = -2;
                    }

                    if ((columnIndex == 3) &&
                            ((val2 >= 1021) && (val1 <= 1025))) {
                        overlap = true;
                    }
                    if ((!overlap) && (columnIndex == 3) &&
                            ((val2 >= 2041) && (val1 <= 2044))) {
                        overlap = true;
                    }
                    if ((!overlap) && (columnIndex == 6) &&
                            ((val2 >= 2048) && (val1 <= 2048))) {
                        overlap = true;
                    }

                    for (int i = 0; (i < table.getRowCount()) && (!overlap); i++) {
                        if (i == rowIndex) {
                        } else {
                            // get row's count and see if overlap
                            String ref = table.getValueAt(i, columnIndex).toString();
                            String ref2 = ref.replace(" **", "");
                            ref2 = ref2.replace(" *", "");

                            int refval1 = -1;
                            int refval2 = -1;
                            if (ref2.contains("-")) {
                                int where = ref2.indexOf('-');
                                refval1 = Integer.parseInt(ref2.substring(0, where));
                                refval2 = Integer.parseInt(ref2.substring(where + 1));
                            } else if (!ref2.isEmpty()) {
                                refval1 = Integer.parseInt(ref2);
                                refval2 = val2;
                                refval2 = refval1;
                            } else {
                                // ??? getOut of here???
                                refval1 = -1;
                                refval2 = -1;
                            }
                            // 1. check if val2 < refval1.
                            if ((val2 >= refval1) && (val1 <= refval2) ) {
                                overlap = true;
                            }

                            if (columnIndex == 3) {
                                if ((overlap == false) && (value.contains(" *"))) {
                                    // check against "broadcast" addresses
                                    refval1 = 1021;
                                    refval2 = 1028;
                                    if ((val2 >= refval1) && (val1 <= refval2) ){
                                        overlap = true;
                                    }
                                }
                                if ((overlap == false) && (value.contains(" *"))) {
                                    // check against "broadcast" addresses
                                    refval1 = 2041;
                                    refval2 = 2044;
                                    if ((val2 >= refval1) && (val1 <= refval2) ){
                                        overlap = true;
                                    }
                                }
                                if ((overlap == false) && (value.contains(" *"))) {
                                    // check against "broadcast" addresses
                                    refval1 = 2048;
                                    refval2 = 2048;
                                    if ((val2 >= refval1) && (val1 <= refval2) ){
                                        overlap = true;
                                    }
                                }
                            }

                            if (columnIndex == 6) {
                                log.debug("Checking if broadcast (d)");
                                if ((overlap == false) && (value.contains(" *"))) {
                                    // check against "broadcast" addresses
                                    refval1 = 2048;
                                    refval2 = 2048;
                                    if ((val2 >= refval1) && (val1 <= refval2) ){
                                        overlap = true;
                                    }
                                }
                            }
                        }
                    }
                }
        return overlap;
    }

    public void message(LocoNetMessage m) {
        if (findButton.isEnabled() == true)  {
            // Ignore messages when not lookimg for the routes info
            return;
        }

        if ((m.getOpCode() == LnConstants.OPC_IMM_PACKET_2) &&
                (m.getElement(1) == 0x10) &&
                (m.getElement(2) == 1) &&
                (m.getElement(3) == 0) &&
                (m.getElement(4) == 0) &&
                (m.getElement(5) == 0) &&
                (m.getElement(6) == 0) &&
                (m.getElement(7) == 0) &&
                (m.getElement(8) == 0) &&
                (m.getElement(9) == 0) &&
                (m.getElement(10) == 0) &&
                (m.getElement(11) == 0) &&
                (m.getElement(12) == 0) &&
                (m.getElement(13) == 0) &&
                (m.getElement(14) == 0)
                ) {
            // 0xEE 0x10 0x01
            gotTheFindMessage = true;
        } else if ((m.getOpCode() == LnConstants.OPC_ALM_READ) &&
                (m.getElement(1) == 0x10) &&
                (m.getElement(2) == 1) // (command station route report?)
                ) {
            // TODO: improve the message check!

            // Ignore reply from command station "routes"!
            return;
        } else if ((m.getOpCode() == LnConstants.OPC_ALM_READ) &&
                (m.getElement(1) == 0x10) &&
                (m.getElement(2) == 2) // (2nd gen route report?)
                ) {
            // TODO: improve the message check!

            /* Some example "Routes capabilities" messages:
            * [E6 10 02 00 20 00 00 02 08 7C 2C 28 02 31 00 6A]  Device DS78V (s/n 0x128)
            *       in Servo (3-position) mode (routes currently enabled), using turnout
            *       addresses 50 thru 65, with 16 routes of 8 entries per route, may be
            *       configured using ALM messaging.
            * [E6 10 02 0E 10 00 00 02 08 74 2A 4E 27 00 01 29]  Device DS74, s/n 0x13ce,
            *       using addresses 129 thru 136 has been selected for Routes configuration.
            * [E6 10 02 00 20 00 00 02 08 7C 2C 28 00 31 00 68]  Device DS78V (s/n 0x28) in
            *       Servo (3-position) mode (routes currently enabled), using turnout
            *       addresses 50 thru 65, with 16 routes of 8 entries per route, may be
            *       configured using ALM messaging.
            * [E6 10 02 00 10 00 00 02 08 46 40 03 07 7C 01 6E]  Device SE74 (s/n 0x383)
            *       in undefined mode (routes currently disabled), using turnout addresses
            *       253 thru 289, with 64 routes of 16 entries per route, may be
            *       configured using ALM messaging.
            */
            // 0xEE 0x10 0x02 - Routes Reply, 2nd-generation(?)
            cancelRoutesResponseTimer();
            gotA7thGenReply = true;
            int device = m.getElement(9) + (((m.getElement(8) & 0x1 ) == 1) ? 0x80 : 0);
            int serNum = m.getElement(11) + (m.getElement(12) << 7); // correct ??? pull in
                    // a couple of bits from 3 or 8?
            int baseAddr = 1 + m.getElement(13) + (m.getElement(14) << 7);

            int firstOnes = m.getElement(10);
            Accy7thGenDevice d = new Accy7thGenDevice(device, serNum, baseAddr, firstOnes);

            devicesModel.addRow(d);

            startRoutesResponseTimer();
        }
    }

    private void noResponseFound() {
        // no response found now.
        findButton.setEnabled(true);
        gotTheFindMessage = false;
        int rc = devicesModel.getRowCount();
        //log.warn("timeout.  found () devices.", rc);
        if (rc < oldRowCount) {
            // wait until MORECOUNT more requests are sent
            cancelRescanAfterCount = requestCount + MORECOUNT;
            log.warn("WARN - row count dropped here!!! (request count {})", requestCount);
        }
        if (rc != oldRowCount) {
            log.warn("GOT NEW ROW COUNT of {} versus {} for request {}",
                    rc, oldRowCount, requestCount);
            oldRowCount = rc;
        }
    }

    private void cancelRoutesResponseTimer() {
        if (rrt != null) {
            boolean notYetBeenRun = rrt.cancel();
//            if (notYetBeenRun) {
//                log.warn("Canceled timer that has not yet been run");
//            }
        }
        rrt = null;
    }

    TimerTask rrt;
    boolean rrtMustNotStart;
    private void startRoutesResponseTimer() {
        if (rrtMustNotStart) {
            log.debug("Not starting the RRT timer.");
            return;
        }
        rrt = new java.util.TimerTask() {
            @Override
            public void run() {
                noResponseFound();
            }
        };
        jmri.util.TimerUtil.scheduleOnLayoutThread(rrt, 250);
    }

    TimerTask retryIt;
    int cancelRescanAfterCount;
    boolean retryItMustNotStart;
    private void delayFromRequest() {
        if ((retryItMustNotStart) || (noFindRetry == false)){
            log.debug("Not starting the RRT timer.");
            return;
        }
        if ((cancelRescanAfterCount == 0)
                || (cancelRescanAfterCount > requestCount)) {
            retryIt = new java.util.TimerTask() {
                @Override
                public void run() {
                    find7thGenDevices();
                }
            };
            jmri.util.TimerUtil.scheduleOnLayoutThread(retryIt, 1000);

        }
    }
    private LocoNetMessage msgRoutesQuery() {
        return new LocoNetMessage (new int[]{0xEE, 0x10, 0x01, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
    }

    @Override
    public String getHelpTarget() {
        return "package.jmri.jmrix.loconet.accy7thgen.Accy7thgen"; // NOI18N
    }

    @Override
    public String getTitle() {
        return getTitle(Bundle.getMessage("MenuItem7thGenAccy"));
    }

    /**
     * Returns either the Color WHITE or the Color BLACK dependent upon the
     * brightness of what the supplied background color might be. If the
     * background color is too dark then WHITE is returned. If the background
     * color is too bright then BLACK is returned.<br><br>
     *
     * @param currentBackgroundColor (Color Object) The background color text
     *                               will reside on.<br>
     *
     * @return (Color Object) The color WHITE or the Color BLACK.
     */
    public static Color properTextColor(Color currentBackgroundColor) {
        double L; // Holds the brightness value for the supplied color
        Color determinedColor;  // Default

        // Calculate color brightness from supplied color.
        int r = currentBackgroundColor.getRed();
        int g = currentBackgroundColor.getGreen();
        int b = currentBackgroundColor.getBlue();
        L = (int) Math.sqrt((r * r * .241) + (g * g * .691) + (b * b * .068));

        // Return the required text color to suit the
        // supplied background color.
        if (L > 129) {
            determinedColor = Color.decode("#000000");  // White
        }
        else {
            determinedColor = Color.decode("#FFFFFF");  // Black
        }
        return determinedColor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        rrtMustNotStart = true;
        retryItMustNotStart = true;
        boolean wasted = rrt.cancel();
        boolean wasted2 = retryIt.cancel();
        var wasted3 = wasted || wasted2;
        log.debug("results: {}",wasted3);
        devicesScroll = null;
        super.dispose();
    }

    private final static Logger log = LoggerFactory.getLogger(AccySeventhGenDiscoveryPanel.class);
}
