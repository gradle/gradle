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

import org.gradle.gradleplugin.foundation.settings.SettingsNode;
import org.gradle.gradleplugin.userinterface.AlternateUIInteraction;
import org.gradle.gradleplugin.userinterface.swing.common.PreferencesAssistant;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * A simple UI for gradle. This is a single panel that can be inserted into a stand-alone application or an IDE. This is meant to hide most of the complexities of gradle. 'single pane' means that both
 * the tabbed pane and the output pane are contained within a single pane that this maintains. Meaning, you add this to a UI and its a self-contained gradle UI. This is opposed to a multi-pane concept
 * where the output would be separated from the tabbed pane.
 */
public class SinglePaneUIInstance extends AbstractGradleUIInstance {
    private static final String SPLITTER_PREFERENCES_ID = "splitter-id";

    private JSplitPane splitter;
    private OutputPanelLord outputPanelLord;

    public SinglePaneUIInstance() {
    }

    public void initialize(SettingsNode settings, AlternateUIInteraction alternateUIInteraction) {

        outputPanelLord = new OutputPanelLord(gradlePluginLord, alternateUIInteraction);

        super.initialize(settings, alternateUIInteraction);
    }

    /**
     * We've overridden this to setup our splitter and our output window.
     */
    @Override
    protected void setupUI() {
        mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(createCenterPanel(), BorderLayout.CENTER);
    }

    public OutputUILord getOutputUILord() {
        return outputPanelLord;
    }

    private Component createCenterPanel() {
        splitter = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        splitter.setTopComponent(createMainGradlePanel());
        splitter.setBottomComponent(outputPanelLord.getMainPanel());

        splitter.setContinuousLayout(true);

        //This little bit of tedium is so we can set our size based on window's size. This listens
        //for when the window is actually shown. It then adds a listen to store the location.
        splitter.addHierarchyListener(new HierarchyListener() {
            public void hierarchyChanged(HierarchyEvent e) {
                if (HierarchyEvent.SHOWING_CHANGED == (e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED)) {
                    splitter.removeHierarchyListener(this); //we only want the first one of these, so remove ourselves as a listener.
                    Window window = SwingUtilities.getWindowAncestor(splitter);
                    if (window != null) {
                        Dimension dimension = window.getSize();
                        int halfHeight = dimension.height / 2; //we'll just make ourselves half the height of the window
                        splitter.setDividerLocation(halfHeight);
                    }
                    PreferencesAssistant.restoreSettings(settings, splitter, SPLITTER_PREFERENCES_ID, SinglePaneUIInstance.class);

                    //Now that we're visible, this is so we save the location when the splitter is moved.
                    splitter.addPropertyChangeListener(new PropertyChangeListener() {
                        public void propertyChange(PropertyChangeEvent evt) {
                            if (JSplitPane.DIVIDER_LOCATION_PROPERTY.equals(evt.getPropertyName())) {
                                PreferencesAssistant.saveSettings(settings, splitter, SPLITTER_PREFERENCES_ID, SinglePaneUIInstance.class);
                            }
                        }
                    });
                }
            }
        });

        splitter.setResizeWeight(1);   //this keeps the bottom the same size when resizing the window. Extra space is added/removed from the top.

        return splitter;
    }
}
