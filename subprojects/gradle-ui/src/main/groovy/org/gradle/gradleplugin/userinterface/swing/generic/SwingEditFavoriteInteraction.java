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

import org.gradle.gradleplugin.foundation.favorites.FavoritesEditor;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * This edits the properties of a single favorite task.
 *
 * @author mhunsicker
 */
public class SwingEditFavoriteInteraction implements FavoritesEditor.EditFavoriteInteraction {
    private JDialog dialog;
    private JTextField fullCommandLineTextField;
    private JTextField displayNameTextField;
    private JCheckBox alwaysShowOutputCheckBox;
    private boolean saveResults;

    public SwingEditFavoriteInteraction(Window parent, String title) {
        setupUI(parent, title);
    }

    private void setupUI(Window parent, String title) {
        dialog = Utility.createDialog(parent, title, true);

        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                close(false);
            }
        });

        JPanel panel = new JPanel(new BorderLayout());
        dialog.getContentPane().add(panel);

        panel.add(createMainPanel(), BorderLayout.CENTER);
        panel.add(createButtonPanel(), BorderLayout.SOUTH);

        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        dialog.pack();
    }

    private Component createMainPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        fullCommandLineTextField = new JTextField();
        displayNameTextField = new JTextField();
        alwaysShowOutputCheckBox = new JCheckBox("Always Show Live Output");

        panel.add(Utility.addLeftJustifiedComponent(new JLabel("Command Line")));
        panel.add(Utility.addLeftJustifiedComponent(fullCommandLineTextField));
        panel.add(Box.createVerticalStrut(10));
        panel.add(Utility.addLeftJustifiedComponent(new JLabel("Display Name")));
        panel.add(Utility.addLeftJustifiedComponent(displayNameTextField));
        panel.add(Box.createVerticalStrut(10));
        panel.add(Utility.addLeftJustifiedComponent(alwaysShowOutputCheckBox));
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private Component createButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        JButton okButton = new JButton(new AbstractAction("OK") {
            public void actionPerformed(ActionEvent e) {
                close(true);
            }
        });

        JButton cancelButton = new JButton(new AbstractAction("Cancel") {
            public void actionPerformed(ActionEvent e) {
                close(false);
            }
        });

        panel.add(Box.createHorizontalGlue());
        panel.add(okButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(cancelButton);
        panel.add(Box.createHorizontalGlue());

        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));


        return panel;
    }

    private void close(boolean saveResults) {
        this.saveResults = saveResults;
        dialog.setVisible(false);
    }

    public boolean editFavorite(FavoritesEditor.EditibleFavoriteTask favoriteTask) {
        saveResults = false;

        fullCommandLineTextField.setText(favoriteTask.fullCommandLine);
        displayNameTextField.setText(favoriteTask.displayName);
        alwaysShowOutputCheckBox.setSelected(favoriteTask.alwaysShowOutput);

        dialog.pack();
        dialog.setLocationRelativeTo(dialog.getParent());
        dialog.setVisible(true);

        if (saveResults) {
            favoriteTask.fullCommandLine = fullCommandLineTextField.getText();
            favoriteTask.displayName = displayNameTextField.getText();
            favoriteTask.alwaysShowOutput = alwaysShowOutputCheckBox.isSelected();
        }

        return saveResults;
    }

    public void reportError(String error) {
        if (dialog.isVisible())
            JOptionPane.showMessageDialog(dialog, error);
        else
            JOptionPane.showMessageDialog(dialog.getParent(), error);
    }
}
