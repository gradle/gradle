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
import org.gradle.gradleplugin.userinterface.swing.generic.tabs.*;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a tabbed pane meant to handle several tabs of gradle-related things. To use this, instantiate it, place it some Swing container (dialog, frame), then call aboutToShow() before you show the
 * parent container. You can also add your own tabs to this (just call addGradleTab before calling aboutToShow()). When you shut down, call aboutToClose() before doing so.
 */
public class MainGradlePanel extends JPanel {
    private static final String CURRENT_TAB = "current-tab";
    private static final String MAIN_PANEL = "main_panel";

    private GradlePluginLord gradlePluginLord;

    private SettingsNode settings;
    private AlternateUIInteraction alternateUIInteraction;

    private List<GradleTab> gradleTabs = new ArrayList<GradleTab>();

    private JTabbedPane tabbedPane;
    private SetupTab setupTab;

    public MainGradlePanel(GradlePluginLord gradlePluginLord, OutputUILord outputUILord, SettingsNode settings, AlternateUIInteraction alternateUIInteraction) {
        this.alternateUIInteraction = alternateUIInteraction;
        this.gradlePluginLord = gradlePluginLord;
        this.settings = settings;
        addDefaultTabs(outputUILord, alternateUIInteraction);
    }

    private void addDefaultTabs(OutputUILord outputUILord, AlternateUIInteraction alternateUIInteraction) {
        //we'll give each tab their own settings node just so we don't have to worry about collisions.
        gradleTabs.add(new TaskTreeTab(gradlePluginLord, settings.addChildIfNotPresent("task-tab"), alternateUIInteraction));
        gradleTabs.add(new FavoriteTasksTab(gradlePluginLord, settings.addChildIfNotPresent("favorites-tab")));
        gradleTabs.add(new CommandLineTab(gradlePluginLord, settings.addChildIfNotPresent("command_line-tab")));
        setupTab = new SetupTab(gradlePluginLord, outputUILord, settings.addChildIfNotPresent("setup-tab"));
        gradleTabs.add(setupTab);
    }

    private int getGradleTabIndex(Class soughtClass) {
        for (int index = 0; index < gradleTabs.size(); index++) {
            GradleTab gradleTab = gradleTabs.get(index);
            if (gradleTab.getClass() == soughtClass) {
                return index;
            }
        }
        return -1;
    }

    public int getGradleTabIndex(String name) {
        if (name != null) {
            for (int index = 0; index < gradleTabs.size(); index++) {
                GradleTab gradleTab = gradleTabs.get(index);
                if (name.equals(gradleTab.getName())) {
                    return index;
                }
            }
        }
        return -1;
    }

    /**
     * @return the currently selected tab
     */
    public int getCurrentGradleTab() {
        return tabbedPane.getSelectedIndex();
    }

    public void setCurrentGradleTab(int index) {
        if (index >= 0 && index < getGradleTabCount()) {
            tabbedPane.setSelectedIndex(index);
        }
    }

    /**
     * Call this to add one of your own tabs to this. You must call this before you call aboutToShow.
     */
    public void addGradleTab(int index, GradleTab gradleTab) {
        //this can ultimately be called via external APIs so let's add a little extra error checking.
        if (index < 0) {
            index = 0;
        }
        if (index > gradleTabs.size()) {
            index = gradleTabs.size();
        }

        gradleTabs.add(index, gradleTab);

        if (tabbedPane != null) {   //if we've already displayed the tabs, we'll need to manually add it now to the tabbed pane.
            addGradleTabToTabbedPane(index, gradleTab);
        }
    }

    //this adds the tab. This is only to be used when adding a tab after the tabbed
    //pane has already been displayed and populated with tabs.
    private void addGradleTabToTabbedPane(int index, GradleTab gradleTab) {
        tabbedPane.add(gradleTab.createComponent(), index);
        tabbedPane.setTitleAt(index, gradleTab.getName());
    }

    public void removeGradleTab(GradleTab gradleTab) {
        int existingIndex = gradleTabs.indexOf(gradleTab);
        if (existingIndex == -1) {
            return;
        }

        gradleTabs.remove(gradleTab);

        tabbedPane.remove(existingIndex);

        tabbedPane.invalidate();
        tabbedPane.revalidate();
        tabbedPane.repaint();
    }

    /**
     * @return the total number of tabs.
     */
    public int getGradleTabCount() {
        return gradleTabs.size();
    }

    /**
     * @param index the index of the tab
     * @return the name of the tab at the specified index.
     */
    public String getGradleTabName(int index) {
        return gradleTabs.get(index).getName();
    }

    /**
     * This is called when this about to displayed. Do any kind of initialization you need to do here.
     */
    public void aboutToShow() {
        setupUI();

        for (GradleTab gradleTab : gradleTabs) {
            gradleTab.aboutToShow();
        }
    }

    /**
     * Notification that we're about to be closed. Here we're going to save our current settings.
     */
    public void aboutToClose() {
    }

    private void setupUI() {
        setLayout(new BorderLayout());

        tabbedPane = new JTabbedPane();
        add(tabbedPane, BorderLayout.CENTER);

        addTabs();

        restoreLastTab();

        //add a listener so we can store the current tab when it changes.
        tabbedPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int selection = tabbedPane.getSelectedIndex();
                if (selection >= 0 && selection < gradleTabs.size()) {
                    SettingsNode rootNode = settings.addChildIfNotPresent(MAIN_PANEL);
                    rootNode.setValueOfChild(CURRENT_TAB, gradleTabs.get(selection).getName());
                }
            }
        });
    }

    private void restoreLastTab() {
        //if they're not setup, make the setup tab visible first.
        if (!gradlePluginLord.isSetupComplete()) {
            int tabToSelect = getGradleTabIndex(SetupTab.class);
            if (tabToSelect != -1) {
                tabbedPane.setSelectedIndex(tabToSelect);
            }
        } else {  //otherwise, try to get the last-used tab
            int lastTabIndex = -1;

            //all this is to just restore the last selected tab
            SettingsNode rootNode = settings.getChildNode(MAIN_PANEL);
            if (rootNode != null) {
                String lastTabName = rootNode.getValueOfChild(CURRENT_TAB, "");
                lastTabIndex = getGradleTabIndex(lastTabName);
            }

            if (lastTabIndex != -1) {
                tabbedPane.setSelectedIndex(lastTabIndex);
            }
        }
    }

    private void addTabs() {
        for (GradleTab gradleTab : gradleTabs) {
            tabbedPane.add(gradleTab.getName(), gradleTab.createComponent());
        }
    }

    /**
     * This adds the specified component to the setup panel. It is added below the last 'default' item. You can only add 1 component here, so if you need to add multiple things, you'll have to handle
     * adding that to yourself to the one component.
     *
     * @param component the component to add.
     */
    public void setCustomPanelToSetupTab(JComponent component) {
        setupTab.setCustomPanel(component);
    }
}
