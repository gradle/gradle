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

import javax.swing.*;
import java.awt.*;

/**
 * A simple UI for gradle. This has two panels that can be inserted into a stand-alone application or an IDE. This is meant to hide most of the complexities of gradle. The two panes are a tabbed pane
 * for executing tasks and an output pane.
 */
public class DualPaneUIInstance extends AbstractGradleUIInstance {
    private OutputPanelLord outputPanelLord;

    public DualPaneUIInstance() {
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
        mainPanel.add(createMainGradlePanel(), BorderLayout.CENTER);
    }

    public OutputUILord getOutputUILord() {
        return outputPanelLord;
    }

    public Component getOutputPanel() {
        return outputPanelLord.getMainPanel();
    }
}
