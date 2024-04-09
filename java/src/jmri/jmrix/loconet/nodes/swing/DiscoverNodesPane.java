package jmri.jmrix.loconet.nodes.swing;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.table.*;

import jmri.DccLocoAddress;
import jmri.jmrit.roster.Roster;
import jmri.jmrit.roster.RosterEntry;
import jmri.jmrit.symbolicprog.ProgDefault;
import jmri.jmrit.symbolicprog.SymbolicProgBundle;
import jmri.jmrit.symbolicprog.tabbedframe.PaneOpsProgFrame;
import jmri.jmrix.loconet.LocoNetListener;
import jmri.jmrix.loconet.LocoNetSystemConnectionMemo;
import jmri.jmrix.loconet.LnTrafficController;
import jmri.jmrix.loconet.LocoNetMessage;
import jmri.jmrix.loconet.lnsvf2.LnSv2MessageContents;
import jmri.jmrix.loconet.lnsvf2.LnSv2MessageContents.Sv2Command;
import jmri.jmrix.loconet.nodes.LnNode;
import jmri.jmrix.loconet.swing.LnPanel;
import jmri.util.ThreadingUtil;
import jmri.util.TimerUtil;

/**
 * Frame for discover SV2 nodes on LocoNet.
 * <p>
 * Note: This code uses the decoder definitions in the xml/decoders folder
 * to find manufacturer and developer. If a decoder has a manufacturer
 * of "Public-domain and DIY", it's important that that decoder defines a
 * developerID value in its family definition, .
 * <p>
 * @author Daniel Bergqvist Copyright (C) 2020
 * @author B. Milhaupt Copyright (C) 2024
 */
public class DiscoverNodesPane extends LnPanel implements LocoNetListener {

    private LocoNetSystemConnectionMemo _memo = null;
    private LnTrafficController _tc;
    private final List<LnNode> lnNodes = new ArrayList<>();

    protected JPanel nodeTablePanel = null;
    protected Border inputBorder = BorderFactory.createEtchedBorder();

    protected NodeTableModel nodeTableModel = null;
    protected JTable nodeTable = null;

    JButton rescanButton = new JButton("Re-scan SV2 Devices");

    private boolean waitingForReplies = false;
    private java.util.TimerTask waitingForSV2Replies;
    private int waitingForEnd = -1;

    private final String[] nodeTableColumnsNames
            = {"Address", "Manufacturer", "Developer", "Product", "Serial No", "Select"};

