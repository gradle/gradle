/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.gradleplugin.userinterface.swing.standalone;

import org.gradle.gradleplugin.foundation.DOM4JSerializer;
import org.gradle.gradleplugin.foundation.ExtensionFileFilter;
import org.gradle.gradleplugin.foundation.settings.DOM4JSettingsNode;
import org.gradle.gradleplugin.userinterface.AlternateUIInteraction;
import org.gradle.gradleplugin.userinterface.swing.common.PreferencesAssistant;
import org.gradle.gradleplugin.userinterface.swing.generic.SinglePaneUIInstance;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.UncheckedException;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;

/**
 * The main entry point for a stand-alone application for Gradle. The real work is not done here. This is just a UI containing components that are meant to be reuseable in other UIs (say an IDE
 * plugin). Those other components do the real work. Most of the work is wrapped inside SinglePaneUIInstance.
 */
public class Application implements AlternateUIInteraction {
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 800;

    private static final String WINDOW_PREFERENCES_ID = "window-id";
    private static final String SETTINGS_EXTENSION = ".setting";

    private JFrame frame;
    private SinglePaneUIInstance singlePaneUIInstance;

    private boolean doesSupportEditingFiles;

    private LifecycleListener lifecycleListener;
    private DOM4JSettingsNode rootSettingsNode;

    /**
     * Interface that allows the caller to do post shutdown processing. For example, you may want to exit the VM. You may not.
     */
    public interface LifecycleListener {
        /**
         * Notification that the application has shut down. This is fired from the Event Dispatch Thread.
         */
        public void hasShutDown();
    }

