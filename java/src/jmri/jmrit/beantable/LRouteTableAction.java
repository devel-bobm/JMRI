package jmri.jmrit.beantable;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import jmri.Conditional.Operator;
import jmri.*;
import jmri.implementation.DefaultConditionalAction;
import jmri.script.swing.ScriptFileChooser;
import jmri.util.FileUtil;
import jmri.util.JmriJFrame;
import jmri.util.swing.JmriJOptionPane;

/**
 * Swing action to create and register groups of Logix Condtionals to perform a
 * railroad control task.
 *
 * @author Pete Cressman Copyright (C) 2009
 * @author Egbert Broerse i18n 2016
 *
 */
public class LRouteTableAction extends AbstractTableAction<Logix> {

    static final ResourceBundle rbx = ResourceBundle.getBundle("jmri.jmrit.beantable.LRouteTableBundle");

    /**
     * Create an action with a specific title.
     * <p>
     * Note that the argument is the Action title, not the title of the
     * resulting frame. Perhaps this should be changed?
     *
     * @param s title of the action
     */
    public LRouteTableAction(String s) {
        super(s);
        _logixManager = InstanceManager.getNullableDefault(LogixManager.class);
        _conditionalManager = InstanceManager.getNullableDefault(ConditionalManager.class);
        // disable ourself if there is no Logix manager or no Conditional manager available
        if ((_logixManager == null) || (_conditionalManager == null)) {
            setEnabled(false);
        }
        _systemName.setName("hwAddressTextField"); // for GUI test
        _userName.setName("userNameTextField"); // for GUI Test
    }

    public LRouteTableAction() {
        this(Bundle.getMessage("TitleLRouteTable"));
    }

    /**
     * Create the JTable DataModel, along with the changes for the specific case
     * of Road Conditionals.
     */
    @Override
    protected void createModel() {
        m = new LBeanTableDataModel();
    }

    class LBeanTableDataModel extends BeanTableDataModel<Logix> {

        // overlay the value column with the enable column
        // overlay the delete column with the edit column
        public static final int ENABLECOL = VALUECOL;
        public static final int EDITCOL = DELETECOL;

        /**
         * Override to filter out the LRoutes from the rest of Logix.
         */
        @Override
        protected synchronized void updateNameList() {
            // first, remove listeners from the individual objects
            if (sysNameList != null) {
                for (int i = 0; i < sysNameList.size(); i++) {
                    // if object has been deleted, it's not here; ignore it
                    NamedBean b = getBySystemName(sysNameList.get(i));
                    if (b != null) {
                        b.removePropertyChangeListener(this);
                    }
                }
            }
            sysNameList = new ArrayList<>();
            // and add them back in
            getManager().getNamedBeanSet().forEach(b -> {
                if (b.getSystemName().startsWith(getLogixSystemPrefix())) {
                    sysNameList.add(b.getSystemName());
                    b.addPropertyChangeListener(this);
                }
            });
            log.debug("updateNameList: sysNameList size= {}", sysNameList.size());
        }

        @Override
        public String getColumnName(int col) {
            switch (col) {
                case EDITCOL:
                    return ""; // no heading on "Edit"
                case ENABLECOL:
                    return Bundle.getMessage("ColumnHeadEnabled");
                default:
                    return super.getColumnName(col);
            }
        }

        @Override
        public Class<?> getColumnClass(int col) {
            switch (col) {
                case EDITCOL:
                    return JButton.class;
                case ENABLECOL:
                    return Boolean.class;
                default:
                    return super.getColumnClass(col);
            }
        }

        @Override
        public int getPreferredWidth(int col) {
            // override default value for SystemName and UserName columns
            switch (col) {
                case SYSNAMECOL:
                    return new JTextField(20).getPreferredSize().width;
                case USERNAMECOL:
                case COMMENTCOL:
                    return new JTextField(25).getPreferredSize().width;
                case EDITCOL:
                    // not actually used due to the configDeleteColumn, setColumnToHoldButton, configureButton
                    return new JTextField(Bundle.getMessage("ButtonEdit")).getPreferredSize().width+4;
                case ENABLECOL:
                    // not actually used due to the configValueColumn, setColumnToHoldButton, configureButton
                    return new JTextField(5).getPreferredSize().width;
                default:
                    return super.getPreferredWidth(col);
            }
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            switch (col) {
                case EDITCOL:
                case ENABLECOL:
                    return true;
                default:
                    return super.isCellEditable(row, col);
            }
        }

        @Override
        public Object getValueAt(int row, int col) {
            switch (col) {
                case EDITCOL:
                    return Bundle.getMessage("ButtonEdit");
                case ENABLECOL:
                    return ((Logix) getValueAt(row, SYSNAMECOL)).getEnabled();
                default:
                    return super.getValueAt(row, col);
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            switch (col) {
                case EDITCOL:
                    // set up to edit
                    String sName = ((Logix) getValueAt(row, SYSNAMECOL)).getSystemName();
                    editPressed(sName);
                    break;
                case ENABLECOL:
                    // alternate
                    Logix x = (Logix) getValueAt(row, SYSNAMECOL);
                    boolean v = x.getEnabled();
                    x.setEnabled(!v);
                    break;
                default:
                    super.setValueAt(value, row, col);
                    break;
            }
        }

        /**
         * Delete the bean after all the checking has been done.
         * <p>
         * Deactivate the Logix and remove its conditionals.
         */
        @Override
        protected void doDelete(Logix logix) {
            if (logix != null) {
                logix.deActivateLogix();
                // delete the Logix and all its Conditionals
                _logixManager.deleteLogix(logix);
            }
        }

        @Override
        protected boolean matchPropertyName(java.beans.PropertyChangeEvent e) {
            if (Logix.PROPERTY_ENABLED.equals(e.getPropertyName())) {
                return true;
            } else {
                return super.matchPropertyName(e);
            }
        }

        @Override
        public Manager<Logix> getManager() {
            return _logixManager;
        }

        @Override
        public Logix getBySystemName(@Nonnull String name) {
            return _logixManager.getBySystemName(name);
        }

        @Override
        public Logix getByUserName(@Nonnull String name) {
            return _logixManager.getByUserName(name);
        }

        /*public int getDisplayDeleteMsg() { return InstanceManager.getDefault(jmri.UserPreferencesManager.class).getMultipleChoiceOption(getClassName(),"delete"); }
         public void setDisplayDeleteMsg(int boo) { InstanceManager.getDefault(jmri.UserPreferencesManager.class).setMultipleChoiceOption(getClassName(), "delete", boo); }*/
        @Override
        protected String getMasterClassName() {
            return getClassName();
        }

        @Override
        public void configureTable(JTable table) {
            table.setDefaultRenderer(Boolean.class, new EnablingCheckboxRenderer());
            table.setDefaultRenderer(JComboBox.class, new jmri.jmrit.symbolicprog.ValueRenderer());
            table.setDefaultEditor(JComboBox.class, new jmri.jmrit.symbolicprog.ValueEditor());
            super.configureTable(table);
        }

        // Not needed - here for interface compatibility
        @Override
        public void clickOn(Logix t) {
        }

        @Override
        public String getValue(String s) {
            return "";
        }

        // typical to get correct width
        @Override
        protected void configDeleteColumn(JTable table) {
            // have the DELETECOL = EDITCOL column hold a button
            setColumnToHoldButton(table, DELETECOL,
                    new JButton(Bundle.getMessage("ButtonEdit")));
        }

        @Override
        protected void configValueColumn(JTable table) {
        }

        @Override
        protected String getBeanType() {
            return "LRoute";
        }

    }

    @Override
    protected void setTitle() {
        f.setTitle(Bundle.getMessage("TitleLRouteTable"));
    }

    @Override
    protected String helpTarget() {
        return "package.jmri.jmrit.beantable.LRouteTable";
    }

///////////////////////////////////// Edit window //////////////////////////////
    private ConditionalManager _conditionalManager = null;
    private LogixManager _logixManager = null;

    private JTextField _systemName = new JTextField(15);
    private JTextField _userName = new JTextField(25);

    JmriJFrame _addFrame = null;
    private JTabbedPane _tabbedPane = null;

    private RouteInputModel _inputModel;
    private JComboBox<String> _testStateCombo;
    private JRadioButton _inputAllButton;
    private boolean _showAllInput;

    private RouteOutputModel _outputModel;
    private JComboBox<String> _setStateCombo;
    private JRadioButton _outputAllButton;
    private boolean _showAllOutput;

    private AlignmentModel _alignModel;
    private JRadioButton _alignAllButton;
    private boolean _showAllAlign;

    private JCheckBox _lockCheckBox;
    private boolean _lock = false;

    private JPanel _typePanel;
    private JRadioButton _newRouteButton;
    private boolean _newRouteType = true;
    private JRadioButton _initializeButton;
    private boolean _initialize = false;

    private JTextField soundFile = new JTextField(30);
    private JTextField scriptFile = new JTextField(30);

    private JButton cancelButton = new JButton(Bundle.getMessage("ButtonCancel"));
    private JButton createButton = new JButton(Bundle.getMessage("ButtonCreate"));
    private JButton deleteButton = new JButton(Bundle.getMessage("ButtonDelete"));
    private JButton updateButton = new JButton(Bundle.getMessage("ButtonUpdate"));

    private boolean routeDirty = false;  // true to fire reminder to save work
    private boolean checkEnabled = InstanceManager.getDefault(jmri.configurexml.ShutdownPreferences.class).isStoreCheckEnabled();

    ArrayList<RouteInputElement> _inputList;
    private HashMap<String, RouteInputElement> _inputMap;
    private HashMap<String, RouteInputElement> _inputUserMap;
    private ArrayList<RouteInputElement> _includedInputList;

    ArrayList<RouteOutputElement> _outputList;
    private HashMap<String, RouteOutputElement> _outputMap;
    private HashMap<String, RouteOutputElement> _outputUserMap;
    private ArrayList<RouteOutputElement> _includedOutputList;

    ArrayList<AlignElement> _alignList;
    private HashMap<String, AlignElement> _alignMap;
    private HashMap<String, AlignElement> _alignUserMap;
    private ArrayList<AlignElement> _includedAlignList;

    void buildLists() {
        TreeSet<RouteInputElement> inputTS = new TreeSet<>(new RouteElementComparator());
        TreeSet<RouteOutputElement> outputTS = new TreeSet<>(new RouteElementComparator());
        //TreeSet <RouteInputElement>inputTS = new TreeSet<RouteInputElement>();
        //TreeSet <RouteOutputElement>outputTS = new TreeSet<RouteOutputElement>();
        TurnoutManager tm = InstanceManager.turnoutManagerInstance();
        tm.getNamedBeanSet().forEach( nb -> {
            String userName = nb.getUserName();
            String systemName = nb.getSystemName();
            inputTS.add(new RouteInputTurnout(systemName, userName));
            outputTS.add(new RouteOutputTurnout(systemName, userName));
        });

        TreeSet<AlignElement> alignTS = new TreeSet<>(new RouteElementComparator());
        SensorManager sm = InstanceManager.sensorManagerInstance();
        sm.getNamedBeanSet().forEach( nb -> {
            String userName = nb.getUserName();
            String systemName = nb.getSystemName();
            inputTS.add(new RouteInputSensor(systemName, userName));
            outputTS.add(new RouteOutputSensor(systemName, userName));
            alignTS.add(new AlignElement(systemName, userName));
        });
        LightManager lm = InstanceManager.lightManagerInstance();
        lm.getNamedBeanSet().forEach( nb -> {
            String userName = nb.getUserName();
            String systemName = nb.getSystemName();
            inputTS.add(new RouteInputLight(systemName, userName));
            outputTS.add(new RouteOutputLight(systemName, userName));
        });
        SignalHeadManager shm = InstanceManager.getDefault(SignalHeadManager.class);
        shm.getNamedBeanSet().forEach( nb -> {
            String userName = nb.getUserName();
            String systemName = nb.getSystemName();
            inputTS.add(new RouteInputSignal(systemName, userName));
            outputTS.add(new RouteOutputSignal(systemName, userName));
        });
        _includedInputList = new ArrayList<>();
        _includedOutputList = new ArrayList<>();
        _inputList = new ArrayList<>(inputTS.size());
        _outputList = new ArrayList<>(outputTS.size());
        _inputMap = new HashMap<>(inputTS.size());
        _outputMap = new HashMap<>(outputTS.size());
        _inputUserMap = new HashMap<>();
        _outputUserMap = new HashMap<>();
        Iterator<RouteInputElement> it = inputTS.iterator();
        while (it.hasNext()) {
            RouteInputElement elt = it.next();
            _inputList.add(elt);
            String key = elt.getType() + elt.getSysName();
            _inputMap.put(key, elt);
            String user = elt.getUserName();
            if (user != null) {
                key = elt.getType() + user;
                _inputUserMap.put(key, elt);
            }
        }
        Iterator<RouteOutputElement> itOut = outputTS.iterator();
        while (itOut.hasNext()) {
            RouteOutputElement elt = itOut.next();
            _outputList.add(elt);
            String key = elt.getType() + elt.getSysName();
            _outputMap.put(key, elt);
            String user = elt.getUserName();
            if (user != null) {
                key = elt.getType() + user;
                _outputUserMap.put(key, elt);
            }
        }
        _includedAlignList = new ArrayList<>();
        _alignList = new ArrayList<>(alignTS.size());
        _alignMap = new HashMap<>(alignTS.size());
        _alignUserMap = new HashMap<>();
        Iterator<AlignElement> itAlign = alignTS.iterator();
        while (itAlign.hasNext()) {
            AlignElement elt = itAlign.next();
            _alignList.add(elt);
            String key = elt.getType() + elt.getSysName();
            _alignMap.put(key, elt);
            String user = elt.getUserName();
            if (user != null) {
                key = elt.getType() + user;
                _alignUserMap.put(key, elt);
            }
        }
    }

