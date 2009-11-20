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
package org.gradle.gradleplugin.userinterface.swing.generic;

import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.settings.SettingsNode;
import org.gradle.gradleplugin.userinterface.AlternateUIInteraction;
import org.gradle.gradleplugin.userinterface.swing.common.PreferencesAssistant;
import org.gradle.gradleplugin.userinterface.swing.generic.tabs.GradleTab;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;

/**
 * A simple UI for gradle. This is a single panel that can be inserted into a stand-alone application or an IDE. This is
 * meant to hide most of the complexities of gradle. 'single pane' means that both the tabbed pane and the output pane
 * are contained within a single pane that this maintains. Meaning, you add this to a UI and its a self-contained gradle
 * UI. This is opposed to a multi-pane concept where the output would be separated from the tabbed pane.
 *
 * @author mhunsicker
 */
public class SinglePaneUIInstance {
    private static final String SPLITTER_PREFERENCES_ID = "splitter-id";

    private JSplitPane splitter;
    private MainGradlePanel gradlePanel;
    private GradlePluginLord gradlePluginLord;
    private SwingGradleExecutionWrapper swingGradleWrapper;
    private SettingsNode settings;
    private AlternateUIInteraction alternateUIInteraction;

    private JPanel mainPanel;

    public SinglePaneUIInstance(SettingsNode settings, AlternateUIInteraction alternateUIInteraction) {
        this.settings = settings;
        this.alternateUIInteraction = alternateUIInteraction;

        gradlePluginLord = new GradlePluginLord();
        swingGradleWrapper = new SwingGradleExecutionWrapper(gradlePluginLord, alternateUIInteraction);

        setupUI();
    }

    public JComponent getComponent() {
        return mainPanel;
    }

    private void setupUI() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(createCenterPanel(), BorderLayout.CENTER);
    }

    private Component createCenterPanel() {
        splitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        splitter.setTopComponent(createGradlePanel());
        splitter.setBottomComponent(swingGradleWrapper.getMainPanel());

        splitter.setContinuousLayout(true);

        //This little bit of tedium is so we can set our size based on window's size. This listens
        //for when the window is actually shown. It then adds a listen to store the location.
        splitter.addHierarchyListener(new HierarchyListener() {
            public void hierarchyChanged(HierarchyEvent e) {
                if (HierarchyEvent.SHOWING_CHANGED == (e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED)) {
                    splitter.removeHierarchyListener(
                            this); //we only want the first one of these, so remove ourselves as a listener.
                    Window window = SwingUtilities.getWindowAncestor(splitter);
                    if (window != null) {
                        Dimension dimension = window.getSize();
                        int halfHeight = dimension.height / 2; //we'll just make ourselves half the height of the window
                        splitter.setDividerLocation(halfHeight);
                    }
                    PreferencesAssistant.restoreSettings(settings, splitter, SPLITTER_PREFERENCES_ID,
                            SinglePaneUIInstance.class);

                    //Now that we're visible, this is so we save the location when the splitter is moved.
                    splitter.addPropertyChangeListener(new PropertyChangeListener() {
                        public void propertyChange(PropertyChangeEvent evt) {
                            if (JSplitPane.DIVIDER_LOCATION_PROPERTY.equals(evt.getPropertyName())) {
                                PreferencesAssistant.saveSettings(settings, splitter, SPLITTER_PREFERENCES_ID,
                                        SinglePaneUIInstance.class);
                            }
                        }
                    });
                }
            }
        });

        splitter.setResizeWeight(
                1);   //this keeps the bottom the same size when resizing the window. Extra space is added/removed from the top.

        return splitter;
    }

    private Component createGradlePanel() {
        gradlePanel = new MainGradlePanel(gradlePluginLord, swingGradleWrapper, settings, alternateUIInteraction);
        return gradlePanel;
    }

    /**
     * Call this whenever you're about to show this panel. We'll do whatever initialization is necessary.
     */
    public void aboutToShow() {
        gradlePanel.aboutToShow();
    }

    public interface CloseInteraction {
        /**
         * This is called if gradle tasks are being executed and you want to know if we can close. Ask the user.
         *
         * @return true if the user confirms cancelling the current tasks. False if not.
         */
        public boolean promptUserToConfirmClosingWhileBusy();
    }

    /**
     * Call this to deteremine if you can close this pane. if we're busy, we'll ask the user if they want to close.
     *
     * @param closeInteraction allows us to interact with the user
     * @return true if we can close, false if not.
     */
    public boolean canClose(CloseInteraction closeInteraction) {
        if (!swingGradleWrapper.isBusy()) {
            return true;
        }

        return closeInteraction.promptUserToConfirmClosingWhileBusy();
    }

    /**
     * Call this before you close the pane. This gives it an opportunity to do cleanup. You probably should call
     * canClose before this. It gives the app a chance to cancel if its busy.
     */
    public void close() {
        gradlePanel.aboutToClose();
    }

    public File getCurrentDirectory() {
        return gradlePluginLord.getCurrentDirectory();
    }

    public void setCurrentDirectory(File currentDirectory) {
        gradlePluginLord.setCurrentDirectory(currentDirectory);
    }

    /**
     * Call this to add one of your own tabs to this. You can call this at any time.
     *
     * @param index where to add the tab
     * @param gradleTab the tab to add
     */
    public void addGradleTab(int index, GradleTab gradleTab) {
        gradlePanel.addGradleTab(index, gradleTab);
    }

    /**
     * Call this to remove one of your own tabs from this.
     *
     * @param gradleTab the tab to remove
     */
    public void removeGradleTab(GradleTab gradleTab) {
        gradlePanel.removeGradleTab(gradleTab);
    }

    /**
     * @return the total number of tabs.
     */
    public int getGradleTabCount() {
        return gradlePanel.getGradleTabCount();
    }

    /**
     * @param index the index of the tab
     * @return the name of the tab at the specified index.
     */
    public String getGradleTabName(int index) {
        return gradlePanel.getGradleTabName(index);
    }

    public SwingGradleExecutionWrapper getSwingGradleWrapper() {
        return swingGradleWrapper;
    }

    public GradlePluginLord getGradlePluginLord() {
        return gradlePluginLord;
    }
}