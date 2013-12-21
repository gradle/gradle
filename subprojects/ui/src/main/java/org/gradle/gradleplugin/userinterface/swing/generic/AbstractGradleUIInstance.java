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
import org.gradle.gradleplugin.userinterface.swing.generic.tabs.GradleTab;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * A simple UI for gradle that is meant to be embedded into an IDE. This doesn't have it own output since most IDEs have their own mechanism for that.
 */
public abstract class AbstractGradleUIInstance implements BasicGradleUI {
    protected MainGradlePanel gradlePanel;
    protected GradlePluginLord gradlePluginLord;
    protected SettingsNode settings;
    protected AlternateUIInteraction alternateUIInteraction;

    protected JPanel mainPanel;

    public AbstractGradleUIInstance() {
        gradlePluginLord = new GradlePluginLord();
    }

    public void initialize(SettingsNode settings, AlternateUIInteraction alternateUIInteraction) {
        this.settings = settings;
        this.alternateUIInteraction = alternateUIInteraction;

        setupUI();
    }

    public JComponent getComponent() {
        return mainPanel;
    }

    protected void setupUI() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(createMainGradlePanel(), BorderLayout.CENTER);
    }

    protected Component createMainGradlePanel() {
        gradlePanel = new MainGradlePanel(gradlePluginLord, getOutputUILord(), settings, alternateUIInteraction);
        return gradlePanel;
    }

    public abstract OutputUILord getOutputUILord();

    /**
     * Call this whenever you're about to show this panel. We'll do whatever initialization is necessary.
     */
    public void aboutToShow() {
        gradlePanel.aboutToShow();
    }

    /**
     * Call this to deteremine if you can close this pane. if we're busy, we'll ask the user if they want to close.
     *
     * @param closeInteraction allows us to interact with the user
     * @return true if we can close, false if not.
     */
    public boolean canClose(CloseInteraction closeInteraction) {
        if (!gradlePluginLord.isBusy()) {
            return true;
        }

        return closeInteraction.promptUserToConfirmClosingWhileBusy();
    }

    /**
     * Call this before you close the pane. This gives it an opportunity to do cleanup. You probably should call canClose before this. It gives the app a chance to cancel if its busy.
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

    public GradlePluginLord getGradlePluginLord() {
        return gradlePluginLord;
    }

    /**
     * Returns the index of the gradle tab with the specified name.
     *
     * @param name the name of the tab
     * @return the index of the tab or -1 if not found
     */
    public int getGradleTabIndex(String name) {
        return gradlePanel.getGradleTabIndex(name);
    }

    /**
     * @return the currently selected tab
     */
    public int getCurrentGradleTab() {
        return gradlePanel.getCurrentGradleTab();
    }

    /**
     * Makes the specified tab the current tab.
     *
     * @param index the index of the tab.
     */
    public void setCurrentGradleTab(int index) {
        gradlePanel.setCurrentGradleTab(index);
    }

    /*
      This executes the given gradle command.

      @param  commandLineArguments the command line arguments to pass to gradle.
      @param displayName           the name displayed in the UI for this command
   */
    public void executeCommand(String commandLineArguments, String displayName) {
        gradlePluginLord.addExecutionRequestToQueue(commandLineArguments, displayName);
    }

    /**
     * This refreshes the task tree. Useful if you know you've changed something behind gradle's back or when first displaying this UI.
     */
    public void refreshTaskTree() {
        gradlePluginLord.addRefreshRequestToQueue();
    }

    /**
     * This refreshes the task tree. Useful if you know you've changed something behind gradle's back or when first displaying this UI.
     *
     * @param additionalCommandLineArguments additional command line arguments to be passed to gradle when refreshing the task tree.
     */
    public void refreshTaskTree(String additionalCommandLineArguments) {
        gradlePluginLord.addRefreshRequestToQueue(additionalCommandLineArguments);
    }

    /**
     * Determines if commands are currently being executed or not.
     *
     * @return true if we're busy, false if not.
     */
    public boolean isBusy() {
        return gradlePluginLord.isBusy();
    }

    /**
     * This adds the specified component to the setup panel. It is added below the last 'default' item. You can only add 1 component here, so if you need to add multiple things, you'll have to handle
     * adding that to yourself to the one component.
     *
     * @param component the component to add.
     */
    public void setCustomPanelToSetupTab(JComponent component) {
        gradlePanel.setCustomPanelToSetupTab(component);
    }

    /**
     * Sets the font for the output text
     *
     * @param font the new font
     */
    public void setOutputTextFont(Font font) {
        getOutputUILord().setOutputTextFont(font);
    }
}
