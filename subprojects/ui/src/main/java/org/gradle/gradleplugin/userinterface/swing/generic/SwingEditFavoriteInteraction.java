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

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;

/**
 * This edits the properties of a single favorite task.
 */
public class SwingEditFavoriteInteraction implements FavoritesEditor.EditFavoriteInteraction {

    public enum SynchronizeType {
        OnlyIfAlreadySynchronized,   //the the display name in synch with the command only if they are already synchronized (and it can be overridden by the user if they change the display name manually)
        Never                        //Do not attempt to keep them in synch
    };

    private JDialog dialog;
    private JTextField fullCommandLineTextField;
    private JTextField displayNameTextField;
    private JCheckBox alwaysShowOutputCheckBox;
    private boolean saveResults;

    private SynchronizeType synchronizeType;

    private DocumentListener synchronizationDocumentListener;
    private KeyAdapter synchronizationKeyAdapter;

    //pass in true to synchronizeDisplayNameWithCommand for new favorites.

    public SwingEditFavoriteInteraction(Window parent, String title, SynchronizeType synchronizeType) {
        this.synchronizeType = synchronizeType;
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


        //create some listeners that we can use for synchronization purposes.
        synchronizationDocumentListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent documentEvent) {
                setDisplayNameTextToCommandLineText();
            }

            public void removeUpdate(DocumentEvent documentEvent) {
                setDisplayNameTextToCommandLineText();
            }

            public void changedUpdate(DocumentEvent documentEvent) {
                setDisplayNameTextToCommandLineText();
            }
        };

        synchronizationKeyAdapter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent keyEvent) {  //the user typed something. Remove the document listener
                fullCommandLineTextField.getDocument().removeDocumentListener(synchronizationDocumentListener);
                displayNameTextField.removeKeyListener(synchronizationKeyAdapter); //and we don't need this anymore either
            }
        };

        return panel;
    }

    /**
     * This synchronizes the display name with the command line (based on whether or not we should synchronize). This is so when you're adding a new favorite, the display name is automatic. If you
     * type anything in the display name, we'll cancel synchronization. This can be called repeatedly for this dialog so it resets it rather than just sets it up once.
     *
     * @param favoriteTask the task currently being edited.
     */
    private void synchronizeDisplayNameWithCommand(FavoritesEditor.EditibleFavoriteTask favoriteTask) {

        if (synchronizeType == SynchronizeType.Never || !favoriteTask.isDisplayNameAndFullCommandSynchronized()) {
            fullCommandLineTextField.getDocument().removeDocumentListener(synchronizationDocumentListener);
            displayNameTextField.removeKeyListener(synchronizationKeyAdapter);
        } else {
            fullCommandLineTextField.getDocument().addDocumentListener(synchronizationDocumentListener);
            displayNameTextField.addKeyListener(synchronizationKeyAdapter);
        }
    }

    private void setDisplayNameTextToCommandLineText() {
        try {
            String text = fullCommandLineTextField.getDocument().getText(0,
                    fullCommandLineTextField.getDocument().getLength());
            displayNameTextField.setText(text);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private Component createButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        JButton okButton = new JButton(new AbstractAction("OK") {
            public void actionPerformed(ActionEvent e) {
                close(true);
            }
        });

        //make OK the default button
        dialog.getRootPane().setDefaultButton(okButton);

        JButton cancelButton = new JButton(new AbstractAction("Cancel") {
            public void actionPerformed(ActionEvent e) {
                close(false);
            }
        });

        //equate escape with cancle
        dialog.getRootPane().registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                close(false);
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

    private void close(boolean saveResults) {
        this.saveResults = saveResults;
        dialog.setVisible(false);
    }

    public boolean editFavorite(FavoritesEditor.EditibleFavoriteTask favoriteTask) {
        saveResults = false;

        fullCommandLineTextField.setText(favoriteTask.fullCommandLine);
        displayNameTextField.setText(favoriteTask.displayName);
        alwaysShowOutputCheckBox.setSelected(favoriteTask.alwaysShowOutput);

        synchronizeDisplayNameWithCommand(favoriteTask);

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
        if (dialog.isVisible()) {
            JOptionPane.showMessageDialog(dialog, error);
        } else {
            JOptionPane.showMessageDialog(dialog.getParent(), error);
        }
    }
}
