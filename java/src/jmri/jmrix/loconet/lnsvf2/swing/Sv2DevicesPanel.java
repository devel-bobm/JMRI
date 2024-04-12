package jmri.jmrix.loconet.lnsvf2.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Component;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.table.TableRowSorter;
import javax.swing.table.TableCellEditor;
import javax.swing.table.DefaultTableCellRenderer;

import jmri.InstanceManager;
import jmri.jmrix.loconet.LnSv2DevicesManager;
import jmri.jmrix.loconet.LocoNetSystemConnectionMemo;
import jmri.jmrix.loconet.Bundle;
import jmri.jmrix.loconet.swing.LnPanel;
import jmri.swing.JTablePersistenceManager;
import jmri.util.table.ButtonEditor;
import jmri.util.table.ButtonRenderer;
import jmri.util.ThreadingUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A panel for managing LocoNet SV Format 2 Devices.
 *
 * Provides GUI-based mechanisms for:
 * - Initiating the SV Format 2 Device "discovery" process.
 * - Presenting discovered devices in a GUI table.
 * - Relating discovered devices to appropriate decoder definitions,
 *      where possible.
 * - Relating discovered devices to corresponding existing decoder definitions,
 *      where possible.
 * - Changing the device's "destination address".
 * - Initiating the SV Format 2 device "reconfigure"/"reset" process for a given
 *      device.
 * - Persisting panel location on screen, size, and column widths.
 * - Colorizing the "destination address" table cell when the destination address
 *      is "0"
 * - Colorizes any "destination address" table cell which matches another row's
 *      "destination address".
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
public class Sv2DevicesPanel extends LnPanel {

    JButton discoverSvF2DevicesButton = new JButton();
    private LnSv2DevicesManager sv2m;
    private JTable sv2DevicesTable;
    private Sv2DeviceDataModel devicesModel;
    private JScrollPane devicesScroll;
    private transient TableRowSorter<Sv2DeviceDataModel> sorter;

    private JPanel contents = new JPanel();

    /**
     * Constructor for a GUI panel for management of LocoNet SV2 devices
     */
    public Sv2DevicesPanel() {
        // basic formatting: Create pane to hold contents
        // within a scroll box
        contents.setLayout(new BoxLayout(contents, BoxLayout.Y_AXIS));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMultipleInstances() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * Note that creation of the data model requires the connection memo,
     * so creation of the GUI contents has been handled where the memo becomes
     * known.
     */
    @Override
    public void initComponents(LocoNetSystemConnectionMemo memo) {
        super.initComponents(memo);

        this.memo = memo;
        sv2m = memo.getSv2DevicesManager();

        // create the data model and its table
        devicesModel = new Sv2DeviceDataModel(this.memo);
        sv2DevicesTable = new JTable(devicesModel);

        // establish row sorting for the table
        sorter = new TableRowSorter<>(devicesModel);
        sv2DevicesTable.setRowSorter(sorter);

        // establish table physical characteristics persistence
        sv2DevicesTable.setName("SV2 Device Management");
        // Reset and then persist the table's ui state
        InstanceManager.getOptionalDefault(JTablePersistenceManager.class).ifPresent((tpm) -> {
            tpm.resetState(sv2DevicesTable);
            tpm.persist(sv2DevicesTable, true);
        });

        // prevent the layout manager from resizing columns willy-nilly when
        // table contents changes
        sv2DevicesTable.setAutoResizeMode(JTable. AUTO_RESIZE_OFF);

        // make it possible to show and activate buttons in the table
        ButtonRenderer buttonRenderer = new ButtonRenderer();
        sv2DevicesTable.setDefaultRenderer(JButton.class, buttonRenderer);
        TableCellEditor buttonEditor = new ButtonEditor(new JButton());
        sv2DevicesTable.setDefaultEditor(JButton.class, buttonEditor);

        // apply a custom cell renderer to the address column
        sv2DevicesTable.getColumnModel().getColumn(
                Sv2DeviceDataModel.ADDRESSCOLUMN).setCellRenderer(new AddressColumnCellRenderer());

        // configure a ScrollPane for the table contents
        devicesScroll = new JScrollPane(sv2DevicesTable);
        devicesScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        devicesScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        sv2DevicesTable.setShowHorizontalLines(true);
        sv2DevicesTable.setAutoCreateColumnsFromModel(true);

        devicesModel.setParent(devicesScroll);

        // apply scroll pane to the entire panel which contains the table and its scroll pane
        JScrollPane scroll = new JScrollPane(contents);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // establish button for discovery process
        discoverSvF2DevicesButton.setText("Discover SV2 Devices");
        discoverSvF2DevicesButton.setVisible(true);
        discoverSvF2DevicesButton.setToolTipText("Perform SV2 'Discovery' operation");
        discoverSvF2DevicesButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                log.warn("discovery request");
                sv2m.clearDevicesList();
                ThreadingUtil.runOnLayoutEventually( ()->{
                        sv2m.sendSv2DiscoveryRequest();
                });
            }
        });

        // establish panel for the discovery button
        JPanel pane2 = new JPanel();
        pane2.add(discoverSvF2DevicesButton);
        // force alignment and add to contents panel
        contents.add(devicesScroll);

        // define overall panel's layout manager, layout
        setLayout(new BorderLayout());

        // add the scroll pane contents to the overall panel
        devicesScroll.setAlignmentX(0.f);
        add(scroll,BorderLayout.CENTER);

        // add the discovery button pane contents to the overall panel
        pane2.setAlignmentX(0.f);
        add(pane2,BorderLayout.PAGE_END);

        setVisible(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        InstanceManager.getOptionalDefault(JTablePersistenceManager.class).ifPresent((tpm) -> {
            tpm.stopPersisting(sv2DevicesTable);
        });
    }

    /**
     * Cell renderer for destination address column of table.
     *
     * Colors the background of Destination Address cells based on value:
     * - Red if destination address is 0
     * - else White
     */
    public static class AddressColumnCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            //Cells are by default rendered as a JLabel.
            JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);

            //Get the status for the current row.
            Sv2DeviceDataModel tableModel = (Sv2DeviceDataModel) table.getModel();
            if (tableModel.getColumnClass(col).equals(Integer.class) &&
                    ((Integer)tableModel.getValueAt(row, col)) == 0) {
                l.setBackground(Color.RED);
            } else if (tableModel.anyOtherRowsAddressSame(row)) {
                l.setBackground(Color.YELLOW);
            } else {
                l.setBackground(Color.WHITE);
            }
            //Return the JLabel which renders the cell.
            return l;
        }
    }

    @Override
    public String getHelpTarget() {
        return "package.jmri.jmrix.loconet.lnsvf2.swing.Sv2DevicesPanel"; // NOI18N
    }

    @Override
    public String getTitle() {
        return getTitle("MenuItemSv2Manager");
    }


    private final static Logger log = LoggerFactory.getLogger(Sv2DevicesPanel.class);
    }