    /**
     * Edit button in Logix Route table pressed.
     *
     * @param sName system name of Logix to edit
     */
    void editPressed(String sName) {
        // Logix was found, initialize for edit
        Logix logix = _logixManager.getBySystemName(sName);
        if (logix == null) {
            log.error("Logix \"{}\" not Found.", sName);
            return;
        }
        // deactivate this Logix
        _systemName.setText(sName);
        // create the Edit Logix Window
        // Use separate Runnable so window is created on top
        Runnable t = () -> {
            setupEdit(null);
            _addFrame.setVisible(true);
        };
        javax.swing.SwingUtilities.invokeLater(t);
    }

    /**
     * Interprets the conditionals from the Logix that was selected for editing
     * and attempts to reconstruct the window entries.
     *
     * @param e the action event
     */
    void setupEdit(ActionEvent e) {
        makeEditWindow();
        Logix logix = checkNamesOK();
        if (logix == null) {
            return;
        }
        logix.deActivateLogix();
        // get information for this route
        _systemName.setEnabled(false);
        _userName.setEnabled(false);
        _systemName.setText(logix.getSystemName());
        _userName.setText(logix.getUserName());
        String logixSysName = logix.getSystemName();
        int numConditionals = logix.getNumConditionals();
        log.debug("setupEdit: logixSysName= {}, numConditionals= {}", logixSysName, numConditionals);
        for (int i = 0; i < numConditionals; i++) {
            String cSysName = logix.getConditionalByNumberOrder(i);
            switch (getRouteConditionalType(logixSysName, cSysName)) {
                case 'T':
                    getControlsAndActions(cSysName);
                    break;
                case 'A':
                    getAlignmentSensors(cSysName);
                    break;
                case 'L':
                    getLockConditions(cSysName);
                    break;
                default:
                    log.warn("Unexpected getRouteConditionalType {}", getRouteConditionalType(logixSysName, cSysName));
                    break;
            }
        }
        // set up buttons and notes
        deleteButton.setVisible(true);
        cancelButton.setVisible(true);
        updateButton.setVisible(true);
        _typePanel.setVisible(false);
        _initialize = getLogixInitializer().equals(logixSysName);
        if (_initialize) {
            _initializeButton.doClick();
        } else {
            _newRouteButton.doClick();
        }
        createButton.setVisible(false);
        _addFrame.setTitle(rbx.getString("LRouteEditTitle"));
    }

    /**
     * Get the type letter from the possible LRoute conditional.
     *
     * @param logixSysName logix system name
     * @param cSysName conditional system name
     * @return the type letter
     */
    char getRouteConditionalType(String logixSysName, String cSysName) {
        if (cSysName.startsWith(logixSysName)) {
            char[] chNum = cSysName.substring(logixSysName.length()).toCharArray();
            int i = 0;
            while (Character.isDigit(chNum[i])) {
                i++;
            }
            return chNum[i];
        }
        return 0;
    }

