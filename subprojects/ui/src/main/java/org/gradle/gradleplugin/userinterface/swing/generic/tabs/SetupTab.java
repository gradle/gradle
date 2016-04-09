/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.gradleplugin.userinterface.swing.generic.tabs;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.settings.SettingsNode;
import org.gradle.gradleplugin.userinterface.swing.generic.OutputUILord;
import org.gradle.gradleplugin.userinterface.swing.generic.Utility;
import org.gradle.internal.SystemProperties;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.internal.logging.LoggingCommandLineConverter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;

/**
 * This tab contains general settings for the plugin.
 */
public class SetupTab implements GradleTab, GradlePluginLord.SettingsObserver {
    private final Logger logger = Logging.getLogger(SetupTab.class);

    private static final String STACK_TRACE_LEVEL_CLIENT_PROPERTY = "stack-trace-level-client-property";
    private static final String SETUP = "setup";
    private static final String STACK_TRACE_LEVEL = "stack-trace-level";
    private static final String SHOW_OUTPUT_ON_ERROR = "show-output-on-error";
    private static final String LOG_LEVEL = "log-level";
    private static final String CURRENT_DIRECTORY = "current-directory";
    private static final String CUSTOM_GRADLE_EXECUTOR = "custom-gradle-executor";

    private GradlePluginLord gradlePluginLord;
    private OutputUILord outputUILord;
    private SettingsNode settingsNode;

    private JPanel mainPanel;

    private JRadioButton showNoStackTraceRadioButton;
    private JRadioButton showStackTrackRadioButton;
    private JRadioButton showFullStackTrackRadioButton;

    private JComboBox logLevelComboBox;

    private JCheckBox onlyShowOutputOnErrorCheckBox;

    private ButtonGroup stackTraceButtonGroup;

    private JTextField currentDirectoryTextField;

    private JCheckBox useCustomGradleExecutorCheckBox;
    private JTextField customGradleExecutorField;
    private JButton browseForCustomGradleExecutorButton;

    private JPanel customPanelPlaceHolder;

    public SetupTab(GradlePluginLord gradlePluginLord, OutputUILord outputUILord, SettingsNode settingsNode) {
        this.gradlePluginLord = gradlePluginLord;
        this.outputUILord = outputUILord;
        this.settingsNode = settingsNode.addChildIfNotPresent(SETUP);
    }

    public String getName() {
        return "Setup";
    }

    public Component createComponent() {
        setupUI();

        return mainPanel;
    }

    /**
     * Notification that this component is about to be shown. Do whatever initialization you choose.
     */
    public void aboutToShow() {
        updatePluginLordSettings();
        gradlePluginLord.addSettingsObserver(this, true);
    }

