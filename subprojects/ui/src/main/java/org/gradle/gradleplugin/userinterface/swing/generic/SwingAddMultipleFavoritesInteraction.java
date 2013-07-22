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

import org.gradle.foundation.TaskView;
import org.gradle.gradleplugin.foundation.favorites.FavoritesEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * This handles prompting the user how to handle adding multiple tasks as favorites.
 */
public class SwingAddMultipleFavoritesInteraction implements FavoritesEditor.AddMultipleFavoritesInteraction {
    private Window parent;

    public SwingAddMultipleFavoritesInteraction(Window parent) {
        this.parent = parent;
    }

    public FavoritesEditor.AddMultipleResult promptUserToCombineTasks(List<TaskView> tasksSample, String singleCommandSample) {
        PromptToCombineTasksDialog dialog = new PromptToCombineTasksDialog();
        return dialog.show(parent, tasksSample, singleCommandSample);
    }

    public class PromptToCombineTasksDialog {
        private JDialog dialog;
        private FavoritesEditor.AddMultipleResult addMultipleResult;
        private JRadioButton separatelyRadioButton;
        private JRadioButton combinedRadioButton;
        private ButtonGroup buttonGroup;

        private JLabel separateLine1;
        private JLabel separateLine2;
        private JLabel separateLine3;

        private JLabel combinedLine1;

        public FavoritesEditor.AddMultipleResult show(Window parent, List<TaskView> tasksSample, String singleCommandSample) {
            setupUI(parent);
            populateValues(tasksSample, singleCommandSample);
            dialog.setVisible(true);
            return this.addMultipleResult;
        }

        /**
         * this populates the dialog's sample values. Most of this function is trying to be very explicit about showing precisely what we're going to do (but for space reasons, we'll only show 3
         * commands in the list.
         */
        private void populateValues(List<TaskView> tasksSample, String singleCommandSample) {
            separatelyRadioButton.setText("Add as separate " + tasksSample.size() + " commands:");

            separateLine1.setText('\"' + tasksSample.get(0).getFullTaskName() + "\",");
            String secondTask = '\"' + tasksSample.get(1).getFullTaskName() + "\"";
            String thirdTask = "";

            if (tasksSample.size() > 2) {
                secondTask += ",";   //add a comma

                thirdTask = '\"' + tasksSample.get(2).getFullTaskName() + "\"";
                if (tasksSample.size() > 3)  //if there are more, show a comma and ellipses
                {
                    thirdTask += ", ... ";
                }
            }

            separateLine2.setText(secondTask);
            separateLine3.setText(thirdTask);
            separateLine3.setVisible(tasksSample.size() > 2);   //only show this if there are more than 2 samples

            combinedLine1.setText('\"' + singleCommandSample + '\"');
        }

        private void setupUI(Window parent) {
            dialog = Utility.createDialog(parent, "Add Multiple Tasks", true);
            dialog.setSize(400, 350);

            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            dialog.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    close(FavoritesEditor.AddMultipleResult.Cancel);
                }
            });

            JPanel panel = new JPanel(new BorderLayout());
            dialog.getContentPane().add(panel);

            panel.add(createMainPanel(), BorderLayout.CENTER);
            panel.add(createButtonPanel(), BorderLayout.SOUTH);

            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            dialog.setLocationRelativeTo(dialog.getParent());
        }

        private Component createMainPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

            separatelyRadioButton = new JRadioButton();
            combinedRadioButton = new JRadioButton("Add as a single command:");
            buttonGroup = new ButtonGroup();

            buttonGroup.add(separatelyRadioButton);
            buttonGroup.add(combinedRadioButton);
            separatelyRadioButton.setSelected(true);

            panel.add(Utility.addLeftJustifiedComponent(new JLabel("How you do want to add multiple tasks?")));
            panel.add(Box.createVerticalStrut(20));
            panel.add(Utility.addLeftJustifiedComponent(separatelyRadioButton));
            panel.add(Box.createVerticalStrut(5));
            panel.add(createSeparateSamplePanel());
            panel.add(Box.createVerticalStrut(20));
            panel.add(Utility.addLeftJustifiedComponent(combinedRadioButton));
            panel.add(Box.createVerticalStrut(5));
            panel.add(createCombinedSamplePanel());
            panel.add(Box.createVerticalGlue());

            return panel;
        }

        private JPanel createSeparateSamplePanel() {
            separateLine1 = new JLabel();   //we'll use at most three samples (actually 2 with ellipses)
            separateLine2 = new JLabel();
            separateLine3 = new JLabel();

            JPanel separateSamplePanel = new JPanel();
            separateSamplePanel.setLayout(new BoxLayout(separateSamplePanel, BoxLayout.Y_AXIS));
            separateSamplePanel.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 0)); //indent it
            separateSamplePanel.add(Utility.addLeftJustifiedComponent(separateLine1));
            separateSamplePanel.add(Utility.addLeftJustifiedComponent(separateLine2));
            separateSamplePanel.add(Utility.addLeftJustifiedComponent(separateLine3));

            return separateSamplePanel;
        }

        private JPanel createCombinedSamplePanel() {
            combinedLine1 = new JLabel();

            JPanel combinedSamplePanel = new JPanel();
            combinedSamplePanel.setLayout(new BoxLayout(combinedSamplePanel, BoxLayout.Y_AXIS));
            combinedSamplePanel.setBorder(BorderFactory.createEmptyBorder(0, 30, 0, 0)); //indent it
            combinedSamplePanel.add(Utility.addLeftJustifiedComponent(combinedLine1));

            return combinedSamplePanel;
        }

        private FavoritesEditor.AddMultipleResult getCurrentSelection() {
            if (separatelyRadioButton.isSelected()) {
                return FavoritesEditor.AddMultipleResult.AddSeparately;
            }
            return FavoritesEditor.AddMultipleResult.AddAsSingleCommand;
        }

        private Component createButtonPanel() {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

            JButton okButton = new JButton(new AbstractAction("OK") {
                public void actionPerformed(ActionEvent e) {
                    close(getCurrentSelection());
                }
            });

            //make OK the default button
            dialog.getRootPane().setDefaultButton(okButton);

            JButton cancelButton = new JButton(new AbstractAction("Cancel") {
                public void actionPerformed(ActionEvent e) {
                    close(FavoritesEditor.AddMultipleResult.Cancel);
                }
            });

            //equate escape with cancle
            dialog.getRootPane().registerKeyboardAction(new ActionListener() {
                public void actionPerformed(ActionEvent actionEvent) {
                    close(FavoritesEditor.AddMultipleResult.Cancel);
                }
            }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

            panel.add(Box.createHorizontalGlue());
            panel.add(okButton);
            panel.add(Box.createHorizontalStrut(10));
            panel.add(cancelButton);
            panel.add(Box.createHorizontalGlue());

            panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

            return panel;
        }

        private void close(FavoritesEditor.AddMultipleResult addMultipleResult) {
            this.addMultipleResult = addMultipleResult;
            dialog.setVisible(false);
        }
    }
}