    /**
     * Extract the Control (input) and Action (output) elements and their
     * states.
     *
     * @param cSysName the conditional system name
     */
    void getControlsAndActions(String cSysName) {
        Conditional c = _conditionalManager.getBySystemName(cSysName);
        if (c != null) {
            List<ConditionalAction> actionList = c.getCopyOfActions();
            boolean onChange = false;
            for (int k = 0; k < actionList.size(); k++) {
                ConditionalAction action = actionList.get(k);
                int type;
                switch (action.getType()) {
                    case SET_SENSOR:
                        type = SENSOR_TYPE;
                        break;
                    case SET_TURNOUT:
                        type = TURNOUT_TYPE;
                        break;
                    case SET_LIGHT:
                        type = LIGHT_TYPE;
                        break;
                    case SET_SIGNAL_APPEARANCE:
                    case SET_SIGNAL_HELD:
                    case CLEAR_SIGNAL_HELD:
                    case SET_SIGNAL_DARK:
                    case SET_SIGNAL_LIT:
                        type = SIGNAL_TYPE;
                        break;
                    case RUN_SCRIPT:
                        scriptFile.setText(action.getActionString());
                        continue;
                    case PLAY_SOUND:
                        soundFile.setText(action.getActionString());
                        continue;
                    default:
                        JmriJOptionPane.showMessageDialog(
                                _addFrame, java.text.MessageFormat.format(rbx.getString("TypeWarn"),
                                        new Object[]{action.toString(), c.getSystemName()}),
                                rbx.getString("EditDiff"), JmriJOptionPane.WARNING_MESSAGE);
                        continue;
                }
                String name = action.getDeviceName();
                String key = type + name;
                RouteOutputElement elt = _outputUserMap.get(key);
                if (elt == null) { // try in system name map
                    elt = _outputMap.get(key);
                }
                if (elt == null) {
                    JmriJOptionPane.showMessageDialog(
                            _addFrame, java.text.MessageFormat.format(rbx.getString("TypeWarn"),
                                    new Object[]{action.toString(), c.getSystemName()}),
                            rbx.getString("EditDiff"), JmriJOptionPane.WARNING_MESSAGE);
                } else {
                    elt.setIncluded(true);
                    elt.setState(action.getActionData());
                    boolean change = (action.getOption() == Conditional.ACTION_OPTION_ON_CHANGE);
                    if (k == 0) {
                        onChange = change;
                    } else if (change != onChange) {
                        JmriJOptionPane.showMessageDialog(
                                _addFrame, java.text.MessageFormat.format(rbx.getString("OnChangeWarn"),
                                        new Object[]{action.toString(), c.getSystemName()}),
                                rbx.getString("EditDiff"), JmriJOptionPane.WARNING_MESSAGE);
                    }
                }
            }
            List<ConditionalVariable> varList = c.getCopyOfStateVariables();
            for (int k = 0; k < varList.size(); k++) {
                ConditionalVariable variable = varList.get(k);
                Conditional.Type testState = variable.getType();
                //boolean negated = variable.isNegated();
                int type;
                switch (testState) {
                    case SENSOR_ACTIVE:
                        type = SENSOR_TYPE;
                        //if (negated) testState = Conditional.TYPE_SENSOR_INACTIVE;
                        break;
                    case SENSOR_INACTIVE:
                        type = SENSOR_TYPE;
                        //if (negated) testState = Conditional.TYPE_SENSOR_ACTIVE;
                        break;
                    case TURNOUT_CLOSED:
                        type = TURNOUT_TYPE;
                        //if (negated) testState = Conditional.TYPE_TURNOUT_THROWN;
                        break;
                    case TURNOUT_THROWN:
                        type = TURNOUT_TYPE;
                        //if (negated) testState = Conditional.TYPE_TURNOUT_CLOSED;
                        break;
                    case LIGHT_ON:
                        type = LIGHT_TYPE;
                        //if (negated) testState = Conditional.TYPE_LIGHT_OFF;
                        break;
                    case LIGHT_OFF:
                        type = LIGHT_TYPE;
                        //if (negated) testState = Conditional.TYPE_LIGHT_ON;
                        break;
                    case SIGNAL_HEAD_LIT:
                    case SIGNAL_HEAD_RED:
                    case SIGNAL_HEAD_YELLOW:
                    case SIGNAL_HEAD_GREEN:
                    case SIGNAL_HEAD_DARK:
                    case SIGNAL_HEAD_FLASHRED:
                    case SIGNAL_HEAD_FLASHYELLOW:
                    case SIGNAL_HEAD_FLASHGREEN:
                    case SIGNAL_HEAD_HELD:
                        type = SIGNAL_TYPE;
                        break;
                    default:
                        if (!getLogixInitializer().equals(variable.getName())) {
                            JmriJOptionPane.showMessageDialog(
                                    _addFrame, java.text.MessageFormat.format(rbx.getString("TypeWarnVar"),
                                            new Object[]{variable.toString(), c.getSystemName()}),
                                    rbx.getString("EditDiff"), JmriJOptionPane.WARNING_MESSAGE);
                        }
                        continue;
                }
                int testStateInt = testState.getIntValue();
                Operator opern = variable.getOpern();
                if (k != 0 && (opern == Conditional.Operator.AND)) {
                    // guess this is a VETO
                    testStateInt += VETO;
                } else if (onChange) {
                    testStateInt = Route.ONCHANGE;
                }
                String name = variable.getName();
                String key = type + name;
                RouteInputElement elt = _inputUserMap.get(key);
                if (elt == null) { // try in system name map
                    elt = _inputMap.get(key);
                }
                if (elt == null) {
                    if (!getLogixInitializer().equals(name)) {
                        JmriJOptionPane.showMessageDialog(
                                _addFrame, java.text.MessageFormat.format(rbx.getString("TypeWarnVar"),
                                        new Object[]{variable.toString(), c.getSystemName()}),
                                rbx.getString("EditDiff"), JmriJOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    elt.setIncluded(true);
                    elt.setState(testStateInt);
                }
            }
        }
    }   // getControlsAndActions

    /**
     * Extract the Alignment Sensors and their types.
     *
     * @param cSysName the conditional system name
     */
    private void getAlignmentSensors(String cSysName) {
        Conditional c = _conditionalManager.getBySystemName(cSysName);
        if (c != null) {
            AlignElement element = null;
            List<ConditionalAction> actionList = c.getCopyOfActions();
            for (int k = 0; k < actionList.size(); k++) {
                ConditionalAction action = actionList.get(k);
                if (action.getType() != Conditional.Action.SET_SENSOR) {
                    JmriJOptionPane.showMessageDialog(
                            _addFrame, java.text.MessageFormat.format(rbx.getString("AlignWarn1"),
                                    new Object[]{action.toString(), c.getSystemName()}),
                            rbx.getString("EditDiff"), JmriJOptionPane.WARNING_MESSAGE);
                } else {
                    String name = action.getDeviceName();
                    String key = SENSOR_TYPE + name;
                    element = _alignUserMap.get(key);
                    if (element == null) { // try in system name map
                        element = _alignMap.get(key);
                    }
                    if (element == null) {
                        JmriJOptionPane.showMessageDialog(
                                _addFrame, java.text.MessageFormat.format(rbx.getString("TypeWarn"),
                                        new Object[]{action.toString(), c.getSystemName()}),
                                rbx.getString("EditDiff"), JmriJOptionPane.WARNING_MESSAGE);

                    } else if (!name.equals(action.getDeviceName())) {
                        JmriJOptionPane.showMessageDialog(
                                _addFrame, java.text.MessageFormat.format(rbx.getString("AlignWarn2"),
                                        new Object[]{action.toString(), action.getDeviceName(), c.getSystemName()}),
                                rbx.getString("EditDiff"), JmriJOptionPane.WARNING_MESSAGE);

                    } else {
                        element.setIncluded(true);
                    }
                }
            }
            // the action elements are identified in getControlsAndActions().
            //  Just identify the type of sensing
            List<ConditionalVariable> varList = c.getCopyOfStateVariables();
            int atype = 0;
            for (int k = 0; k < varList.size(); k++) {
                ConditionalVariable variable = varList.get(k);
                Conditional.Type testState = variable.getType();
                int type;
                switch (testState) {
                    case SENSOR_ACTIVE:
                    case SENSOR_INACTIVE:
                        type = SENSOR_TYPE;
                        break;
                    case TURNOUT_CLOSED:
                    case TURNOUT_THROWN:
                        type = TURNOUT_TYPE;
                        break;
                    case LIGHT_ON:
                    case LIGHT_OFF:
                        type = LIGHT_TYPE;
                        break;
                    case SIGNAL_HEAD_LIT:
                    case SIGNAL_HEAD_RED:
                    case SIGNAL_HEAD_YELLOW:
                    case SIGNAL_HEAD_GREEN:
                    case SIGNAL_HEAD_DARK:
                    case SIGNAL_HEAD_FLASHRED:
                    case SIGNAL_HEAD_FLASHYELLOW:
                    case SIGNAL_HEAD_FLASHGREEN:
                    case SIGNAL_HEAD_HELD:
                        type = SIGNAL_TYPE;
                        break;
                    default:
                        if (!getLogixInitializer().equals(variable.getName())) {
                            JmriJOptionPane.showMessageDialog(
                                    _addFrame, java.text.MessageFormat.format(rbx.getString("TypeWarnVar"),
                                            new Object[]{variable.toString(), c.getSystemName()}),
                                    rbx.getString("EditDiff"), JmriJOptionPane.WARNING_MESSAGE);
                        }
                        continue;
                }
                if (k == 0) {
                    atype = type;
                } else if (atype != type) {
                    // more than one type. therefor, ALL
                    atype = ALL_TYPE;
                    break;
                }
            }
            if (element != null) {
                element.setState(atype);
            }
        }
    }

    /**
     * Extract the Lock expression. For now, same as action control expression.
     *
     * @param cSysName the conditional system name
     */
    private void getLockConditions(String cSysName) {
        Conditional c = _conditionalManager.getBySystemName(cSysName);
        if (c != null) {
            _lock = true;
            // Verify conditional is what we think it is
            ArrayList<RouteOutputElement> tList = makeTurnoutLockList();
            List<ConditionalAction> actionList = c.getCopyOfActions();
            if (actionList.size() != tList.size()) {
                JmriJOptionPane.showMessageDialog(
                        _addFrame, java.text.MessageFormat.format(rbx.getString("LockWarn1"),
                                new Object[]{Integer.toString(tList.size()), c.getSystemName(),
                                    Integer.toString(actionList.size())}),
                        rbx.getString("EditDiff"), JmriJOptionPane.WARNING_MESSAGE);
            }
            for (int k = 0; k < actionList.size(); k++) {
                ConditionalAction action = actionList.get(k);
                if (action.getType() != Conditional.Action.LOCK_TURNOUT) {
                    JmriJOptionPane.showMessageDialog(
                            _addFrame, java.text.MessageFormat.format(rbx.getString("LockWarn2"),
                                    new Object[]{action.getDeviceName(), c.getSystemName()}),
                            rbx.getString("EditDiff"), JmriJOptionPane.WARNING_MESSAGE);
                } else {
                    String name = action.getDeviceName();
                    boolean found = false;
                    ArrayList<RouteOutputElement> lockList = makeTurnoutLockList();
                    for (int j = 0; j < lockList.size(); j++) {
                        RouteOutputElement elt = lockList.get(j);
                        if (name.equals(elt.getUserName()) || name.equals(elt.getSysName())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        JmriJOptionPane.showMessageDialog(
                                _addFrame, java.text.MessageFormat.format(rbx.getString("LockWarn3"),
                                        new Object[]{name, c.getSystemName()}),
                                rbx.getString("EditDiff"), JmriJOptionPane.WARNING_MESSAGE);
                    }
                }
            }
        }
    }

    /**
     * Responds to the Cancel button.
     *
     * @param e the action event
     */
    private void cancelPressed(ActionEvent e) {
        if (_addFrame.getTitle().equals(rbx.getString("LRouteEditTitle"))) { // Warnings shown are useless when cancelling Add New LRoute
            Logix logix = checkNamesOK();
            if (logix != null) {
                logix.activateLogix();
            }
        }
        clearPage();
    }

    @Override
    protected void addPressed(ActionEvent e) {
        makeEditWindow();
        _tabbedPane.setSelectedIndex(0);
        createButton.setVisible(true);
        cancelButton.setVisible(true);
        _typePanel.setVisible(true);
        _addFrame.setVisible(true);
        _systemName.setEnabled(true);
        _userName.setEnabled(true);
        _addFrame.setTitle(rbx.getString("LRouteAddTitle"));

        _addFrame.setEscapeKeyClosesWindow(true);
        _addFrame.getRootPane().setDefaultButton(createButton);
    }

    /**
     * Set up Create/Edit LRoute pane
     */
    private void makeEditWindow() {
        buildLists();
        if (_addFrame == null) {
            _addFrame = new JmriJFrame(rbx.getString("LRouteAddTitle"), false, false);
            _addFrame.addHelpMenu("package.jmri.jmrit.beantable.LRouteAddEdit", true);
            _addFrame.setLocation(100, 30);

            _tabbedPane = new JTabbedPane();

            //////////////////////////////////// Tab 1 /////////////////////////////
            JPanel tab1 = new JPanel();
            tab1.setLayout(new BoxLayout(tab1, BoxLayout.Y_AXIS));
            tab1.add(Box.createVerticalStrut(10));
            // add system name
            JPanel p = new JPanel();
            p.setLayout(new FlowLayout());
            p.add(new JLabel(Bundle.getMessage("LabelSystemName")));
            p.add(_systemName);
            _systemName.setToolTipText(rbx.getString("SystemNameHint"));
            tab1.add(p);
            // add user name
            p = new JPanel();
            p.setLayout(new FlowLayout());
            p.add(new JLabel(Bundle.getMessage("LabelUserName")));
            p.add(_userName);
            _userName.setToolTipText(rbx.getString("UserNameHint"));
            tab1.add(p);

            JPanel pa = new JPanel();
            p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.add(new JLabel(rbx.getString("Guide1")));
            p.add(new JLabel(rbx.getString("Guide2")));
            p.add(new JLabel(rbx.getString("Guide3")));
            p.add(new JLabel(rbx.getString("Guide4")));
            pa.add(p);
            tab1.add(pa);

            _newRouteButton = new JRadioButton(rbx.getString("NewRoute"), true);
            JRadioButton oldRoute = new JRadioButton(rbx.getString("OldRoute"), false);
            _initializeButton = new JRadioButton(rbx.getString("Initialize"), false);
            _newRouteButton.setToolTipText(rbx.getString("NewRouteHint"));
            _newRouteButton.addActionListener( e -> {
                _newRouteType = true;
                _systemName.setEnabled(true);
            });
            oldRoute.setToolTipText(rbx.getString("OldRouteHint"));
            oldRoute.addActionListener( e -> {
                _newRouteType = false;
                _systemName.setEnabled(true);
            });
            _initializeButton.setToolTipText(rbx.getString("InitializeHint"));
            _initializeButton.addActionListener( e -> {
                _initialize = true;
                _newRouteType = true;
                _systemName.setEnabled(false);
                _systemName.setText(getLogixInitializer());
            });
            _typePanel = makeShowButtons(_newRouteButton, oldRoute, _initializeButton, rbx.getString("LRouteType") + ":");
            _typePanel.setBorder(BorderFactory.createEtchedBorder());
            tab1.add(_typePanel);
            tab1.add(Box.createVerticalGlue());

            // add buttons
            JPanel pb = new JPanel();
            pb.setLayout(new FlowLayout());
            // Cancel button
            pb.add(cancelButton);
            cancelButton.addActionListener(this::cancelPressed);
            cancelButton.setToolTipText(Bundle.getMessage("TooltipCancelRoute"));
            cancelButton.setName("CancelButton");
            // Add Route button
            pb.add(createButton);
            createButton.addActionListener(this::createPressed);
            createButton.setToolTipText(rbx.getString("CreateHint"));
            createButton.setName("CreateButton");
            // Delete Route button
            pb.add(deleteButton);
            deleteButton.addActionListener(this::deletePressed);
            deleteButton.setToolTipText(rbx.getString("DeleteHint"));
            // Update Route button
            pb.add(updateButton);
            updateButton.addActionListener( e -> updatePressed());
            updateButton.setToolTipText(rbx.getString("UpdateHint"));
            updateButton.setName("UpdateButton");

            // Show the initial buttons, and hide the others
            cancelButton.setVisible(true);
            updateButton.setVisible(false);
            createButton.setVisible(false);
            deleteButton.setVisible(false);
            tab1.add(pb);

            tab1.setVisible(true);
            _tabbedPane.addTab(rbx.getString("BasicTab"), null, tab1, rbx.getString("BasicTabHint"));

            //////////////////////////////////// Tab 2 /////////////////////////////
            JPanel tab2 = new JPanel();
            tab2.setLayout(new BoxLayout(tab2, BoxLayout.Y_AXIS));
            tab2.add(new JLabel(rbx.getString("OutputTitle") + ":"));
            _outputAllButton = new JRadioButton(Bundle.getMessage("All"), true);
            JRadioButton includedOutputButton = new JRadioButton(Bundle.getMessage("Included"), false);
            tab2.add(makeShowButtons(_outputAllButton, includedOutputButton, null, Bundle.getMessage("Show") + ":"));
            _outputAllButton.addActionListener( e -> {
                // Setup for display of all Turnouts, if needed
                if (!_showAllOutput) {
                    _showAllOutput = true;
                    _outputModel.fireTableDataChanged();
                }
            });
            includedOutputButton.addActionListener( e -> {
                // Setup for display of included Turnouts only, if needed
                if (_showAllOutput) {
                    _showAllOutput = false;
                    initializeIncludedOutputList();
                    _outputModel.fireTableDataChanged();
                }
            });
            tab2.add(new JLabel(rbx.getString("PickOutput")));

            _outputModel = new RouteOutputModel();
            JTable routeOutputTable = new JTable(_outputModel);
            JScrollPane _outputScrollPane = makeColumns(routeOutputTable, _setStateCombo, true);
            tab2.add(_outputScrollPane, BorderLayout.CENTER);
            tab2.setVisible(true);
            _tabbedPane.addTab(rbx.getString("ActionTab"), null, tab2, rbx.getString("ActionTabHint"));

            //////////////////////////////////// Tab 3 /////////////////////////////
            JPanel tab3 = new JPanel();
            tab3.setLayout(new BoxLayout(tab3, BoxLayout.Y_AXIS));
            tab3.add(new JLabel(rbx.getString("InputTitle") + ":"));
            _inputAllButton = new JRadioButton(Bundle.getMessage("All"), true);
            JRadioButton includedInputButton = new JRadioButton(Bundle.getMessage("Included"), false);
            tab3.add(makeShowButtons(_inputAllButton, includedInputButton, null, Bundle.getMessage("Show") + ":"));
            _inputAllButton.addActionListener( e -> {
                // Setup for display of all Turnouts, if needed
                if (!_showAllInput) {
                    _showAllInput = true;
                    _inputModel.fireTableDataChanged();
                }
            });
            includedInputButton.addActionListener( e -> {
                // Setup for display of included Turnouts only, if needed
                if (_showAllInput) {
                    _showAllInput = false;
                    initializeIncludedInputList();
                    _inputModel.fireTableDataChanged();
                }
            });
            tab3.add(new JLabel(rbx.getString("PickInput")));

            _inputModel = new RouteInputModel();
            JTable routeInputTable = new JTable(_inputModel);
            //ROW_HEIGHT = routeInputTable.getRowHeight();
            JScrollPane _inputScrollPane = makeColumns(routeInputTable, _testStateCombo, true);
            tab3.add(_inputScrollPane, BorderLayout.CENTER);
            tab3.setVisible(true);
            _tabbedPane.addTab(rbx.getString("TriggerTab"), null, tab3, rbx.getString("TriggerTabHint"));

            ////////////////////// Tab 4 /////////////////
            JPanel tab4 = new JPanel();
            tab4.setLayout(new BoxLayout(tab4, BoxLayout.Y_AXIS));
            tab4.add(new JLabel(rbx.getString("MiscTitle") + ":"));
            // Enter filenames for sound, script
            JPanel p25 = new JPanel();
            p25.setLayout(new FlowLayout());
            p25.add(new JLabel(Bundle.getMessage("LabelPlaySound")));
            JButton ss = new JButton("...");
            ss.addActionListener( e -> setSoundPressed());
            p25.add(ss);
            p25.add(soundFile);
            tab4.add(p25);

            p25 = new JPanel();
            p25.setLayout(new FlowLayout());
            p25.add(new JLabel(Bundle.getMessage("LabelRunScript")));
            ss = new JButton("...");
            ss.addActionListener( e -> setScriptPressed());
            p25.add(ss);
            p25.add(scriptFile);
            tab4.add(p25);

            p25 = new JPanel();
            p25.setLayout(new FlowLayout());
            p25.add(new JLabel(rbx.getString("SetLocks") + ":"));
            _lockCheckBox = new JCheckBox(rbx.getString("Lock"), true);
            _lockCheckBox.addActionListener( e -> {
                // Setup for display of all Turnouts, if needed
                _lock = _lockCheckBox.isSelected();
            });
            p25.add(_lockCheckBox);
            tab4.add(p25);

            _alignAllButton = new JRadioButton(Bundle.getMessage("All"), true);
            JRadioButton includedAlignButton = new JRadioButton(Bundle.getMessage("Included"), false);
            tab4.add(makeShowButtons(_alignAllButton, includedAlignButton, null, Bundle.getMessage("Show") + ":"));
            _alignAllButton.addActionListener( e -> {
                // Setup for display of all Turnouts, if needed
                if (!_showAllAlign) {
                    _showAllAlign = true;
                    _alignModel.fireTableDataChanged();
                }
            });
            includedAlignButton.addActionListener( e -> {
                // Setup for display of included Turnouts only, if needed
                if (_showAllAlign) {
                    _showAllAlign = false;
                    initializeIncludedAlignList();
                    _alignModel.fireTableDataChanged();
                }
            });
            tab4.add(new JLabel(rbx.getString("PickAlign")));
            _alignModel = new AlignmentModel();
            JTable alignTable = new JTable(_alignModel);
            JComboBox<String> _alignCombo = new JComboBox<>();
            for (String state : ALIGNMENT_STATES) {
                _alignCombo.addItem(state);
            }
            JScrollPane alignScrollPane = makeColumns(alignTable, _alignCombo, false);
            //alignTable.setPreferredScrollableViewportSize(new java.awt.Dimension(250,200));
            _alignCombo = new JComboBox<>();
            for (String state : ALIGNMENT_STATES) {
                _alignCombo.addItem(state);
            }
            tab4.add(alignScrollPane, BorderLayout.CENTER);
            tab4.setVisible(true);
            _tabbedPane.addTab(rbx.getString("MiscTab"), null, tab4, rbx.getString("MiscTabHint"));

            Container contentPane = _addFrame.getContentPane();
            //tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

            ///////////////////////////////////
            JPanel pt = new JPanel();
            pt.add(_tabbedPane);
            contentPane.add(pt);

            // set listener for window closing
            _addFrame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    // remind to save, if Route was created or edited
                    if (routeDirty) {
                        showReminderMessage();
                    }
                    clearPage();
                    _addFrame.setVisible(false);
                    _inputModel.dispose();
                    _outputModel.dispose();
                    routeDirty = false;
                }
            });

            _addFrame.pack();
            _inputAllButton.doClick();
            _outputAllButton.doClick();
            _alignAllButton.doClick();
            _newRouteButton.doClick();
            if (_initialize) {
                _initializeButton.doClick();
            }
        } else {
            _addFrame.setVisible(true);
        }
    }

    private void showReminderMessage() {
        if (checkEnabled) {
            return;
        }
        InstanceManager.getDefault(UserPreferencesManager.class).
            showInfoMessage(Bundle.getMessage("ReminderTitle"), Bundle.getMessage("ReminderSaveString", Bundle.getMessage("BeanNameLRoute")),
                    getClassName(),
                    "remindSaveRoute"); // NOI18N
    }