    /**
     * {@inheritDoc}
     */
    @Override
    public void initComponents(LocoNetSystemConnectionMemo memo) {
        _memo = memo;

        Container contentPane = this;
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));

        // Set up the assignment panel
        nodeTablePanel = new JPanel();
        nodeTablePanel.setLayout(new BoxLayout(nodeTablePanel, BoxLayout.Y_AXIS));

        nodeTableModel = new NodeTableModel();
        nodeTable = new JTable(nodeTableModel);

        nodeTable.setShowGrid(true);
        nodeTable.setGridColor(Color.black);
        nodeTable.setRowSelectionAllowed(false);
        nodeTable.setFont(new Font("Arial", Font.PLAIN, 14));
        nodeTable.setRowHeight(30);

        nodeTable.getTableHeader().setReorderingAllowed(false);
        nodeTable.setPreferredScrollableViewportSize(new java.awt.Dimension(300, 350));
        TableColumnModel assignmentColumnModel = nodeTable.getColumnModel();

        DefaultTableCellRenderer dtcen = new DefaultTableCellRenderer();
        dtcen.setHorizontalAlignment(SwingConstants.CENTER);
        DefaultTableCellRenderer dtrt = new DefaultTableCellRenderer();
        dtrt.setHorizontalAlignment(SwingConstants.RIGHT);

        TableCellRenderer rendererFromHeader = nodeTable.getTableHeader().getDefaultRenderer();
        JLabel headerLabel = (JLabel) rendererFromHeader;
        headerLabel.setHorizontalAlignment(JLabel.CENTER);
        headerLabel.setBackground(Color.LIGHT_GRAY);

        TableColumn nodeAddrColumn = assignmentColumnModel.getColumn(NodeTableModel.ADDRESS_COLUMN);
        nodeAddrColumn.setMinWidth(40);
        nodeAddrColumn.setMaxWidth(80);
        nodeAddrColumn.setCellRenderer(dtcen);
        nodeAddrColumn.setResizable(false);

        TableColumn nodenumColumn = assignmentColumnModel.getColumn(NodeTableModel.MANUFACTURER_COLUMN);
        nodenumColumn.setMinWidth(150);
        nodenumColumn.setMaxWidth(200);
        nodenumColumn.setCellRenderer(dtcen);
        nodenumColumn.setResizable(true);

        TableColumn nodetypeColumn = assignmentColumnModel.getColumn(NodeTableModel.DEVELOPER_COLUMN);
        nodetypeColumn.setMinWidth(150);
        nodetypeColumn.setMaxWidth(200);
        nodetypeColumn.setCellRenderer(dtcen);
        nodetypeColumn.setResizable(true);

        JScrollPane nodeTableScrollPane = new JScrollPane(nodeTable);

        TableColumn selectColumn = assignmentColumnModel.getColumn(NodeTableModel.SELECT_COLUMN);
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.addItem(Bundle.getMessage("SelectSelect"));
        comboBox.addItem(Bundle.getMessage("SelectEdit"));
        comboBox.addItem(Bundle.getMessage("SelectDelete"));
        comboBox.addItem(Bundle.getMessage("SelectProgram"));
        selectColumn.setCellEditor(new DefaultCellEditor(comboBox));


        selectColumn.setMinWidth(40);
        selectColumn.setMaxWidth(90);
        selectColumn.setCellRenderer(dtcen);
        selectColumn.setResizable(false);




        Border inputBorderTitled = BorderFactory.createTitledBorder(inputBorder,
                " ",
                TitledBorder.LEFT, TitledBorder.ABOVE_TOP);
        nodeTablePanel.add(nodeTableScrollPane, BorderLayout.CENTER);
        nodeTablePanel.setBorder(inputBorderTitled);
        setPreferredSize(new Dimension(950, 550));

        nodeTable.setAutoCreateRowSorter(true);
        nodeTable.getRowSorter().toggleSortOrder(NodeTableModel.ADDRESS_COLUMN);

        contentPane.add(nodeTablePanel);

        // Setup main window buttons
        JPanel panel3 = new JPanel();
        panel3.setLayout(new BoxLayout(panel3, FlowLayout.RIGHT));
        panel3.setPreferredSize(new Dimension(950, 50));

        // Set up Done button
        rescanButton.setVisible(true);
        rescanButton.addActionListener((java.awt.event.ActionEvent e) -> {
            emptyAndRescan();
        });
        panel3.add(rescanButton);

        contentPane.add(panel3);

        nodeTablePanel.setVisible(true);


        _tc = _memo.getLnTrafficController();
        _tc.addLocoNetListener(~0, this);

        startSV2Query();
    }

    /**
     * Handle the done button click.
     */
    public void emptyAndRescan() {

        log.warn("emptyAndRescan .");

        // stop the timer if running
        endTheWait();

        // empty the table
        while (nodeTableModel.getRowCount() > 0) {
            nodeTableModel.removeRow(0);
        }

        // re-send the LnSV2 query
        startSV2Query();
    }

    private void addNode(LnNode node) {
        ThreadingUtil.runOnGUIEventually(() -> {
            nodeTableModel.addRow(node);
        });
    }


    @Override
    public void message(LocoNetMessage msg) {
        if (!waitingForReplies) {
            // skip message if not looking for SV identity messages
            return;
        }

        // Return if the message is not a SV2 message
        if (!LnSv2MessageContents.isSupportedSv2Message(msg)) return;
        LnSv2MessageContents contents = new LnSv2MessageContents(msg);

        Sv2Command command = LnSv2MessageContents.extractMessageType(msg);

        if (command == Sv2Command.SV2_DISCOVER_DEVICE_REPORT) {
            LnNode node = new LnNode(contents, _tc);
            addNode(node);
        }
    }

    /**
     * Get the selected node address from the node table.
     */
    private LnNode getSelectedNode() {
        int addr = (Integer)nodeTable.getValueAt(
                nodeTable.getSelectedRow(), NodeTableModel.ADDRESS_COLUMN);

        for (LnNode node : lnNodes) {
            if (node.getAddress() == addr) return node;
        }
        // If here, the node is not found
        return null;
    }

    /**
     * Open programmer
     */
    public void openProgrammerActionSelected() {

        LnNode selectedNode = getSelectedNode();
        log.warn("LnNode: Mfg: {}, mfgId = {}, Dev: {}, devId = {}, Prod: {}, \n\tdecoderFile: {}.",
                selectedNode.getManufacturer(), selectedNode.getManufacturerID(),
                selectedNode.getDeveloper(), selectedNode.getDeveloperID(),
                selectedNode.getProduct(), selectedNode.getDecoderFile());

        if (selectedNode.getDecoderFile() == null) {
            log.warn("openProgrammerActionSelected: no selected decoder file!  Aborting!");
            return;
        }

        String programmerFilename;

        if (ProgDefault.getDefaultProgFile() != null) {
            programmerFilename = ProgDefault.getDefaultProgFile();
        } else {
            programmerFilename = ProgDefault.findListOfProgFiles()[0];
        }

        DccLocoAddress addr = new DccLocoAddress(selectedNode.getAddress(), false);

        RosterEntry re = new RosterEntry();
        re.setDccAddress(Integer.toString(selectedNode.getAddress()));
        log.warn("selectedNode = {}, selectedNode.getDecoderFile() = {}",selectedNode, selectedNode.getDecoderFile());
        re.setMfg(selectedNode.getDecoderFile().getMfg());
        re.setDecoderFamily(selectedNode.getDecoderFile().getFamily());
        re.setDecoderModel(selectedNode.getDecoderFile().getModel());
        re.setId(SymbolicProgBundle.getMessage("LabelNewDecoder")); // NOI18N

        // note that we're leaving the filename null

        // add the new roster entry to the in-memory roster
        Roster.getDefault().addEntry(re);

        String title = java.text.MessageFormat.format(SymbolicProgBundle.getMessage("FrameServiceProgrammerTitle"),
                new Object[]{selectedNode.getProduct()});
        PaneOpsProgFrame p = new PaneOpsProgFrame(selectedNode.getDecoderFile(), re,
                title, "programmers" + File.separator + programmerFilename + ".xml",
                _memo.getProgrammerManager().getAddressedProgrammer(addr));
        p.pack();
        p.setVisible(true);
        ThreadingUtil.runOnGUIEventually(p::clickButtonReadAll);
    }

    /**
     * Set up table for displaying bit assignments
     */
    public class NodeTableModel extends AbstractTableModel {
        private static final int ADDRESS_COLUMN = 0;
        private static final int MANUFACTURER_COLUMN = 1;
        private static final int DEVELOPER_COLUMN = 2;
        private static final int PRODUCT_COLUMN = 3;
        private static final int SERIALNO_COLUMN = 4;
        private static final int SELECT_COLUMN = 5;
        private static final int NUM_COLUMNS = SELECT_COLUMN + 1;

        @Override
        public String getColumnName(int c) {
            return nodeTableColumnsNames[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            switch (c) {
                case MANUFACTURER_COLUMN:
                case DEVELOPER_COLUMN:
                case PRODUCT_COLUMN:
                    return String.class;

                case SERIALNO_COLUMN:
                case ADDRESS_COLUMN:
                    return Integer.class;

                case SELECT_COLUMN:
                    return String.class;

                default:
                    return String.class;
            }
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            if (c == SELECT_COLUMN) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public int getColumnCount() {
            return NUM_COLUMNS;
        }

        @Override
        public int getRowCount() {
            return lnNodes.size();
        }

        public void removeRow(int row) {
            lnNodes.remove(row);
            fireTableRowsDeleted(row, row);
        }

        public void addRow(LnNode newNode) {
            lnNodes.add(newNode);
            fireTableDataChanged();
        }

        public void changeRow(int row, LnNode aNode) {
            lnNodes.set(row, aNode);
            fireTableDataChanged();
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == SELECT_COLUMN) {
                if (Bundle.getMessage("SelectEdit").equals(value)) {
                    // TODO: rename and populate
                } else if (Bundle.getMessage("SelectDelete").equals(value)) {
                    // TODO: rename and populate
                } else if (Bundle.getMessage("SelectProgram").equals(value)) {
                    openProgrammerActionSelected();
                }
            } else {
                log.info("setValueAt Row {} value {}", row, value);
            }
            fireTableDataChanged();
        }

        @Override
        public Object getValueAt(int r, int c) {
            switch (c) {
                case ADDRESS_COLUMN:
                    return lnNodes.get(r).getAddress();

                case MANUFACTURER_COLUMN:
                    if (lnNodes.get(r) == null) return 0;    // DANIEL
                    return lnNodes.get(r).getManufacturer();

                case DEVELOPER_COLUMN:
                    return lnNodes.get(r).getDeveloper();

                case PRODUCT_COLUMN:
                    return lnNodes.get(r).getProduct();

                case SERIALNO_COLUMN:
                    return lnNodes.get(r).getSerialNumber();
                default:
                    return "";
            }
        }
    }

    private void endTheWait() {
        synchronized(this) {
            waitingForEnd = 1000;
        }
    }

    /**
     * Timer which waits for ~ 10 sec, and can be canceled at every 500 mSec.
     */
    private void startSV2Query() {
        log.warn("Beginning: waiting for SV2 Replies");
        synchronized (this) {
            waitingForEnd = 0;
        }
        waitingForReplies = true;
        _tc.sendLocoNetMessage(LnSv2MessageContents.createSvDiscoverQueryMessage());

        waitingForSV2Replies = new java.util.TimerTask() {
            @Override
            public void run() {
                synchronized (this) {
                    waitingForEnd ++;
                    if (waitingForEnd > 19) {
                        waitingForSV2Replies.cancel();
                        log.warn("ending the SV2 devices request. {}", waitingForEnd);
                        waitingForEnd = -1;
                    }
                }
            }
        };
        TimerUtil.schedule(waitingForSV2Replies, 500, 500);
    }

    private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DiscoverNodesPane.class);

}