    public Application(LifecycleListener lifecycleListener) {
        this.lifecycleListener = lifecycleListener;

        try {   //try and make it look like a native app
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        this.doesSupportEditingFiles = determineIfSupportsEditingFiles();

        //read in the settings
        rootSettingsNode = DOM4JSerializer.readSettingsFile(new SettingsImportInteraction(), createFileFilter());
        if (rootSettingsNode == null) {
            rootSettingsNode = DOM4JSerializer.createBlankSettings();
        }

        singlePaneUIInstance = new SinglePaneUIInstance();
        singlePaneUIInstance.initialize(rootSettingsNode, this);

        setupUI();

        restoreSettings();

        frame.setVisible(true);
    }

    private void setupUI() {
        frame = new JFrame("Gradle");

        JPanel mainPanel = new JPanel(new BorderLayout());
        frame.getContentPane().add(mainPanel);

        mainPanel.add(singlePaneUIInstance.getComponent());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        singlePaneUIInstance.aboutToShow();

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                close();
            }
        });

        frame.setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        frame.setLocationByPlatform(true);
    }

    private void close() {
        boolean canClose = singlePaneUIInstance.canClose(new SinglePaneUIInstance.CloseInteraction() {
            public boolean promptUserToConfirmClosingWhileBusy() {
                int result = JOptionPane.showConfirmDialog(frame, "Gradle tasks are being currently being executed. Exit anyway?", "Exit While Busy?", JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                return result == JOptionPane.YES_OPTION;
            }
        });

        if (!canClose) {
            return;
        }

        singlePaneUIInstance.close();

        saveSettings();
        frame.setVisible(false);

        if (lifecycleListener != null) {
            lifecycleListener.hasShutDown();
        } else {
            System.exit(0);
        }
    }

    private void saveSettings() {
        PreferencesAssistant.saveSettings(rootSettingsNode, frame, WINDOW_PREFERENCES_ID, Application.class);

        DOM4JSerializer.exportToFile(new SettingsExportInteraction(), createFileFilter(), rootSettingsNode);
    }

    private void restoreSettings() {
        PreferencesAssistant.restoreSettings(rootSettingsNode, frame, WINDOW_PREFERENCES_ID, Application.class);
    }

    /**
     * Notification that you should open the specified file and go to the specified line. Its up to the application to determine if this file should be opened for editing or simply displayed. The
     * difference comes into play for things like xml or html files where a user may want to open them in a browser vs a source code file where they may want to open it directly in an IDE.
     *
     * @param file the file to edit
     * @param line the line to go to. -1 if no line is specified.
     */
    public void openFile(File file, int line) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            browseFile(file);
        } else {
            editFile(file, line);
        }
    }

    public void browseFile(File file) {
        if (!file.exists())  //the file might not exist. This happens if its just using the default settings (no file is required).
        {
            JOptionPane.showMessageDialog(frame, "File does not exist '" + file.getAbsolutePath() + "'");
        } else {

            if (!invokeDesktopFunction("browse", URI.class, file.toURI())) {
                String extension = getFileNameExtension(file.getName());
                JOptionPane.showMessageDialog(frame, "Cannot browse file. Do you have an application assocated with '" + extension + "' files?");
            }
        }
    }

    /**
     * This is called when we should edit the specified file. Open it in the current IDE or some external editor.
     */
    public void editFile(File file, int line) {
        editFileInExternalApplication(file, true);
    }

    /**
     * This edits the application using java.awt.Desktop. Since we're compiling with 1.5 and this is a 1.6 feature, this is done using reflection making this much uglier than it needs to be.
     *
     * @param file the file to edit
     * @param attemptToOpen true if we should attempt to just open the file is editing it fails. Often, file associations don't distinguish edit from open and open is the default.
     */
    public void editFileInExternalApplication(File file, boolean attemptToOpen) {
        if (!file.exists())  //the file might not exist. This happens if its just using the default settings (no file is required).
        {
            JOptionPane.showMessageDialog(frame, "File does not exist '" + file.getAbsolutePath() + "'");
        } else {
            if (!invokeDesktopFunction("edit", File.class, file)) {
                openFileInExternalApplication(file);
            }
        }
    }

    public void openFileInExternalApplication(File file) {

        if (!file.exists())  //the file might not exist. This happens if its just using the default settings (no file is required).
        {
            JOptionPane.showMessageDialog(frame, "File does not exist '" + file.getAbsolutePath() + "'");
        } else {
            if (!invokeDesktopFunction("open", File.class, file)) {
                String extension = getFileNameExtension(file.getName());
                JOptionPane.showMessageDialog(frame, "Cannot open file. Do you have an application assocated with '" + extension + "' files?");
            }
        }
    }

    /**
     * This invokes one of the java.awt.Desktop functions. Since we're compiling with 1.5 and this is a 1.6 feature, this is done using reflection making this much uglier than it needs to be. This is
     * for calling one of the 'edit', 'browse', 'open' or even 'mail' functions that always take a single argument.
     *
     * @param name the function to invoke
     * @param argumentClass the class of the argument of the above function.
     * @param argument the argument itself.
     * @return true if it worked, false if not. It might fail if the platform doesn't support editing/opening the file passed in, for example.
     */
    public boolean invokeDesktopFunction(String name, Class argumentClass, Object argument) {
        try {
            Class<?> desktopClass = Class.forName("java.awt.Desktop");
            Method getDesktopMethod = desktopClass.getDeclaredMethod("getDesktop", (Class<?>[]) null);
            Object desktopObject = getDesktopMethod.invoke(null, (Object[]) null);
            if (desktopObject != null)   //may be null if this plaform doesn't support this.
            {
                Method method = desktopClass.getMethod(name, new Class[]{argumentClass});
                method.invoke(desktopObject, argument);
                return true;
            }
        } catch (Exception e) {
            //ignore this. Just return false. This is relatively normal to get these and if you look at where this is called with 'edit', if it fails, we'll try again with open.
        }
        return false;
    }

    /**
     * Returns the file extension preserving its case.
     * @param fileName the file name
     * @return the extension.
     */
    public static String getFileNameExtension(String fileName) {
        String result = fileName;
        int indexOfDot = fileName.lastIndexOf('.');
        if (indexOfDot > 0) {
            result = fileName.substring(indexOfDot + 1, result.length());
        }

        return result;
    }

    /**
     * Determines if we can call editFiles. This is not a dynamic answer and should always return either true of false. If you want to change the answer, return true and then handle the files
     * differently in editFiles.
     *
     * @return true if support editing files, false otherwise.
     */
    public boolean doesSupportEditingOpeningFiles() {
        return doesSupportEditingFiles;
    }

    /**
     * Determines if we support editing files. At the time of this writing, we were mooching off of java 1.6's ability to get the OS to do this. If we're running on 1.5, this will fail.
     *
     * @return true if we support it, false if not.
     */
    public boolean determineIfSupportsEditingFiles() {
        try {
            Class<?> desktopClass = Class.forName("java.awt.Desktop");
            Method getDesktopMethod = desktopClass.getDeclaredMethod("isDesktopSupported", (Class<?>[]) null);
            Object desktopObject = getDesktopMethod.invoke(null, (Object[]) null);
            return (Boolean) desktopObject;
        } catch (Exception e) {
            return false;
        }
    }

    private ExtensionFileFilter createFileFilter() {
        return new ExtensionFileFilter(SETTINGS_EXTENSION, "Setting");
    }

    /**
     * @return the file that we save our settings to.
     */
    private File getSettingsFile() {
        return new File(SystemProperties.getInstance().getCurrentDir(), "gradle-app" + SETTINGS_EXTENSION);
    }

    private class SettingsImportInteraction implements DOM4JSerializer.ImportInteraction {
        /**
         * This is called when you should ask the user for a source file to read.
         *
         * @return a file to read or null to cancel.
         */
        public File promptForFile(FileFilter fileFilters) {
            File settingsFile = getSettingsFile();
            if (!settingsFile.exists())  //if its not present (first time we've run on this machine), just cancel the read.
            {
                return null;
            }
            return settingsFile;
        }

        /**
         * Report an error that occurred. The read failed.
         *
         * @param error the error message.
         */
        public void reportError(String error) {
            JOptionPane.showMessageDialog(frame, "Failed to read settings: " + error);
        }
    }

    /**
     * This interaction is for saving our settings. As such, its not all that interactive unless errors occur.
     */
    private class SettingsExportInteraction implements DOM4JSerializer.ExportInteraction {
        /**
         * This is called when you should ask the user for a destination file of a save.
         *
         * @return a file to save to or null to cancel.
         */
        public File promptForFile(FileFilter fileFilters) {
            return getSettingsFile();
        }

        /**
         * The file already exists. Confirm whether or not you want to overwrite it.
         *
         * @param file the file in question
         * @return true to overwrite it, false not to.
         */
        public boolean confirmOverwritingExistingFile(File file) {
            return true;   //It's most likely going to exist. Always overwrite it.
        }

        /**
         * Report an error that occurred. The save failed.
         *
         * @param error the error message.
         */
        public void reportError(String error) {
            JOptionPane.showMessageDialog(frame, "Failed to save settings: " + error);
        }
    }
}