    /*
     * Utility for addPressed
     */
    private JPanel makeShowButtons(JRadioButton allButton, JRadioButton includeButton,
            JRadioButton extraButton, String msg) {
        JPanel panel = new JPanel();
        panel.add(new JLabel(msg));
        panel.add(allButton);
        panel.add(includeButton);
        ButtonGroup selGroup = new ButtonGroup();
        selGroup.add(allButton);
        selGroup.add(includeButton);
        if (extraButton != null) {
            panel.add(extraButton);
            selGroup.add(extraButton);
        }
        return panel;
    }

    /*
     * Utility for addPressed
     */
    private JScrollPane makeColumns(@Nonnull JTable table, JComboBox<String> box, boolean specialBox) {
        table.setRowSelectionAllowed(false);
        //table.setPreferredScrollableViewportSize(new java.awt.Dimension(250,450));
        TableColumnModel columnModel = table.getColumnModel();

        TableColumn sNameColumnT = columnModel.getColumn(RouteElementModel.SNAME_COLUMN);
        sNameColumnT.setResizable(true);
        sNameColumnT.setMinWidth(75);
        //sNameColumnT.setMaxWidth(110);

        TableColumn uNameColumnT = columnModel.getColumn(RouteElementModel.UNAME_COLUMN);
        uNameColumnT.setResizable(true);
        uNameColumnT.setMinWidth(75);
        //uNameColumnT.setMaxWidth(260);

        TableColumn typeColumnT = columnModel.getColumn(RouteElementModel.TYPE_COLUMN);
        typeColumnT.setResizable(true);
        typeColumnT.setMinWidth(50);
        //typeColumnT.setMaxWidth(110);

        TableColumn includeColumnT = columnModel.getColumn(RouteElementModel.INCLUDE_COLUMN);
        includeColumnT.setResizable(false);
        includeColumnT.setMinWidth(30);
        includeColumnT.setMaxWidth(60);

        TableColumn stateColumnT = columnModel.getColumn(RouteElementModel.STATE_COLUMN);
        if (specialBox) {
            box = new JComboBox<>();
            stateColumnT.setCellEditor(new ComboBoxCellEditor(box));
        } else {
            stateColumnT.setCellEditor(new DefaultCellEditor(box));
        }
        stateColumnT.setResizable(false);
        stateColumnT.setMinWidth(75);
        //stateColumnT.setMaxWidth(1310);

        return new JScrollPane(table);
    }

    /**
     * Initialize list of included input elements
     */
    private void initializeIncludedInputList() {
        _includedInputList = new ArrayList<>();
        for (int i = 0; i < _inputList.size(); i++) {
            if (_inputList.get(i).isIncluded()) {
                _includedInputList.add(_inputList.get(i));
            }
        }
    }

    /**
     * Initialize list of included input elements
     */
    private void initializeIncludedOutputList() {
        _includedOutputList = new ArrayList<>();
        for (int i = 0; i < _outputList.size(); i++) {
            if (_outputList.get(i).isIncluded()) {
                _includedOutputList.add(_outputList.get(i));
            }
        }
    }

    /**
     * Initialize list of included alignment sensors
     */
    private void initializeIncludedAlignList() {
        _includedAlignList = new ArrayList<>();
        for (int i = 0; i < _alignList.size(); i++) {
            if (_alignList.get(i).isIncluded()) {
                _includedAlignList.add(_alignList.get(i));
            }
        }
    }

    private ArrayList<RouteOutputElement> makeTurnoutLockList() {
        ArrayList<RouteOutputElement> list = new ArrayList<>();
        for (int i = 0; i < _outputList.size(); i++) {
            if (_outputList.get(i).isIncluded()) {
                RouteOutputElement elt = _outputList.get(i);
                if ((elt.getType() == TURNOUT_TYPE) && (elt.getState() != Route.TOGGLE)) {
                    list.add(elt);
                }
            }
        }
        return list;
    }

    private void showMessage(String msg) {

        JmriJOptionPane.showMessageDialog(
                _addFrame, rbx.getString(msg), Bundle.getMessage("WarningTitle"),
                JmriJOptionPane.WARNING_MESSAGE);
    }

    private boolean checkNewNamesOK() {
        // Get system name and user name
        String sName = _systemName.getText();
        if (sName.length() == 0 || sName.equals(getLogixSystemPrefix())) {
            showMessage("EnterNames");
            return false;
        }
        if (!sName.startsWith(getLogixSystemPrefix())) {
            sName = getLogixSystemPrefix() + sName;
        }
        // check if a Route with this system name already exists
        if (_logixManager.getBySystemName(sName) != null) {
            // Route already exists
            showMessage("DuplicateSys");
            updateButton.setVisible(true);
            return false;
        }
        String uName = _userName.getText();
        // check if a Route with the same user name exists
        if (!uName.isEmpty()) {
            if (_logixManager.getByUserName(uName) != null) {
                // Route with this user name already exists
                showMessage("DuplicateUser");
                updateButton.setVisible(true);
                return false;
            } else {
                return true;
            }
        }
        _systemName.setText(sName);
        return true;
    }

    @CheckForNull
    private Logix checkNamesOK() {
        // Get system name and user name
        String sName = _systemName.getText();
        if (sName.length() == 0) {
            showMessage("EnterNames");
            return null;
        }
        Logix logix = _logixManager.getBySystemName(sName);
        if (!sName.startsWith(getLogixSystemPrefix())) {
            sName = getLogixSystemPrefix() + sName;
        }
        if (logix != null) {
            return logix;
        }
        String uName = _userName.getText();
        if (uName.length() != 0) {
            logix = _logixManager.getByUserName(uName);
            if (logix != null) {
                return logix;
            }
        }
        logix = _logixManager.createNewLogix(sName, uName);
        if (logix == null) {
            // should never get here
            log.error("Unknown failure to create Route with System Name: {}", sName);
        }
        return logix;
    }

    private JFileChooser soundChooser = null;

    /**
     * Set the sound file
     */
    private void setSoundPressed() {
        if (soundChooser == null) {
            soundChooser = new jmri.util.swing.JmriJFileChooser(FileUtil.getUserFilesPath());
            soundChooser.setFileFilter(new jmri.util.NoArchiveFileFilter());
        }
        soundChooser.rescanCurrentDirectory();
        int retVal = soundChooser.showOpenDialog(null);
        // handle selection or cancel
        if (retVal == JFileChooser.APPROVE_OPTION) {
            try {
                soundFile.setText(FileUtil.getPortableFilename(soundChooser.getSelectedFile().getCanonicalPath()));
            } catch (java.io.IOException e) {
                log.error("exception setting sound file", e);
            }
        }
    }

    private ScriptFileChooser scriptChooser = null;

    /**
     * Set the script file
     */
    private void setScriptPressed() {
        if (scriptChooser == null) {
            scriptChooser = new ScriptFileChooser();
        }
        scriptChooser.rescanCurrentDirectory();
        int retVal = scriptChooser.showOpenDialog(null);
        // handle selection or cancel
        if (retVal == JFileChooser.APPROVE_OPTION) {
            try {
                scriptFile.setText(FileUtil.getPortableFilename(scriptChooser.getSelectedFile().getCanonicalPath()));
            } catch (java.io.IOException e) {
                log.error("exception setting script file", e);
            }
        }
    }

    /**
     * Responds to the Add Route button.
     *
     * @param e the action event
     */
    void createPressed(ActionEvent e) {
        if (!checkNewNamesOK()) {
            return;
        }
        updatePressed();
    }

    /**
     * Responds to the Delete button.
     *
     * @param e the action event
     */
    private void deletePressed(ActionEvent e) {
        Logix l = checkNamesOK();
        if (l != null) {
            l.deActivateLogix();
            // delete the Logix and all its Conditionals
            _logixManager.deleteLogix(l);
        }
        finishUpdate();
    }

