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

import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.favorites.FavoritesEditor;
import org.gradle.gradleplugin.foundation.settings.SettingsNode;
import org.gradle.gradleplugin.userinterface.swing.generic.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * A tab that allows you to just type a straight command line that is sent to Gradle.
 */
public class CommandLineTab implements GradleTab {
    private GradlePluginLord gradlePluginLord;
    private FavoritesEditor favoritesEditor;

    private JPanel mainPanel;
    private JTextField commandLineField;

    private JButton executeButton;
    private JButton addToFavoritesButton;

    public CommandLineTab(GradlePluginLord gradlePluginLord, SettingsNode settingsNode) {
        this.gradlePluginLord = gradlePluginLord;

        this.favoritesEditor = gradlePluginLord.getFavoritesEditor();
    }

    /**
     * @return the name of this tab
     */
    public String getName() {
        return "Command Line";
    }

    /**
     * Notification that this component is about to be shown. Do whatever initialization you choose.
     */
    public void aboutToShow() {

    }

    /**
     * This is where we should create your component.
     *
     * @return the component
     */
    public Component createComponent() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        mainPanel.add(createCommandLinePanel(), BorderLayout.NORTH);
        mainPanel.add(Box.createVerticalGlue(), BorderLayout.CENTER);

        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        return mainPanel;
    }

    private Component createCommandLinePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        commandLineField = new JTextField();

        //make Enter execute the command line.
        commandLineField.registerKeyboardAction(new ExecuteAction(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        //we'll put 'gradle' in from the command line to make it more obvious that its not needed.
        JPanel commandLinePanel = new JPanel();
        commandLinePanel.setLayout(new BoxLayout(commandLinePanel, BoxLayout.X_AXIS));
        commandLinePanel.add(new JLabel("gradle "));
        commandLinePanel.add(commandLineField);

        panel.add(Utility.addLeftJustifiedComponent(new JLabel("Command Line:")));
        panel.add(Box.createVerticalStrut(5));
        panel.add(commandLinePanel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(createButtonPanel());

        return panel;
    }

    private class ExecuteAction extends AbstractAction {
        private ExecuteAction() {
            super("Execute");
        }

        public void actionPerformed(ActionEvent e) {
            executeCommandLine();
        }
    }

    private Component createButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        executeButton = new JButton(new ExecuteAction());

        addToFavoritesButton = new JButton(new AbstractAction("Add To Favorites") {
            public void actionPerformed(ActionEvent e) {
                addToFavorites();
            }
        });

        panel.add(Box.createHorizontalGlue());
        panel.add(executeButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(addToFavoritesButton);

        return panel;
    }

    private void addToFavorites() {
        String commandLineText = commandLineField.getText();
        favoritesEditor.addFavorite(commandLineText, false);
    }

    private void executeCommandLine() {
        String commandLineText = commandLineField.getText();
        gradlePluginLord.addExecutionRequestToQueue(commandLineText, "Command Line");
    }
}