    private void setupUI() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        mainPanel.add(createCurrentDirectoryPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createLogLevelPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createStackTracePanel());
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createOptionsPanel());
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(createCustomExecutorPanel());
        mainPanel.add(Box.createVerticalStrut(10));

        //add a panel that can be used to add custom things to the setup tab
        customPanelPlaceHolder = new JPanel(new BorderLayout());
        mainPanel.add(customPanelPlaceHolder);

        //Glue alone doesn't work in this situation. This forces everything to the top.
        JPanel expandingPanel = new JPanel(new BorderLayout());
        expandingPanel.add(Box.createVerticalGlue(), BorderLayout.CENTER);
        mainPanel.add(expandingPanel);

        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private Component createCurrentDirectoryPanel() {
        currentDirectoryTextField = new JTextField();
        currentDirectoryTextField.setEditable(false);

        String currentDirectory = settingsNode.getValueOfChild(CURRENT_DIRECTORY, null);
        if (currentDirectory == null || "".equals(currentDirectory.trim())) {
            currentDirectory = gradlePluginLord.getCurrentDirectory().getAbsolutePath();
        }

        currentDirectoryTextField.setText(currentDirectory);
        gradlePluginLord.setCurrentDirectory(new File(currentDirectory));

        JButton browseButton = new JButton(new AbstractAction("Browse...") {
            public void actionPerformed(ActionEvent e) {
                File file = browseForDirectory(gradlePluginLord.getCurrentDirectory());
                if (file != null) {
                    setCurrentDirectory(file);
                }
            }
        });

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(Utility.addLeftJustifiedComponent(new JLabel("Current Directory")));
        panel.add(createSideBySideComponent(currentDirectoryTextField, browseButton));

        return panel;
    }

    private void setCurrentDirectory(File file) {

        if (file == null) {
            currentDirectoryTextField.setText("");
            settingsNode.setValueOfChild(CURRENT_DIRECTORY, "");
        } else {
            currentDirectoryTextField.setText(file.getAbsolutePath());
            settingsNode.setValueOfChild(CURRENT_DIRECTORY, file.getAbsolutePath());
        }

        if (gradlePluginLord.setCurrentDirectory(file)) {
            //refresh the tasks only if we actually changed the current directory
            gradlePluginLord.addRefreshRequestToQueue();
        }
    }

    /**
     * this creates a panel where the right component is its preferred size. This is useful for putting on a button on the right and a text field on the left.
     */
    public static JComponent createSideBySideComponent(Component leftComponent, Component rightComponent) {
        JPanel xLayoutPanel = new JPanel();
        xLayoutPanel.setLayout(new BoxLayout(xLayoutPanel, BoxLayout.X_AXIS));

        Dimension preferredSize = leftComponent.getPreferredSize();
        leftComponent.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredSize.height));

        xLayoutPanel.add(leftComponent);
        xLayoutPanel.add(Box.createHorizontalStrut(5));
        xLayoutPanel.add(rightComponent);

        return xLayoutPanel;
    }

    /**
     * Browses for a file using the text value from the text field as the current value.
     */
    private File browseForDirectory(File initialFile) {

        if (initialFile == null) {
            initialFile = SystemProperties.getInstance().getCurrentDir();
        }

        JFileChooser chooser = new JFileChooser(initialFile);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);

        File file = null;
        if (chooser.showOpenDialog(mainPanel) == JFileChooser.APPROVE_OPTION) {
            file = chooser.getSelectedFile();
        }

        return file;
    }

    /**
     * Creates a panel that has a combo box to select a log level
     */
    private Component createLogLevelPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        logLevelComboBox = new JComboBox(getLogLevelWrappers());

        panel.add(Utility.addLeftJustifiedComponent(new JLabel("Log Level")));
        panel.add(Utility.addLeftJustifiedComponent(logLevelComboBox));

        //initialize our value
        String logLevelName = settingsNode.getValueOfChild(LOG_LEVEL, null);
        LogLevel logLevel = gradlePluginLord.getLogLevel();
        if (logLevelName != null) {
            try {
                logLevel = LogLevel.valueOf(logLevelName);
            } catch (IllegalArgumentException e) //this may happen if the enum changes. We don't want this to stop the whole UI
            {
                logger.error("Converting log level text to log level enum '" + logLevelName + "'", e);
            }
        }

        gradlePluginLord.setLogLevel(logLevel);
        setLogLevelComboBoxSetting(logLevel);

        logLevelComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                LogLevelWrapper wrapper = (LogLevelWrapper) logLevelComboBox.getSelectedItem();
                if (wrapper != null) {
                    gradlePluginLord.setLogLevel(wrapper.logLevel);
                    settingsNode.setValueOfChild(LOG_LEVEL, wrapper.logLevel.name());
                }
            }
        });

        return panel;
    }

    /**
     * This creates an array of wrapper objects suitable for passing to the constructor of the log level combo box.
     */
    private Vector<LogLevelWrapper> getLogLevelWrappers() {
        Collection<LogLevel> collection = new LoggingCommandLineConverter().getLogLevels();

        Vector<LogLevelWrapper> wrappers = new Vector<LogLevelWrapper>();

        Iterator<LogLevel> iterator = collection.iterator();

        while (iterator.hasNext()) {
            LogLevel level = iterator.next();
            wrappers.add(new LogLevelWrapper(level));
        }

        Collections.sort(wrappers, new Comparator<LogLevelWrapper>() {
            public int compare(LogLevelWrapper o1, LogLevelWrapper o2) {
                return o1.toString().compareToIgnoreCase(o2.toString());
            }
        });

        return wrappers;
    }

    /**
     * This exists solely for overriding toString to something nicer. We'll captilize the first letter. The rest become lower case. Ultimately, this should probably move into LogLevel. We'll also put
     * the log level shortcut in parenthesis
     */
    private class LogLevelWrapper {
        private LogLevel logLevel;
        private String toString;

        private LogLevelWrapper(LogLevel logLevel) {
            this.logLevel = logLevel;

            String temp = logLevel.toString().toLowerCase().replace('_', ' '); //replace underscores in the name with spaces
            this.toString = Character.toUpperCase(temp.charAt(0)) + temp.substring(1);

            //add the command line character to the end (so if an error message says use a log level, you can easily translate)
            String commandLineCharacter = new LoggingCommandLineConverter().getLogLevelCommandLine(logLevel);
            if (commandLineCharacter != null && !commandLineCharacter.equals("")) {
                this.toString += " (-" + commandLineCharacter + ")";
            }
        }

        public String toString() {
            return toString;
        }
    }

    /**
     * Sets the log level combo box to the specified log level.
     *
     * @param logLevel the log level in question.
     */
    private void setLogLevelComboBoxSetting(LogLevel logLevel) {
        DefaultComboBoxModel model = (DefaultComboBoxModel) logLevelComboBox.getModel();
        for (int index = 0; index < model.getSize(); index++) {
            LogLevelWrapper wrapper = (LogLevelWrapper) model.getElementAt(index);
            if (wrapper.logLevel == logLevel) {
                logLevelComboBox.setSelectedIndex(index);
                return;
            }
        }
    }

    /**
     * Creates a panel with stack trace level radio buttons that allow you to specify how much info is given when an error occurs.
     */
    private Component createStackTracePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.setBorder(BorderFactory.createTitledBorder("Stack Trace Output"));

        showNoStackTraceRadioButton = new JRadioButton("Exceptions Only");
        showStackTrackRadioButton = new JRadioButton("Standard Stack Trace (-" + LoggingCommandLineConverter.STACKTRACE
                + ")");  //add the command line character to the end (so if an error message says use a stack trace level, you can easily translate)
        showFullStackTrackRadioButton = new JRadioButton("Full Stack Trace (-" + LoggingCommandLineConverter.FULL_STACKTRACE + ")");

        showNoStackTraceRadioButton.putClientProperty(STACK_TRACE_LEVEL_CLIENT_PROPERTY, ShowStacktrace.INTERNAL_EXCEPTIONS);
        showStackTrackRadioButton.putClientProperty(STACK_TRACE_LEVEL_CLIENT_PROPERTY, ShowStacktrace.ALWAYS);
        showFullStackTrackRadioButton.putClientProperty(STACK_TRACE_LEVEL_CLIENT_PROPERTY, ShowStacktrace.ALWAYS_FULL);

        stackTraceButtonGroup = new ButtonGroup();
        stackTraceButtonGroup.add(showNoStackTraceRadioButton);
        stackTraceButtonGroup.add(showStackTrackRadioButton);
        stackTraceButtonGroup.add(showFullStackTrackRadioButton);

        showNoStackTraceRadioButton.setSelected(true);

        ActionListener radioButtonListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateStackTraceSetting(true);
            }
        };

        showNoStackTraceRadioButton.addActionListener(radioButtonListener);
        showStackTrackRadioButton.addActionListener(radioButtonListener);
        showFullStackTrackRadioButton.addActionListener(radioButtonListener);

        panel.add(Utility.addLeftJustifiedComponent(showNoStackTraceRadioButton));
        panel.add(Utility.addLeftJustifiedComponent(showStackTrackRadioButton));
        panel.add(Utility.addLeftJustifiedComponent(showFullStackTrackRadioButton));

        String stackTraceLevel = settingsNode.getValueOfChild(STACK_TRACE_LEVEL, getSelectedStackTraceLevel().name());
        if (stackTraceLevel != null) {
            try {
                setSelectedStackTraceLevel(ShowStacktrace.valueOf(stackTraceLevel));
                updateStackTraceSetting(false);   //false because we're serializing this in
            } catch (Exception e) {  //this can happen if the stack trace levels change because you're moving between versions.
                logger.error("Converting stack trace level text to stack trace level enum '" + stackTraceLevel + "'", e);
            }
        }

        return panel;
    }

    /**
     * This stores the current stack trace setting (based on the UI controls) in the plugin.
     */
    private void updateStackTraceSetting(boolean saveSetting) {
        ShowStacktrace stackTraceLevel = getSelectedStackTraceLevel();
        gradlePluginLord.setStackTraceLevel(stackTraceLevel);

        if (saveSetting) {
            settingsNode.setValueOfChild(STACK_TRACE_LEVEL, stackTraceLevel.name());
        }
    }

    /**
     * Sets the selected strack trace level on the radio buttons. The radio buttons store their stack trace level as a client property and I'll look for a match using that. This way, we don't have to
     * edit this if new levels are created.
     *
     * @param newStackTraceLevel the new stack trace level.
     */
    private void setSelectedStackTraceLevel(ShowStacktrace newStackTraceLevel) {
        Enumeration<AbstractButton> buttonEnumeration = stackTraceButtonGroup.getElements();
        while (buttonEnumeration.hasMoreElements()) {
            JRadioButton radioButton = (JRadioButton) buttonEnumeration.nextElement();
            ShowStacktrace level = (ShowStacktrace) radioButton.getClientProperty(STACK_TRACE_LEVEL_CLIENT_PROPERTY);
            if (newStackTraceLevel == level) {
                radioButton.setSelected(true);
                return;
            }
        }
    }

    /**
     * Returns the currently selected stack trace level.  The radio buttons store their stack trace level as a client property so once we get the selected button, we know the level. This way, we don't
     * have to edit this if new levels are created. Unfortunately, Swing doesn't have an easy way to get the actual button from the group.
     *
     * @return the selected stack trace level
     */
    private ShowStacktrace getSelectedStackTraceLevel() {
        ButtonModel selectedButtonModel = stackTraceButtonGroup.getSelection();
        if (selectedButtonModel != null) {
            Enumeration<AbstractButton> buttonEnumeration = stackTraceButtonGroup.getElements();
            while (buttonEnumeration.hasMoreElements()) {
                JRadioButton radioButton = (JRadioButton) buttonEnumeration.nextElement();
                if (radioButton.getModel() == selectedButtonModel) {
                    ShowStacktrace level = (ShowStacktrace) radioButton.getClientProperty(STACK_TRACE_LEVEL_CLIENT_PROPERTY);
                    return level;
                }
            }
        }

        return ShowStacktrace.INTERNAL_EXCEPTIONS;
    }

    private Component createOptionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        onlyShowOutputOnErrorCheckBox = new JCheckBox("Only Show Output When Errors Occur");

        onlyShowOutputOnErrorCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateShowOutputOnErrorsSetting();
                settingsNode.setValueOfChildAsBoolean(SHOW_OUTPUT_ON_ERROR, onlyShowOutputOnErrorCheckBox.isSelected());
            }
        });

        //initialize its default value
        boolean valueAsBoolean = settingsNode.getValueOfChildAsBoolean(SHOW_OUTPUT_ON_ERROR, onlyShowOutputOnErrorCheckBox.isSelected());
        onlyShowOutputOnErrorCheckBox.setSelected(valueAsBoolean);

        updateShowOutputOnErrorsSetting();

        panel.add(Utility.addLeftJustifiedComponent(onlyShowOutputOnErrorCheckBox));

        return panel;
    }

    private void updateShowOutputOnErrorsSetting() {
        boolean value = onlyShowOutputOnErrorCheckBox.isSelected();

        outputUILord.setOnlyShowOutputOnErrors(value);
    }

    private Component createCustomExecutorPanel() {
        useCustomGradleExecutorCheckBox = new JCheckBox("Use Custom Gradle Executor");

        customGradleExecutorField = new JTextField();
        customGradleExecutorField.setEditable(false);

        browseForCustomGradleExecutorButton = new JButton(new AbstractAction("Browse...") {
            public void actionPerformed(ActionEvent e) {
                browseForCustomGradleExecutor();
            }
        });

        String customExecutorPath = settingsNode.getValueOfChild(CUSTOM_GRADLE_EXECUTOR, null);
        if (customExecutorPath == null) {
            setCustomGradleExecutor(null);
        } else {
            setCustomGradleExecutor(new File(customExecutorPath));
        }

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(Utility.addLeftJustifiedComponent(useCustomGradleExecutorCheckBox));
        JComponent sideBySideComponent = createSideBySideComponent(customGradleExecutorField, browseForCustomGradleExecutorButton);
        sideBySideComponent.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 0)); //indent it
        panel.add(sideBySideComponent);

        useCustomGradleExecutorCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (useCustomGradleExecutorCheckBox.isSelected()) { //if they checked it, browse for a custom executor immediately
                    browseForCustomGradleExecutor();
                } else {
                    setCustomGradleExecutor(null);
                }
            }
        });

        return panel;
    }

    /**
     * Call this to browse for a custom gradle executor.
     */
    private void browseForCustomGradleExecutor() {
        File startingDirectory = new File(SystemProperties.getInstance().getUserHome());
        File currentFile = gradlePluginLord.getCustomGradleExecutor();
        if (currentFile != null) {
            startingDirectory = currentFile.getAbsoluteFile();
        } else {
            if (gradlePluginLord.getCurrentDirectory() != null) {
                startingDirectory = gradlePluginLord.getCurrentDirectory();
            }
        }

        JFileChooser chooser = new JFileChooser(startingDirectory);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);

        File file = null;
        if (chooser.showOpenDialog(mainPanel) == JFileChooser.APPROVE_OPTION) {
            file = chooser.getSelectedFile();
        }

        if (file != null) {
            setCustomGradleExecutor(file);
        } else {  //if they canceled, and they have no custom gradle executor specified, then we must clear things
            //This will reset the UI back to 'not using a custom executor'. We can't have them check the
            //field and not have a value here.
            if (gradlePluginLord.getCustomGradleExecutor() == null) {
                setCustomGradleExecutor(null);
            }
        }
    }

    /**
     * Call this to set a custom gradle executor. We'll enable all fields appropriately and setup the foundation settings. We'll also fire off a refresh.
     *
     * @param file the file to use as a custom executor. Null not to use one.
     */
    private void setCustomGradleExecutor(File file) {
        String storagePath;
        boolean isUsingCustom = false;
        if (file == null) {
            isUsingCustom = false;
            storagePath = null;
        } else {
            isUsingCustom = true;
            storagePath = file.getAbsolutePath();
        }

        //set the executor in the foundation
        if (gradlePluginLord.setCustomGradleExecutor(file)) {
            //refresh the tasks only if we actually changed the executor
            gradlePluginLord.addRefreshRequestToQueue();
        }

        //set the UI values
        useCustomGradleExecutorCheckBox.setSelected(isUsingCustom);
        customGradleExecutorField.setText(storagePath);

        //enable the UI appropriately.
        browseForCustomGradleExecutorButton.setEnabled(isUsingCustom);
        customGradleExecutorField.setEnabled(isUsingCustom);

        //store the settings
        settingsNode.setValueOfChild(CUSTOM_GRADLE_EXECUTOR, storagePath);
    }

    /**
     * This adds the specified component to the setup panel. It is added below the last 'default' item. You can only add 1 component here, so if you need to add multiple things, you'll have to handle
     * adding that to yourself to the one component.
     *
     * @param component the component to add.
     */
    public void setCustomPanel(JComponent component) {
        customPanelPlaceHolder.add(component, BorderLayout.CENTER);
        customPanelPlaceHolder.invalidate();
        mainPanel.validate();
    }

    /**
     * Notification that some settings have changed for the plugin. Settings such as current directory, gradle home directory, etc. This is useful for UIs that need to update their UIs when this is
     * changed by other means.
     */
    public void settingsChanged() {
        updatePluginLordSettings();
    }

    /**
     * Called upon start up and whenever GradlePluginLord settings are changed. We'll update our values. Note: this actually gets called several times in a row for each settings during initialization.
     * Its not optimal, but functional and I didn't want to deal with numerous, specific-field notifications.
     */
    private void updatePluginLordSettings() {
        setCustomGradleExecutor(gradlePluginLord.getCustomGradleExecutor());

        setCurrentDirectory(gradlePluginLord.getCurrentDirectory());

        setSelectedStackTraceLevel(gradlePluginLord.getStackTraceLevel());

        setLogLevelComboBoxSetting(gradlePluginLord.getLogLevel());
    }
}