    /**
     * Update the Route Table.
     */
    private void updatePressed() {
        Logix logix = checkNamesOK();
        if (logix == null) {
            log.error("No Logix found!");
            return;
        }
        String sName = logix.getSystemName();
        // Check if the User Name has been changed
        String uName = _userName.getText();
        logix.setUserName(uName);

        initializeIncludedInputList();
        initializeIncludedOutputList();
        initializeIncludedAlignList();
        log.debug("updatePressed: _includedInputList.size()= {}, _includedOutputList.size()= {}, _includedAlignList.size()= {}",
            _includedInputList.size(), _includedOutputList.size(), _includedAlignList.size());

        ////// Construct output actions for trigger conditionals ///////////
        ArrayList<ConditionalAction> actionList = new ArrayList<>();
        for (int i = 0; i < _includedOutputList.size(); i++) {
            RouteOutputElement elt = _includedOutputList.get(i);
            String name = elt.getUserName();
            if (name == null || name.length() == 0) {
                name = elt.getSysName();
            }
            int state = elt.getState();    // actionData
            Conditional.Action actionType = Conditional.Action.NONE;
            String params = "";
            switch (elt.getType()) {
                case SENSOR_TYPE:
                    actionType = Conditional.Action.SET_SENSOR;
                    break;
                case TURNOUT_TYPE:
                    actionType = Conditional.Action.SET_TURNOUT;
                    break;
                case LIGHT_TYPE:
                    actionType = Conditional.Action.SET_LIGHT;
                    break;
                case SIGNAL_TYPE:
                    actionType = Conditional.Action.SET_SIGNAL_APPEARANCE;
                    if (state > OFFSET) {
                        actionType = Conditional.Action.getOperatorFromIntValue(state & ~OFFSET);
                    }
                    break;
                default:
                    log.debug("updatePressed: Unknown action type {}", elt.getType());
            }
            actionList.add(new DefaultConditionalAction(Conditional.ACTION_OPTION_ON_CHANGE_TO_TRUE,
                    actionType, name, state, params));
        }
        String file = scriptFile.getText();
        if (file.length() > 0) {
            actionList.add(new DefaultConditionalAction(Conditional.ACTION_OPTION_ON_CHANGE_TO_TRUE,
                    Conditional.Action.RUN_SCRIPT, "", -1, file));
        }
        file = soundFile.getText();
        if (file.length() > 0) {
            actionList.add(new DefaultConditionalAction(Conditional.ACTION_OPTION_ON_CHANGE_TO_TRUE,
                    Conditional.Action.PLAY_SOUND, "", -1, file));
        }
        ArrayList<ConditionalAction> onChangeList = cloneActionList(actionList, Conditional.ACTION_OPTION_ON_CHANGE);

        /////// Construct 'AND' clause from 'VETO' controls ////////
        ArrayList<ConditionalVariable> vetoList = new ArrayList<>();
        if (!_initialize) {
            for (int i = 0; i < _includedInputList.size(); i++) {
                RouteInputElement elt = _includedInputList.get(i);
                String name = elt.getUserName();
                if (name == null || name.length() == 0) {
                    name = elt.getSysName();
                }
                Operator opern = ( i == 0 ? Conditional.Operator.NONE : Conditional.Operator.AND);
                int state = elt.getState();
                if (VETO < state) {
                    vetoList.add(new ConditionalVariable(true, opern,
                            Conditional.Type.getOperatorFromIntValue(state & ~VETO), name, _newRouteType));
                }
            }
        }

        ///////////////// Make Trigger Conditional Controls /////////////////
        ArrayList<ConditionalVariable> oneTriggerList = new ArrayList<>();
        ArrayList<ConditionalVariable> twoTriggerList = new ArrayList<>();
        if (!_initialize) {
            for (int i = 0; i < _includedInputList.size(); i++) {
                RouteInputElement elt = _includedInputList.get(i);
                String name = elt.getUserName();
                if (name == null || name.length() == 0) {
                    name = elt.getSysName();
                }
                Operator opern = _newRouteType ? Conditional.Operator.OR : Conditional.Operator.AND;
                if (i == 0) {
                    opern = Conditional.Operator.NONE;
                }
                int type = elt.getState();
                if (VETO > type) {
                    if (Route.ONCHANGE == type) {
                        switch (elt.getType()) {
                            case SENSOR_TYPE:
                                type = Conditional.TYPE_SENSOR_ACTIVE;
                                break;
                            case TURNOUT_TYPE:
                                type = Conditional.TYPE_TURNOUT_CLOSED;
                                break;
                            case LIGHT_TYPE:
                                type = Conditional.TYPE_LIGHT_ON;
                                break;
                            case SIGNAL_TYPE:
                                type = Conditional.TYPE_SIGNAL_HEAD_LIT;
                                break;
                            default:
                                log.debug("updatePressed: Unknown state variable type {}", elt.getType());
                        }
                        twoTriggerList.add(new ConditionalVariable(false, opern,
                            Conditional.Type.getOperatorFromIntValue(type), name, true));
                    } else {
                        oneTriggerList.add(new ConditionalVariable(false, opern,
                            Conditional.Type.getOperatorFromIntValue(type), name, true));
                    }
                }
            }
            if (actionList.isEmpty()) {
                JmriJOptionPane.showMessageDialog(
                        _addFrame, rbx.getString("noAction"),
                        rbx.getString("addErr"), JmriJOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            oneTriggerList.add(new ConditionalVariable(false, Conditional.Operator.NONE,
                    Conditional.Type.NONE, getLogixInitializer(), true));
        }
        log.debug("actionList.size()= {}, oneTriggerList.size()= {}, twoTriggerList.size()= {}, "
                + "onChangeList.size()= {}, vetoList.size()= {}",
            actionList.size(), oneTriggerList.size(), twoTriggerList.size(), onChangeList.size(), vetoList.size());

        logix.deActivateLogix();

        // remove old Conditionals for actions (ver 2.5.2 only -remove a bad idea)
        char[] ch = sName.toCharArray();
        int hash = 0;
        for (int i = 0; i < ch.length; i++) {
            hash += ch[i];
        }
        String cSystemName = getConditionalSystemPrefix() + "T" + hash;
        removeConditionals(cSystemName, logix);
        cSystemName = getConditionalSystemPrefix() + "F" + hash;
        removeConditionals(cSystemName, logix);
        cSystemName = getConditionalSystemPrefix() + "A" + hash;
        removeConditionals(cSystemName, logix);
        cSystemName = getConditionalSystemPrefix() + "L" + hash;
        removeConditionals(cSystemName, logix);
        int n = 0;
        do {
            n++;
            cSystemName = sName + n + "A";
        } while (removeConditionals(cSystemName, logix));
        n = 0;
        do {
            n++;
            cSystemName = sName + n + "T";
        } while (removeConditionals(cSystemName, logix));
        cSystemName = sName + "L";
        removeConditionals(cSystemName, logix);

        int numConds = 1;
        if (_newRouteType) {
            numConds = makeRouteConditional(numConds, /*false,*/ actionList, oneTriggerList,
                    vetoList, logix, sName, uName, "T");
            if (!_initialize && !twoTriggerList.isEmpty()) {
                numConds = makeRouteConditional(numConds, /*true, actionList,*/ onChangeList, twoTriggerList,
                        null, logix, sName, uName, "T");
            }
        } else {
            for (int i = 0; i < oneTriggerList.size(); i++) {
                ArrayList<ConditionalVariable> vList = new ArrayList<>();
                vList.add(oneTriggerList.get(i));
                numConds = makeRouteConditional(numConds, /*false,*/ actionList, vList,
                        vetoList, logix, sName, uName, "T");
            }
            for (int i = 0; i < twoTriggerList.size(); i++) {
                ArrayList<ConditionalVariable> vList = new ArrayList<>();
                vList.add(twoTriggerList.get(i));
                numConds = makeRouteConditional(numConds, /*true, actionList,*/ onChangeList, vList,
                        vetoList, logix, sName, uName, "T");
            }
        }
        if (numConds == 1) {
            JmriJOptionPane.showMessageDialog(
                    _addFrame, rbx.getString("noVars"),
                    rbx.getString("addErr"), JmriJOptionPane.ERROR_MESSAGE);
            return;
        }

        ///////////////// Make Alignment Conditionals //////////////////////////
        numConds = 1;
        for (int i = 0; i < _includedAlignList.size(); i++) {
            ArrayList<ConditionalVariable> vList = new ArrayList<>();
            ArrayList<ConditionalAction> aList = new ArrayList<>();
            AlignElement sensor = _includedAlignList.get(i);
            String name = sensor.getUserName();
            if (name == null || name.length() == 0) {
                name = sensor.getSysName();
            }
            aList.add(new DefaultConditionalAction(Conditional.ACTION_OPTION_ON_CHANGE_TO_TRUE,
                    Conditional.Action.SET_SENSOR, name, Sensor.ACTIVE, ""));
            aList.add(new DefaultConditionalAction(Conditional.ACTION_OPTION_ON_CHANGE_TO_FALSE,
                    Conditional.Action.SET_SENSOR, name, Sensor.INACTIVE, ""));
            int alignType = sensor.getState();
            for (int k = 0; k < _includedOutputList.size(); k++) {
                RouteOutputElement elt = _includedOutputList.get(k);
                Conditional.Type varType = Conditional.Type.NONE;
                boolean add = (ALL_TYPE == alignType);
                switch (elt.getType()) {
                    case SENSOR_TYPE:
                        if (alignType == SENSOR_TYPE) {
                            add = true;
                        }
                        switch (elt.getState()) {
                            case Sensor.INACTIVE:
                                varType = Conditional.Type.SENSOR_INACTIVE;
                                break;
                            case Sensor.ACTIVE:
                                varType = Conditional.Type.SENSOR_ACTIVE;
                                break;
                            case Route.TOGGLE:
                                add = false;
                                break;
                            default:
                                log.warn("Unexpected state {} from elt.getState() in SENSOR_TYPE", elt.getState());
                                break;
                        }
                        break;
                    case TURNOUT_TYPE:
                        if (alignType == TURNOUT_TYPE) {
                            add = true;
                        }
                        switch (elt.getState()) {
                            case Turnout.CLOSED:
                                varType = Conditional.Type.TURNOUT_CLOSED;
                                break;
                            case Turnout.THROWN:
                                varType = Conditional.Type.TURNOUT_THROWN;
                                break;
                            case Route.TOGGLE:
                                add = false;
                                break;
                            default:
                                log.warn("Unexpected state {} from elt.getState() in TURNOUT_TYPE", elt.getState());
                                break;
                        }
                        break;
                    case LIGHT_TYPE:
                        if (alignType == LIGHT_TYPE) {
                            add = true;
                        }
                        switch (elt.getState()) {
                            case Light.ON:
                                varType = Conditional.Type.LIGHT_ON;
                                break;
                            case Light.OFF:
                                varType = Conditional.Type.LIGHT_OFF;
                                break;
                            case Route.TOGGLE:
                                add = false;
                                break;
                            default:
                                log.warn("Unexpected state {} from elt.getState() in LIGHT_TYPE", elt.getState());
                                break;
                        }
                        break;
                    case SIGNAL_TYPE:
                        if (alignType == SIGNAL_TYPE) {
                            add = true;
                        }
                        switch (elt.getState()) {
                            case SET_SIGNAL_DARK:
                            case SignalHead.DARK:
                                varType = Conditional.Type.SIGNAL_HEAD_DARK;
                                break;
                            case SignalHead.RED:
                                varType = Conditional.Type.SIGNAL_HEAD_RED;
                                break;
                            case SignalHead.FLASHRED:
                                varType = Conditional.Type.SIGNAL_HEAD_FLASHRED;
                                break;
                            case SignalHead.YELLOW:
                                varType = Conditional.Type.SIGNAL_HEAD_YELLOW;
                                break;
                            case SignalHead.FLASHYELLOW:
                                varType = Conditional.Type.SIGNAL_HEAD_FLASHYELLOW;
                                break;
                            case SignalHead.GREEN:
                                varType = Conditional.Type.SIGNAL_HEAD_GREEN;
                                break;
                            case SignalHead.FLASHGREEN:
                                varType = Conditional.Type.SIGNAL_HEAD_FLASHGREEN;
                                break;
                            case SET_SIGNAL_HELD:
                                varType = Conditional.Type.SIGNAL_HEAD_HELD;
                                break;
                            case CLEAR_SIGNAL_HELD:
                                add = false;    // don't know how to test for this
                                break;
                            case SET_SIGNAL_LIT:
                                varType = Conditional.Type.SIGNAL_HEAD_LIT;
                                break;
                            default:
                                log.warn("Unexpected state {} from elt.getState() in SIGNAL_TYPE", elt.getState());
                                break;
                        }
                        break;
                    default:
                        log.debug("updatePressed: Unknown Alignment state variable type {}", elt.getType());
                }
                if (add && !_initialize) {
                    String eltName = elt.getUserName();
                    if (eltName == null || eltName.length() == 0) {
                        eltName = elt.getSysName();
                    }
                    vList.add(new ConditionalVariable(false, Conditional.Operator.AND,
                            varType, eltName, true));
                }
            }
            if (!vList.isEmpty()) {
                numConds = makeAlignConditional(numConds, aList, vList, logix, sName, uName);
            } else {
                JmriJOptionPane.showMessageDialog(
                        _addFrame, java.text.MessageFormat.format(rbx.getString("NoAlign"),
                                new Object[]{name, sensor.getAlignType()}),
                        Bundle.getMessage("WarningTitle"), JmriJOptionPane.WARNING_MESSAGE);
            }
        }
        ///////////////// Make Lock Conditional //////////////////////////
        if (_lock) {
            ArrayList<ConditionalAction> aList = new ArrayList<>();
            for (int k = 0; k < _includedOutputList.size(); k++) {
                RouteOutputElement elt = _includedOutputList.get(k);
                if (elt.getType() != TURNOUT_TYPE || elt.getState() == Route.TOGGLE) {
                    continue;
                }
                String eltName = elt.getUserName();
                if (eltName == null || eltName.length() == 0) {
                    eltName = elt.getSysName();
                }
                aList.add(new DefaultConditionalAction(Conditional.ACTION_OPTION_ON_CHANGE_TO_TRUE,
                        Conditional.Action.LOCK_TURNOUT,
                        eltName, Turnout.LOCKED, ""));
                aList.add(new DefaultConditionalAction(Conditional.ACTION_OPTION_ON_CHANGE_TO_FALSE,
                        Conditional.Action.LOCK_TURNOUT,
                        eltName, Turnout.UNLOCKED, ""));
            }
            makeRouteConditional(numConds, /*false,*/ aList, oneTriggerList,
                    vetoList, logix, sName, uName, "L");
        }
        log.debug("Conditionals added= {}", logix.getNumConditionals());
        for (int i = 0; i < logix.getNumConditionals(); i++) {
            log.debug("Conditional SysName= \"{}\"", logix.getConditionalByNumberOrder(i));
        }
        logix.activateLogix();
        log.debug("Conditionals added= {}", logix.getNumConditionals());
        for (int i = 0; i < logix.getNumConditionals(); i++) {
            log.debug("Conditional SysName= \"{}\"", logix.getConditionalByNumberOrder(i));
        }
        finishUpdate();
    } //updatePressed

    private boolean removeConditionals(String cSystemName, Logix logix) {
        Conditional c = _conditionalManager.getBySystemName(cSystemName);
        if (c != null) {
            logix.deleteConditional(cSystemName);
            return true;
        }
        return false;
    }

    /**
     * Create a new Route conditional.
     *
     * @param numConds number of existing route conditionals
     * @param actionList actions to take in conditional
     * @param triggerList triggers for conditional to take actions
     * @param vetoList controls that veto taking actions
     * @param logix Logix to add the conditional to
     * @param sName system name for conditional
     * @param uName user name for conditional
     * @param type type of conditional
     * @return number of conditionals after the creation
     * @throws IllegalArgumentException if "user input no good"
     */
    private int makeRouteConditional(int numConds, /*boolean onChange,*/ ArrayList<ConditionalAction> actionList,
            ArrayList<ConditionalVariable> triggerList, ArrayList<ConditionalVariable> vetoList,
            Logix logix, String sName, String uName, String type) {
        if (log.isDebugEnabled()) {
            log.debug("makeRouteConditional: numConds= {}, triggerList.size()= {}", numConds, triggerList.size());
        }
        if (triggerList.isEmpty() && (vetoList == null || vetoList.isEmpty())) {
            return numConds;
        }
        StringBuilder antecedent = new StringBuilder();
        ArrayList<ConditionalVariable> varList = new ArrayList<>();

        int tSize = triggerList.size();
        if (tSize > 0) {
            if (tSize > 1) {
                antecedent.append("(");
            }
            antecedent.append("R1"); // NOI18N
            for (int i = 1; i < tSize; i++) {
                antecedent.append(" ").append(Bundle.getMessage("LogicOR")).append(" R").append(i + 1); // NOI18N
            }
            if (tSize > 1) {
                antecedent.append(")");
            }
            for (int i = 0; i < triggerList.size(); i++) {
                //varList.add(cloneVariable(triggerList.get(i)));
                varList.add(triggerList.get(i));
            }
        }

        if (vetoList != null && !vetoList.isEmpty()) {
            int vSize = vetoList.size();
            if (tSize > 0) {
                antecedent.append(" ").append(Bundle.getMessage("LogicAND")).append(" ");
            }
            if (vSize > 1) {
                antecedent.append("(");
            }
            antecedent.append(Bundle.getMessage("LogicNOT")).append(" R").append(1 + tSize); // NOI18N
            for (int i = 1; i < vSize; i++) {
                antecedent.append(" ").append(Bundle.getMessage("LogicAND")).append(" ").append(Bundle.getMessage("LogicNOT")).append(" R").append(i + 1 + tSize); // NOI18N
            }
            if (vSize > 1) {
                antecedent.append(")");
            }
            for (int i = 0; i < vetoList.size(); i++) {
                //varList.add(cloneVariable(vetoList.get(i)));
                varList.add(vetoList.get(i));
            }
        }
        String cSystemName = sName + numConds + type;
        String cUserName = CONDITIONAL_USER_PREFIX + numConds + "C " + uName;
        Conditional c = null;
        try {
            c = _conditionalManager.createNewConditional(cSystemName, cUserName);
        } catch (Exception ex) {
            // user input no good
            handleCreateException(sName);
            // throw without creating any
            throw new IllegalArgumentException("user input no good");
        }
        c.setStateVariables(varList);
        //int option = onChange ? Conditional.ACTION_OPTION_ON_CHANGE : Conditional.ACTION_OPTION_ON_CHANGE_TO_TRUE;
        //c.setAction(cloneActionList(actionList, option));
        c.setAction(actionList);
        Conditional.AntecedentOperator logicType =
                _newRouteType
                ? Conditional.AntecedentOperator.MIXED
                : Conditional.AntecedentOperator.ALL_AND;
        c.setLogicType(logicType, antecedent.toString());
        logix.addConditional(cSystemName, 0);
        log.debug("Conditional added: SysName= \"{}\"", cSystemName);
        c.calculate(true, null);

        return numConds + 1;
    }

    private void handleCreateException(String sysName) {
        JmriJOptionPane.showMessageDialog(_addFrame,
                Bundle.getMessage("ErrorLRouteAddFailed", sysName) + "\n" + Bundle.getMessage("ErrorAddFailedCheck"),
                Bundle.getMessage("ErrorTitle"),
                JmriJOptionPane.ERROR_MESSAGE);
    }

    /**
     * Create a new alignment conditional.
     *
     * @param numConds number of existing route conditionals
     * @param actionList actions to take in conditional
     * @param triggerList triggers for conditional to take actions
     * @param logix Logix to add the conditional to
     * @param sName system name for conditional
     * @param uName user name for conditional
     * @return number of conditionals after the creation
     * @throws IllegalArgumentException if "user input no good"
     */
    private int makeAlignConditional(int numConds, ArrayList<ConditionalAction> actionList,
            ArrayList<ConditionalVariable> triggerList,
            Logix logix, String sName, String uName) {
        if (triggerList.isEmpty()) {
            return numConds;
        }
        String cSystemName = sName + numConds + "A";
        String cUserName = CONDITIONAL_USER_PREFIX + numConds + "A " + uName;
        Conditional c = null;
        try {
            c = _conditionalManager.createNewConditional(cSystemName, cUserName);
        } catch (Exception ex) {
            // user input no good
            handleCreateException(sName);
            // throw without creating any
            throw new IllegalArgumentException("user input no good");
        }
        c.setStateVariables(triggerList);
        //c.setAction(cloneActionList(actionList, Conditional.ACTION_OPTION_ON_CHANGE_TO_TRUE));
        c.setAction(actionList);
        c.setLogicType(Conditional.AntecedentOperator.ALL_AND, "");
        logix.addConditional(cSystemName, 0);
        log.debug("Conditional added: SysName= \"{}\"", cSystemName);
        c.calculate(true, null);
        return numConds+1;
    }

    @Nonnull
    private ArrayList<ConditionalAction> cloneActionList(ArrayList<ConditionalAction> actionList, int option) {
        ArrayList<ConditionalAction> list = new ArrayList<>();
        for (int i = 0; i < actionList.size(); i++) {
            ConditionalAction action = actionList.get(i);
            ConditionalAction clone = new DefaultConditionalAction();
            clone.setType(action.getType());
            clone.setOption(option);
            clone.setDeviceName(action.getDeviceName());
            clone.setActionData(action.getActionData());
            clone.setActionString(action.getActionString());
            list.add(clone);
        }
        return list;
    }

    private void finishUpdate() {
        routeDirty = true;
        clearPage();
    }

    private void clearPage() {
        // move to show all turnouts if not there
        cancelIncludedOnly();
        deleteButton.setVisible(false);
        cancelButton.setVisible(false);
        updateButton.setVisible(false);
        createButton.setVisible(false);
        _systemName.setText("");
        _userName.setText("");
        soundFile.setText("");
        scriptFile.setText("");
        for (int i = _inputList.size() - 1; i >= 0; i--) {
            _inputList.get(i).setIncluded(false);
        }
        for (int i = _outputList.size() - 1; i >= 0; i--) {
            _outputList.get(i).setIncluded(false);
        }
        for (int i = _alignList.size() - 1; i >= 0; i--) {
            _alignList.get(i).setIncluded(false);
        }
        _lock = false;
        _newRouteType = true;
        _newRouteButton.doClick();
        _lockCheckBox.setSelected(_lock);
        if (routeDirty) {
            showReminderMessage();
            routeDirty = false;
        }
        _addFrame.setVisible(false);
    }

    /**
     * Cancels included only option
     */
    private void cancelIncludedOnly() {
        if (!_showAllInput) {
            _inputAllButton.doClick();
        }
        if (!_showAllOutput) {
            _outputAllButton.doClick();
        }
        if (!_showAllAlign) {
            _alignAllButton.doClick();
        }
    }

////////////////////////////// Internal Utility Classes ////////////////////////////////
    private class ComboBoxCellEditor extends DefaultCellEditor {

        ComboBoxCellEditor() {
            super(new JComboBox<String>());
        }

        ComboBoxCellEditor(JComboBox<String> comboBox) {
            super(comboBox);
        }

        @SuppressWarnings("unchecked") // getComponent call requires an unchecked cast
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {

            RouteElementModel model = (RouteElementModel) table.getModel();

            RouteElement elt;
            String[] items;
            if (model.isInput()) {
                if (_showAllInput) {
                    elt = _inputList.get(row);
                } else {
                    elt = _includedInputList.get(row);
                }
                items = getInputComboBoxItems(elt.getType());
            } else {
                if (_showAllOutput) {
                    elt = _outputList.get(row);
                } else {
                    elt = _includedOutputList.get(row);
                }
                items = getOutputComboBoxItems(elt.getType());
            }
            JComboBox<String> comboBox = (JComboBox<String>) getComponent();
            comboBox.removeAllItems();
            for (String item : items) {
                comboBox.addItem(item);
            }
            return super.getTableCellEditorComponent(table, value, isSelected, row, column);
        }

        private String[] getInputComboBoxItems(int type) {
            switch (type) {
                case SENSOR_TYPE:
                    return INPUT_SENSOR_STATES;
                case TURNOUT_TYPE:
                    return INPUT_TURNOUT_STATES;
                case LIGHT_TYPE:
                    return INPUT_LIGHT_STATES;
                case SIGNAL_TYPE:
                    return INPUT_SIGNAL_STATES;
                default:
                    log.warn("Unhandled object type: {}", type);
                    break;
            }
            return new String[]{};
        }

        private String[] getOutputComboBoxItems(int type) {
            switch (type) {
                case SENSOR_TYPE:
                    return OUTPUT_SENSOR_STATES;
                case TURNOUT_TYPE:
                    return OUTPUT_TURNOUT_STATES;
                case LIGHT_TYPE:
                    return OUTPUT_LIGHT_STATES;
                case SIGNAL_TYPE:
                    return OUTPUT_SIGNAL_STATES;
                default:
                    log.warn("Unhandled type: {}", type);
            }
            return new String[]{};
        }

    }

    /**
     * Base Table model for selecting Route elements
     */
    abstract class RouteElementModel extends AbstractTableModel implements PropertyChangeListener {

        abstract boolean isInput();

        @Override
        public Class<?> getColumnClass(int c) {
            if (c == INCLUDE_COLUMN) {
                return Boolean.class;
            } else {
                return String.class;
            }
        }

        @Override
        public int getColumnCount() {
            return 5;
        }

        @Override
        public String getColumnName(int c) {
            switch (c) {
                case SNAME_COLUMN:
                    return Bundle.getMessage("ColumnSystemName");
                case UNAME_COLUMN:
                    return Bundle.getMessage("ColumnUserName");
                case TYPE_COLUMN:
                    return rbx.getString("Type");
                case INCLUDE_COLUMN:
                    return Bundle.getMessage("Include");
                default:
                    log.warn("Unhandled column type: {}", c);
                    break;
            }
            return "";
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return ((c == INCLUDE_COLUMN) || (c == STATE_COLUMN));
        }

        @Override
        public void propertyChange(java.beans.PropertyChangeEvent e) {
            if ( Manager.PROPERTY_LENGTH.equals( e.getPropertyName())) {
                // a new NamedBean is available in the manager
                fireTableDataChanged();
            }
        }

        public void dispose() {
            InstanceManager.turnoutManagerInstance().removePropertyChangeListener(this);
        }

        public static final int SNAME_COLUMN = 0;
        public static final int UNAME_COLUMN = 1;
        public static final int TYPE_COLUMN = 2;
        public static final int INCLUDE_COLUMN = 3;
        public static final int STATE_COLUMN = 4;
    }

    /**
     * Table model for selecting input variables
     */
    private class RouteInputModel extends RouteElementModel {

        @Override
        public boolean isInput() {
            return true;
        }

        @Override
        public String getColumnName(int c) {
            if (c == STATE_COLUMN) {
                return rbx.getString("SetTrigger");
            }
            return super.getColumnName(c);
        }

        @Override
        public int getRowCount() {
            if (_showAllInput) {
                return _inputList.size();
            } else {
                return _includedInputList.size();
            }
        }

        @Override
        public Object getValueAt(int r, int c) {
            ArrayList<RouteInputElement> inputList;
            if (_showAllInput) {
                inputList = _inputList;
            } else {
                inputList = _includedInputList;
            }
            // some error checking
            if (r >= inputList.size()) {
                log.debug("row out of range");
                return null;
            }
            switch (c) {
                case SNAME_COLUMN:
                    return inputList.get(r).getSysName();
                case UNAME_COLUMN:
                    return inputList.get(r).getUserName();
                case TYPE_COLUMN:
                    return inputList.get(r).getTypeString();
                case INCLUDE_COLUMN:
                    return inputList.get(r).isIncluded();
                case STATE_COLUMN:
                    return inputList.get(r).getTestState();
                default:
                    return null;
            }
        }

        @Override
        public void setValueAt(Object type, int r, int c) {
            ArrayList<RouteInputElement> inputList;
            if (_showAllInput) {
                inputList = _inputList;
            } else {
                inputList = _includedInputList;
            }
            switch (c) {
                case INCLUDE_COLUMN:
                    inputList.get(r).setIncluded(((Boolean) type));
                    break;
                case STATE_COLUMN:
                    inputList.get(r).setTestState((String) type);
                    break;
                default:
                    log.warn("Unexpected column {} in setValueAt", c);
                    break;
            }
        }
    }

    /**
     * Table model for selecting output variables
     */
    private class RouteOutputModel extends RouteElementModel {

        @Override
        public boolean isInput() {
            return false;
        }

        @Override
        public String getColumnName(int c) {
            if (c == STATE_COLUMN) {
                return rbx.getString("SetAction");
            }
            return super.getColumnName(c);
        }

        @Override
        public int getRowCount() {
            if (_showAllOutput) {
                return _outputList.size();
            } else {
                return _includedOutputList.size();
            }
        }

        @Override
        public Object getValueAt(int r, int c) {
            ArrayList<RouteOutputElement> outputList;
            if (_showAllOutput) {
                outputList = _outputList;
            } else {
                outputList = _includedOutputList;
            }
            // some error checking
            if (r >= outputList.size()) {
                log.debug("row out of range");
                return null;
            }
            switch (c) {
                case SNAME_COLUMN:  // slot number
                    return outputList.get(r).getSysName();
                case UNAME_COLUMN:  //
                    return outputList.get(r).getUserName();
                case INCLUDE_COLUMN:
                    return outputList.get(r).isIncluded();
                case TYPE_COLUMN:
                    return outputList.get(r).getTypeString();
                case STATE_COLUMN:  //
                    return outputList.get(r).getSetToState();
                default:
                    return null;
            }
        }

        @Override
        public void setValueAt(Object type, int r, int c) {
            ArrayList<RouteOutputElement> outputList;
            if (_showAllOutput) {
                outputList = _outputList;
            } else {
                outputList = _includedOutputList;
            }
            switch (c) {
                case INCLUDE_COLUMN:
                    outputList.get(r).setIncluded(((Boolean) type));
                    break;
                case STATE_COLUMN:
                    outputList.get(r).setSetToState((String) type);
                    break;
                default:
                    log.warn("Unexpected column {} in setValueAt", c);
                    break;
            }
        }
    }

    /**
     * Table model for selecting output variables
     */
    private class AlignmentModel extends RouteElementModel {

        @Override
        public boolean isInput() {
            return false;
        }

        @Override
        public String getColumnName(int c) {
            if (c == STATE_COLUMN) {
                return rbx.getString("Alignment");
            }
            return super.getColumnName(c);
        }

        @Override
        public int getRowCount() {
            if (_showAllAlign) {
                return _alignList.size();
            } else {
                return _includedAlignList.size();
            }
        }

        @Override
        public Object getValueAt(int r, int c) {
            ArrayList<AlignElement> alignList;
            if (_showAllAlign) {
                alignList = _alignList;
            } else {
                alignList = _includedAlignList;
            }
            // some error checking
            if (r >= alignList.size()) {
                log.debug("row out of range");
                return null;
            }
            switch (c) {
                case SNAME_COLUMN:  // slot number
                    return alignList.get(r).getSysName();
                case UNAME_COLUMN:  //
                    return alignList.get(r).getUserName();
                case INCLUDE_COLUMN:
                    return alignList.get(r).isIncluded();
                case TYPE_COLUMN:
                    return Bundle.getMessage("BeanNameSensor");
                case STATE_COLUMN:  //
                    return alignList.get(r).getAlignType();
                default:
                    return null;
            }
        }

        @Override
        public void setValueAt(Object type, int r, int c) {
            ArrayList<AlignElement> alignList;
            if (_showAllAlign) {
                alignList = _alignList;
            } else {
                alignList = _includedAlignList;
            }
            switch (c) {
                case INCLUDE_COLUMN:
                    alignList.get(r).setIncluded(((Boolean) type));
                    break;
                case STATE_COLUMN:
                    alignList.get(r).setAlignType((String) type);
                    break;
                default:
                    log.warn("Unexpected column {} in setValueAt", c);
                    break;
            }
        }
    }

    public static final String CONDITIONAL_USER_PREFIX = "Route ";

    public static final int SENSOR_TYPE = 1;
    public static final int TURNOUT_TYPE = 2;
    public static final int LIGHT_TYPE = 3;
    public static final int SIGNAL_TYPE = 4;
    public static final int CONDITIONAL_TYPE = 5;
    public static final int ALL_TYPE = 6;

    // Should not conflict with state variable types
    public static final int VETO = 0x80;
    // due to the unecessary bit assignments in SignalHead for appearances,
    // offset the following
    public static final int OFFSET = 0x30;
    public static final int SET_SIGNAL_HELD = Conditional.ACTION_SET_SIGNAL_HELD + OFFSET;
    public static final int CLEAR_SIGNAL_HELD = Conditional.ACTION_CLEAR_SIGNAL_HELD + OFFSET;
    public static final int SET_SIGNAL_DARK = Conditional.ACTION_SET_SIGNAL_DARK + OFFSET;
    public static final int SET_SIGNAL_LIT = Conditional.ACTION_SET_SIGNAL_LIT + OFFSET;

    private static final String ALIGN_SENSOR = rbx.getString("AlignSensor");
    private static final String ALIGN_TURNOUT = rbx.getString("AlignTurnout");
    private static final String ALIGN_LIGHT = rbx.getString("AlignLight");
    private static final String ALIGN_SIGNAL = rbx.getString("AlignSignal");
    private static final String ALIGN_ALL = rbx.getString("AlignAll");

    private static final String ON_CHANGE = Bundle.getMessage("OnConditionChange");
    private static final String ON_ACTIVE = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("SensorStateActive");
    private static final String ON_INACTIVE = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("SensorStateInactive");
    private static final String VETO_ON_ACTIVE = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("SensorStateActive");
    private static final String VETO_ON_INACTIVE = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("SensorStateInactive");
    private static final String ON_THROWN = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("TurnoutStateThrown");
    private static final String ON_CLOSED = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("TurnoutStateClosed");
    private static final String VETO_ON_THROWN = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("TurnoutStateThrown");
    private static final String VETO_ON_CLOSED = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("TurnoutStateClosed");
    private static final String ON_LIT = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("ColumnHeadLit");
    private static final String ON_UNLIT = rbx.getString("OnUnLit");
    private static final String VETO_ON_LIT = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("ColumnHeadLit");
    private static final String VETO_ON_UNLIT = rbx.getString("VetoUnLit");
    private static final String ON_RED = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("SignalHeadStateRed");
    private static final String ON_FLASHRED = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("SignalHeadStateFlashingRed");
    private static final String ON_YELLOW = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("SignalHeadStateYellow");
    private static final String ON_FLASHYELLOW = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("SignalHeadStateFlashingYellow");
    private static final String ON_GREEN = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("SignalHeadStateGreen");
    private static final String ON_FLASHGREEN = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("SignalHeadStateFlashingGreen");
    private static final String ON_DARK = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("SignalHeadStateDark");
    private static final String ON_SIGNAL_LIT = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("ColumnHeadLit");
    private static final String ON_SIGNAL_HELD = Bundle.getMessage("OnCondition")
        + " " + Bundle.getMessage("SignalHeadStateHeld");
    private static final String VETO_ON_RED = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("SignalHeadStateRed");
    private static final String VETO_ON_FLASHRED = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("SignalHeadStateFlashingRed");
    private static final String VETO_ON_YELLOW = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("SignalHeadStateYellow");
    private static final String VETO_ON_FLASHYELLOW = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("SignalHeadStateFlashingYellow");
    private static final String VETO_ON_GREEN = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("SignalHeadStateGreen");
    private static final String VETO_ON_FLASHGREEN = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("SignalHeadStateFlashingGreen");
    private static final String VETO_ON_DARK = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("SignalHeadStateDark");
    private static final String VETO_ON_SIGNAL_LIT = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("ColumnHeadLit");
    private static final String VETO_ON_SIGNAL_HELD = "Veto " + Bundle.getMessage("WhenCondition")
        + " " + Bundle.getMessage("SignalHeadStateHeld");

    private static final String SET_TO_ACTIVE = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameSensor"), Bundle.getMessage("SensorStateActive"));
    private static final String SET_TO_INACTIVE = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameSensor"), Bundle.getMessage("SensorStateInactive"));
    private static final String SET_TO_CLOSED = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameTurnout"), Bundle.getMessage("TurnoutStateClosed"));
    private static final String SET_TO_THROWN = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameTurnout"), Bundle.getMessage("TurnoutStateThrown"));
    private static final String SET_TO_TOGGLE = Bundle.getMessage("SetBeanState",
        "", Bundle.getMessage("Toggle"));
    private static final String SET_TO_ON = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameLight"), Bundle.getMessage("StateOn"));
    private static final String SET_TO_OFF = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameLight"), Bundle.getMessage("StateOff"));
    private static final String SET_TO_DARK = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameSignalHead"), Bundle.getMessage("SignalHeadStateDark"));
    private static final String SET_TO_LIT = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameSignalHead"), Bundle.getMessage("ColumnHeadLit"));
    private static final String SET_TO_HELD = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameSignalHead"), Bundle.getMessage("SignalHeadStateHeld"));
    private static final String SET_TO_CLEAR = rbx.getString("SetClear");
    private static final String SET_TO_RED = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameSignalHead"), Bundle.getMessage("SignalHeadStateRed"));
    private static final String SET_TO_FLASHRED = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameSignalHead"), Bundle.getMessage("SignalHeadStateFlashingRed"));
    private static final String SET_TO_YELLOW = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameSignalHead"), Bundle.getMessage("SignalHeadStateYellow"));
    private static final String SET_TO_FLASHYELLOW = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameSignalHead"), Bundle.getMessage("SignalHeadStateFlashingYellow"));
    private static final String SET_TO_GREEN = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameSignalHead"), Bundle.getMessage("SignalHeadStateGreen"));
    private static final String SET_TO_FLASHGREEN = Bundle.getMessage("SetBeanState",
        Bundle.getMessage("BeanNameSignalHead"), Bundle.getMessage("SignalHeadStateFlashingGreen"));

    private static final String[] ALIGNMENT_STATES = {
        ALIGN_SENSOR, ALIGN_TURNOUT, ALIGN_LIGHT, ALIGN_SIGNAL, ALIGN_ALL};
    private static final String[] INPUT_SENSOR_STATES = {
        ON_ACTIVE, ON_INACTIVE, ON_CHANGE, VETO_ON_ACTIVE, VETO_ON_INACTIVE};
    private static final String[] INPUT_TURNOUT_STATES = {
        ON_THROWN, ON_CLOSED, ON_CHANGE, VETO_ON_THROWN, VETO_ON_CLOSED};
    private static final String[] INPUT_LIGHT_STATES = {
        ON_LIT, ON_UNLIT, ON_CHANGE, VETO_ON_LIT, VETO_ON_UNLIT};
    private static final String[] INPUT_SIGNAL_STATES = {
        ON_RED, ON_FLASHRED, ON_YELLOW, ON_FLASHYELLOW, ON_GREEN,
        ON_FLASHGREEN, ON_DARK, ON_SIGNAL_LIT, ON_SIGNAL_HELD, VETO_ON_RED,
        VETO_ON_FLASHRED, VETO_ON_YELLOW, VETO_ON_FLASHYELLOW, VETO_ON_GREEN,
        VETO_ON_FLASHGREEN, VETO_ON_DARK, VETO_ON_SIGNAL_LIT, VETO_ON_SIGNAL_HELD};
    private static final String[] OUTPUT_SENSOR_STATES = {SET_TO_ACTIVE, SET_TO_INACTIVE, SET_TO_TOGGLE};
    private static final String[] OUTPUT_TURNOUT_STATES = {SET_TO_CLOSED, SET_TO_THROWN, SET_TO_TOGGLE};
    private static final String[] OUTPUT_LIGHT_STATES = {SET_TO_ON, SET_TO_OFF, SET_TO_TOGGLE};
    private static final String[] OUTPUT_SIGNAL_STATES = {SET_TO_DARK, SET_TO_LIT, SET_TO_HELD, SET_TO_CLEAR,
        SET_TO_RED, SET_TO_FLASHRED, SET_TO_YELLOW,
        SET_TO_FLASHYELLOW, SET_TO_GREEN, SET_TO_FLASHGREEN};

    private static String getLogixSystemPrefix() {
        // Note: RouteExportToLogix uses ":RTX:" which is right?
        return InstanceManager.getDefault(LogixManager.class).getSystemNamePrefix() + ":RTX";
    }

    // should be private or package protected, but hey, its Logix! so its public
    // because Logix is scattered across all of JMRI without rhyme or reason
    public static String getLogixInitializer() {
        return getLogixSystemPrefix() + "INITIALIZER";
    }

    private String getConditionalSystemPrefix() {
        return getLogixSystemPrefix() + "C";
    }

    /**
     * Sorts RouteElement
     */
    public static class RouteElementComparator implements java.util.Comparator<RouteElement> {
        // RouteElement objects aren't really NamedBeans, as they don't inherit
        // so we have to create our own comparator object here.  This assumes they
        // the do have a named-bean-like system name format.
        RouteElementComparator() {
        }

        private static final jmri.util.AlphanumComparator ac = new jmri.util.AlphanumComparator();

        @Override
        public int compare(RouteElement e1, RouteElement e2) {
            String s1 = e1.getSysName();
            String s2 = e2.getSysName();

            int p1len = Manager.getSystemPrefixLength(s1);
            int p2len = Manager.getSystemPrefixLength(s2);

            int comp = ac.compare(s1.substring(0, p1len), s2.substring(0, p2len));
            if (comp != 0) {
                return comp;
            }

            char c1 = s1.charAt(p1len);
            char c2 = s2.charAt(p2len);

            if (c1 == c2) {
                return ac.compare(s1.substring(p1len+1), s2.substring(p2len+1));
            } else {
                return (c1 > c2) ? +1 : -1 ;
            }
        }

    }

    /**
     * Base class for all the output (ConditionalAction) and input
     * (ConditionalVariable) elements
     */
    static class RouteElement {

        private String _sysName;
        private String _userName;
        private int _type;
        private String _typeString;
        private boolean _included;
        int _state;

        RouteElement(String sysName, String userName, int type) {
            _sysName = sysName;
            _userName = userName;
            _type = type;
            _included = false;
            switch (type) {
                case SENSOR_TYPE:
                    _typeString = Bundle.getMessage("BeanNameSensor");
                    break;
                case TURNOUT_TYPE:
                    _typeString = Bundle.getMessage("BeanNameTurnout");
                    break;
                case LIGHT_TYPE:
                    _typeString = Bundle.getMessage("BeanNameLight");
                    break;
                case SIGNAL_TYPE:
                    _typeString = Bundle.getMessage("BeanNameSignalHead");
                    break;
                case CONDITIONAL_TYPE:
                    _typeString = Bundle.getMessage("BeanNameConditional");
                    break;
                default:
                    log.warn("Unexpected type {} in RouteElement constructor", type);
                    break;
            }
        }

        String getSysName() {
            return _sysName;
        }

        String getUserName() {
            return _userName;
        }

        int getType() {
            return _type;
        }

        String getTypeString() {
            return _typeString;
        }

        boolean isIncluded() {
            return _included;
        }

        void setIncluded(boolean include) {
            _included = include;
        }

        int getState() {
            return _state;
        }

        final void setState(int state) {
            _state = state;
        }
    }

    abstract class RouteInputElement extends RouteElement {

        RouteInputElement(String sysName, String userName, int type) {
            super(sysName, userName, type);
        }

        abstract String getTestState();

        abstract void setTestState(String state);
    }

    private class RouteInputSensor extends RouteInputElement {

        RouteInputSensor(String sysName, String userName) {
            super(sysName, userName, SENSOR_TYPE);
            setState(Conditional.TYPE_SENSOR_ACTIVE);
        }

        @Override
        String getTestState() {
            switch (_state) {
                case Conditional.TYPE_SENSOR_INACTIVE:
                    return ON_INACTIVE;
                case Conditional.TYPE_SENSOR_ACTIVE:
                    return ON_ACTIVE;
                case Route.ONCHANGE:
                    return ON_CHANGE;
                case VETO + Conditional.TYPE_SENSOR_INACTIVE:
                    return VETO_ON_INACTIVE;
                case VETO + Conditional.TYPE_SENSOR_ACTIVE:
                    return VETO_ON_ACTIVE;
                default:
                    log.error("Unhandled test state type: {}", _state);
                    break;
            }
            return "";
        }

        @Override
        void setTestState(String state) {
            if (ON_INACTIVE.equals(state)) {
                _state = Conditional.TYPE_SENSOR_INACTIVE;
            } else if (ON_ACTIVE.equals(state)) {
                _state = Conditional.TYPE_SENSOR_ACTIVE;
            } else if (ON_CHANGE.equals(state)) {
                _state = Route.ONCHANGE;
            } else if (VETO_ON_INACTIVE.equals(state)) {
                _state = VETO + Conditional.TYPE_SENSOR_INACTIVE;
            } else if (VETO_ON_ACTIVE.equals(state)) {
                _state = VETO + Conditional.TYPE_SENSOR_ACTIVE;
            }
        }
    }

    private class RouteInputTurnout extends RouteInputElement {

        RouteInputTurnout(String sysName, String userName) {
            super(sysName, userName, TURNOUT_TYPE);
            setState(Conditional.TYPE_TURNOUT_CLOSED);
        }

        @Override
        String getTestState() {
            switch (_state) {
                case Conditional.TYPE_TURNOUT_CLOSED:
                    return ON_CLOSED;
                case Conditional.TYPE_TURNOUT_THROWN:
                    return ON_THROWN;
                case Route.ONCHANGE:
                    return ON_CHANGE;
                case VETO + Conditional.TYPE_TURNOUT_CLOSED:
                    return VETO_ON_CLOSED;
                case VETO + Conditional.TYPE_TURNOUT_THROWN:
                    return VETO_ON_THROWN;
                default:
                    log.warn("Unhandled test state type: {}", _state);
            }
            return "";
        }

        @Override
        void setTestState(String state) {
            if (ON_CLOSED.equals(state)) {
                _state = Conditional.TYPE_TURNOUT_CLOSED;
            } else if (ON_THROWN.equals(state)) {
                _state = Conditional.TYPE_TURNOUT_THROWN;
            } else if (ON_CHANGE.equals(state)) {
                _state = Route.ONCHANGE;
            } else if (VETO_ON_CLOSED.equals(state)) {
                _state = VETO + Conditional.TYPE_TURNOUT_CLOSED;
            } else if (VETO_ON_THROWN.equals(state)) {
                _state = VETO + Conditional.TYPE_TURNOUT_THROWN;
            }
        }
    }

    private class RouteInputLight extends RouteInputElement {

        RouteInputLight(String sysName, String userName) {
            super(sysName, userName, LIGHT_TYPE);
            setState(Conditional.TYPE_LIGHT_OFF);
        }

        @Override
        String getTestState() {
            switch (_state) {
                case Conditional.TYPE_LIGHT_OFF:
                    return ON_UNLIT;
                case Conditional.TYPE_LIGHT_ON:
                    return ON_LIT;
                case Route.ONCHANGE:
                    return ON_CHANGE;
                case VETO + Conditional.TYPE_LIGHT_OFF:
                    return VETO_ON_UNLIT;
                case VETO + Conditional.TYPE_LIGHT_ON:
                    return VETO_ON_LIT;
                default:
                    log.warn("Unhandled test state: {}", _state);
                    break;
            }
            return "";
        }

        @Override
        void setTestState(String state) {
            if (ON_UNLIT.equals(state)) {
                _state = Conditional.TYPE_LIGHT_OFF;
            } else if (ON_LIT.equals(state)) {
                _state = Conditional.TYPE_LIGHT_ON;
            } else if (ON_CHANGE.equals(state)) {
                _state = Route.ONCHANGE;
            } else if (VETO_ON_UNLIT.equals(state)) {
                _state = VETO + Conditional.TYPE_LIGHT_OFF;
            } else if (VETO_ON_LIT.equals(state)) {
                _state = VETO + Conditional.TYPE_LIGHT_ON;
            }
        }
    }

    private class RouteInputSignal extends RouteInputElement {

        RouteInputSignal(String sysName, String userName) {
            super(sysName, userName, SIGNAL_TYPE);
            setState(Conditional.TYPE_SIGNAL_HEAD_LIT);
        }

        @Override
        String getTestState() {
            switch (_state) {
                case Conditional.TYPE_SIGNAL_HEAD_RED:
                    return ON_RED;
                case Conditional.TYPE_SIGNAL_HEAD_FLASHRED:
                    return ON_FLASHRED;
                case Conditional.TYPE_SIGNAL_HEAD_YELLOW:
                    return ON_YELLOW;
                case Conditional.TYPE_SIGNAL_HEAD_FLASHYELLOW:
                    return ON_FLASHYELLOW;
                case Conditional.TYPE_SIGNAL_HEAD_GREEN:
                    return ON_GREEN;
                case Conditional.TYPE_SIGNAL_HEAD_FLASHGREEN:
                    return ON_FLASHGREEN;
                case Conditional.TYPE_SIGNAL_HEAD_DARK:
                    return ON_DARK;
                case Conditional.TYPE_SIGNAL_HEAD_LIT:
                    return ON_SIGNAL_LIT;
                case Conditional.TYPE_SIGNAL_HEAD_HELD:
                    return ON_SIGNAL_HELD;
                case VETO + Conditional.TYPE_SIGNAL_HEAD_RED:
                    return VETO_ON_RED;
                case VETO + Conditional.TYPE_SIGNAL_HEAD_FLASHRED:
                    return VETO_ON_FLASHRED;
                case VETO + Conditional.TYPE_SIGNAL_HEAD_YELLOW:
                    return VETO_ON_YELLOW;
                case VETO + Conditional.TYPE_SIGNAL_HEAD_FLASHYELLOW:
                    return VETO_ON_FLASHYELLOW;
                case VETO + Conditional.TYPE_SIGNAL_HEAD_GREEN:
                    return VETO_ON_GREEN;
                case VETO + Conditional.TYPE_SIGNAL_HEAD_FLASHGREEN:
                    return VETO_ON_FLASHGREEN;
                case VETO + Conditional.TYPE_SIGNAL_HEAD_DARK:
                    return VETO_ON_DARK;
                case VETO + Conditional.TYPE_SIGNAL_HEAD_LIT:
                    return VETO_ON_SIGNAL_LIT;
                case VETO + Conditional.TYPE_SIGNAL_HEAD_HELD:
                    return VETO_ON_SIGNAL_HELD;
                default:
                    log.warn("Unhandled test state: {}", _state);
                    break;
            }
            return "";
        }

        @Override
        void setTestState(String state) {
            if (ON_RED.equals(state)) {
                _state = Conditional.TYPE_SIGNAL_HEAD_RED;
            } else if (ON_FLASHRED.equals(state)) {
                _state = Conditional.TYPE_SIGNAL_HEAD_FLASHRED;
            } else if (ON_YELLOW.equals(state)) {
                _state = Conditional.TYPE_SIGNAL_HEAD_YELLOW;
            } else if (ON_FLASHYELLOW.equals(state)) {
                _state = Conditional.TYPE_SIGNAL_HEAD_FLASHYELLOW;
            } else if (ON_GREEN.equals(state)) {
                _state = Conditional.TYPE_SIGNAL_HEAD_GREEN;
            } else if (ON_FLASHGREEN.equals(state)) {
                _state = Conditional.TYPE_SIGNAL_HEAD_FLASHGREEN;
            } else if (ON_DARK.equals(state)) {
                _state = Conditional.TYPE_SIGNAL_HEAD_DARK;
            } else if (ON_SIGNAL_LIT.equals(state)) {
                _state = Conditional.TYPE_SIGNAL_HEAD_LIT;
            } else if (ON_SIGNAL_HELD.equals(state)) {
                _state = Conditional.TYPE_SIGNAL_HEAD_HELD;
            } else if (VETO_ON_RED.equals(state)) {
                _state = VETO + Conditional.TYPE_SIGNAL_HEAD_RED;
            } else if (VETO_ON_FLASHRED.equals(state)) {
                _state = VETO + Conditional.TYPE_SIGNAL_HEAD_FLASHRED;
            } else if (VETO_ON_YELLOW.equals(state)) {
                _state = VETO + Conditional.TYPE_SIGNAL_HEAD_YELLOW;
            } else if (VETO_ON_FLASHYELLOW.equals(state)) {
                _state = VETO + Conditional.TYPE_SIGNAL_HEAD_FLASHYELLOW;
            } else if (VETO_ON_GREEN.equals(state)) {
                _state = VETO + Conditional.TYPE_SIGNAL_HEAD_GREEN;
            } else if (VETO_ON_FLASHGREEN.equals(state)) {
                _state = VETO + Conditional.TYPE_SIGNAL_HEAD_FLASHGREEN;
            } else if (VETO_ON_DARK.equals(state)) {
                _state = VETO + Conditional.TYPE_SIGNAL_HEAD_DARK;
            } else if (VETO_ON_SIGNAL_LIT.equals(state)) {
                _state = VETO + Conditional.TYPE_SIGNAL_HEAD_LIT;
            } else if (VETO_ON_SIGNAL_HELD.equals(state)) {
                _state = VETO + Conditional.TYPE_SIGNAL_HEAD_HELD;
            }
        }
    }

    abstract class RouteOutputElement extends RouteElement {

        RouteOutputElement(String sysName, String userName, int type) {
            super(sysName, userName, type);
        }

        abstract String getSetToState();

        abstract void setSetToState(String state);
    }

    private class RouteOutputSensor extends RouteOutputElement {

        RouteOutputSensor(String sysName, String userName) {
            super(sysName, userName, SENSOR_TYPE);
            setState(Sensor.ACTIVE);
        }

        @Override
        String getSetToState() {
            switch (_state) {
                case Sensor.INACTIVE:
                    return SET_TO_INACTIVE;
                case Sensor.ACTIVE:
                    return SET_TO_ACTIVE;
                case Route.TOGGLE:
                    return SET_TO_TOGGLE;
                default:
                    log.warn("Unhandled set to state: {}", _state);
                    break;
            }
            return "";
        }

        @Override
        void setSetToState(String state) {
            if (SET_TO_INACTIVE.equals(state)) {
                _state = Sensor.INACTIVE;
            } else if (SET_TO_ACTIVE.equals(state)) {
                _state = Sensor.ACTIVE;
            } else if (SET_TO_TOGGLE.equals(state)) {
                _state = Route.TOGGLE;
            }
        }
    }

    private class RouteOutputTurnout extends RouteOutputElement {

        RouteOutputTurnout(String sysName, String userName) {
            super(sysName, userName, TURNOUT_TYPE);
            setState(Turnout.CLOSED);
        }

        @Override
        String getSetToState() {
            switch (_state) {
                case Turnout.CLOSED:
                    return SET_TO_CLOSED;
                case Turnout.THROWN:
                    return SET_TO_THROWN;
                case Route.TOGGLE:
                    return SET_TO_TOGGLE;
                default:
                    log.warn("Unhandled set to state: {}", _state);
            }
            return "";
        }

        @Override
        void setSetToState(String state) {
            if (SET_TO_CLOSED.equals(state)) {
                _state = Turnout.CLOSED;
            } else if (SET_TO_THROWN.equals(state)) {
                _state = Turnout.THROWN;
            } else if (SET_TO_TOGGLE.equals(state)) {
                _state = Route.TOGGLE;
            }
        }
    }

    private class RouteOutputLight extends RouteOutputElement {

        RouteOutputLight(String sysName, String userName) {
            super(sysName, userName, LIGHT_TYPE);
            setState(Light.ON);
        }

        @Override
        String getSetToState() {
            switch (_state) {
                case Light.ON:
                    return SET_TO_ON;
                case Light.OFF:
                    return SET_TO_OFF;
                case Route.TOGGLE:
                    return SET_TO_TOGGLE;
                default:
                    log.warn("Unhandled set to state: {}", _state);
            }
            return "";
        }

        @Override
        void setSetToState(String state) {
            if (SET_TO_ON.equals(state)) {
                _state = Light.ON;
            } else if (SET_TO_OFF.equals(state)) {
                _state = Light.OFF;
            } else if (SET_TO_TOGGLE.equals(state)) {
                _state = Route.TOGGLE;
            }
        }
    }

    private class RouteOutputSignal extends RouteOutputElement {

        RouteOutputSignal(String sysName, String userName) {
            super(sysName, userName, SIGNAL_TYPE);
            setState(SignalHead.RED);
        }

        @Override
        String getSetToState() {
            switch (_state) {
                case SignalHead.DARK:
                    return SET_TO_DARK;
                case SignalHead.RED:
                    return SET_TO_RED;
                case SignalHead.FLASHRED:
                    return SET_TO_FLASHRED;
                case SignalHead.YELLOW:
                    return SET_TO_YELLOW;
                case SignalHead.FLASHYELLOW:
                    return SET_TO_FLASHYELLOW;
                case SignalHead.GREEN:
                    return SET_TO_GREEN;
                case SignalHead.FLASHGREEN:
                    return SET_TO_FLASHGREEN;
                case CLEAR_SIGNAL_HELD:
                    return SET_TO_CLEAR;
                case SET_SIGNAL_LIT:
                    return SET_TO_LIT;
                case SET_SIGNAL_HELD:
                    return SET_TO_HELD;
                default:
                    log.warn("Unhandled set to state: {}", _state);
                    break;
            }
            return "";
        }

        @Override
        void setSetToState(String state) {
            if (SET_TO_DARK.equals(state)) {
                _state = SignalHead.DARK;
            } else if (SET_TO_RED.equals(state)) {
                _state = SignalHead.RED;
            } else if (SET_TO_FLASHRED.equals(state)) {
                _state = SignalHead.FLASHRED;
            } else if (SET_TO_YELLOW.equals(state)) {
                _state = SignalHead.YELLOW;
            } else if (SET_TO_FLASHYELLOW.equals(state)) {
                _state = SignalHead.FLASHYELLOW;
            } else if (SET_TO_GREEN.equals(state)) {
                _state = SignalHead.GREEN;
            } else if (SET_TO_FLASHGREEN.equals(state)) {
                _state = SignalHead.FLASHGREEN;
            } else if (SET_TO_CLEAR.equals(state)) {
                _state = CLEAR_SIGNAL_HELD;
            } else if (SET_TO_LIT.equals(state)) {
                _state = SET_SIGNAL_LIT;
            } else if (SET_TO_HELD.equals(state)) {
                _state = SET_SIGNAL_HELD;
            }
        }
    }

    class AlignElement extends RouteElement {

        AlignElement(String sysName, String userName) {
            super(sysName, userName, SENSOR_TYPE);
            setState(TURNOUT_TYPE);
        }

        String getAlignType() {
            switch (_state) {
                case SENSOR_TYPE:
                    return ALIGN_SENSOR;
                case TURNOUT_TYPE:
                    return ALIGN_TURNOUT;
                case LIGHT_TYPE:
                    return ALIGN_LIGHT;
                case SIGNAL_TYPE:
                    return ALIGN_SIGNAL;
                case ALL_TYPE:
                    return ALIGN_ALL;
                default:
                    log.warn("Unhandled align type state: {}", _state);
                    break;
            }
            return "";
        }

        void setAlignType(String state) {
            if (ALIGN_SENSOR.equals(state)) {
                _state = SENSOR_TYPE;
            } else if (ALIGN_TURNOUT.equals(state)) {
                _state = TURNOUT_TYPE;
            } else if (ALIGN_LIGHT.equals(state)) {
                _state = LIGHT_TYPE;
            } else if (ALIGN_SIGNAL.equals(state)) {
                _state = SIGNAL_TYPE;
            } else if (ALIGN_ALL.equals(state)) {
                _state = ALL_TYPE;
            }
        }
    }

    @Override
    public void setMessagePreferencesDetails() {
        InstanceManager.getDefault(UserPreferencesManager.class).setPreferenceItemDetails(
            getClassName(), "remindSaveRoute", Bundle.getMessage("HideSaveReminder"));
        super.setMessagePreferencesDetails();
    }

    @Override
    protected String getClassName() {
        return LRouteTableAction.class.getName();
    }

    @Override
    public String getClassDescription() {
        return Bundle.getMessage("TitleLRouteTable");
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LRouteTableAction.class);
}
