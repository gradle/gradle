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
package org.gradle.gradleplugin.userinterface.swing.standalone;

import org.gradle.gradleplugin.foundation.DOM4JSerializer;
import org.gradle.gradleplugin.foundation.ExtensionFileFilter;
import org.gradle.gradleplugin.foundation.settings.DOM4JSettingsNode;
import org.gradle.gradleplugin.userinterface.AlternateUIInteraction;
import org.gradle.gradleplugin.userinterface.swing.common.PreferencesAssistant;
import org.gradle.gradleplugin.userinterface.swing.generic.SinglePaneUIInstance;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

/**
 * The main entry point for a stand-alone application for Gradle. The real work is not done here. This is just a UI
 * containing components that are meant to be reuseable in other UIs (say an IDE plugin). Those other components do the
 * real work. Most of the work is wrapped inside SinglePaneUIInstance.
 *
 * @author mhunsicker
 */
public class Application implements AlternateUIInteraction {
    private final Logger logger = Logging.getLogger(Application.class);

    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 800;

    private static final String WINDOW_PREFERENCES_ID = "window-id";
    private static final String SETTINGS_EXTENSION = ".setting";

    private JFrame frame;
    private SinglePaneUIInstance singlePaneUIInstance;

    private boolean doesSupportEditingFiles;

    private LifecycleListener lifecycleListener = null;
    private DOM4JSettingsNode rootSettingsNode;

    /**
     * Interface that allows the caller to do post shutdown processing. For example, you may want to exit the VM. You
     * may not.
     */
    public interface LifecycleListener {
        /**
         * Notification that the application has started successfully. This is fired within the same thread that
         * instantiates us.
         */
        public void hasStarted();

        /**
         * Notification that the application has shut down. This is fired from the Event Dispatch Thread.
         */
        public void hasShutDown();
    }

    public static void main(String[] args) {
        new Application(new LifecycleListener() {
            public void hasStarted() {
                //we don't care
            }

            public void hasShutDown() {
                System.exit(0);
            }
        });
    }

    public Application(LifecycleListener lifecycleListener) {
        this.lifecycleListener = lifecycleListener;

        try {   //try and make it look like a native app
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }

        this.doesSupportEditingFiles = determineIfSupportsEditingFiles();

        //read in the settings
        rootSettingsNode = DOM4JSerializer.readSettingsFile(new SettingsImportInteraction(), createFileFilter());
        if (rootSettingsNode == null) {
            rootSettingsNode = DOM4JSerializer.createBlankSettings();
        }

        singlePaneUIInstance = new SinglePaneUIInstance();
        singlePaneUIInstance.initialize( rootSettingsNode, this);

        setupUI();

        restoreSettings();

        frame.setVisible(true);

        lifecycleListener.hasStarted();  //notify listeners that we have successfully started
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
                int result = JOptionPane.showConfirmDialog(frame,
                        "Gradle tasks are being currently being executed. Exit anyway?", "Exit While Busy?",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
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
     * This is called when we should edit the specified file. Open it in the current IDE or some external editor.
     *
     * @param files the files to open
     */
    public void editFiles(List<File> files) {
        try {
            Class<?> desktopClass = Class.forName("java.awt.Desktop");
            Method getDesktopMethod = desktopClass.getDeclaredMethod("getDesktop", (Class<?>[]) null);
            Object desktopObject = getDesktopMethod.invoke(null, (Object[]) null);
            if (desktopObject != null)   //may be null if this plaform doesn't support this.
            {
                Method method = desktopClass.getMethod("edit", new Class[]{File.class});
                Iterator<File> iterator = files.iterator();
                while (iterator.hasNext()) {
                    File file = iterator.next();
                    if (file.exists())  //the file might not exist. This happens if its just using the default settings (no file is required).
                    {
                        method.invoke(desktopObject, file);
                    } else {
                        JOptionPane.showMessageDialog(frame, "File does not exist '" + file.getAbsolutePath() + "'");
                    }
                }
            }
        } catch (NoSuchMethodException e) {
            logger.info("Trying to edit files via java's Desktop method. This VM doesn't support it.", e);
            //we're not requiring 1.6, so its not a problem if we don't find the method. We just don't get this feature.
        } catch (Exception e) {
            logger.error("Trying to edit files via java's Desktop methods.", e);
        }
    }

    /**
     * Determines if we can call editFiles. This is not a dynamic answer and should always return either true of false.
     * If you want to change the answer, return true and then handle the files differently in editFiles.
     *
     * @return true if support editing files, false otherwise.
     */
    public boolean doesSupportEditingFiles() {
        return doesSupportEditingFiles;
    }

    /**
     * Determines if we support editing files. At the time of this writing, we were mooching off of java 1.6's ability
     * to get the OS to do this. If we're running on 1.5, this will fail.
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
        return new File(System.getProperty("user.dir"), "gradle-app" + SETTINGS_EXTENSION);
    }

    private class SettingsImportInteraction implements DOM4JSerializer.ImportInteraction {
        /**
         * This is called when you should ask the user for a source file to read.
         *
         * @return a file to read or null to cancel.
         */
        public File promptForFile(FileFilter fileFilters) {
            File settingsFile = getSettingsFile();
            if (!settingsFile
                    .exists())  //if its not present (first time we've run on this machine), just cancel the read.
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
        public boolean confirmOverwritingExisingFile(File file) {
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
