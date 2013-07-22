/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.gradleplugin.userinterface.swing.generic.filter;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This panel displays something that is filtered. Its really just a list with show/hide buttons. You populate it with whatever you like (in String form).
 */
public abstract class AbstractFilterEditorPanel {
    private JPanel mainPanel;
    private DefaultListModel model;
    private JList list;

    private JButton hideButton;
    private JButton showButton;

    public AbstractFilterEditorPanel() {
        setupUI();
    }

    public JComponent getComponent() {
        return mainPanel;
    }

    private void setupUI() {
        mainPanel = new JPanel(new BorderLayout());

        mainPanel.add(createOptionsPanel(), BorderLayout.NORTH);
        mainPanel.add(createListPanel(), BorderLayout.CENTER);
    }

    private Component createOptionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        hideButton = new JButton(new AbstractAction("Hide") {
            public void actionPerformed(ActionEvent e) {
                hideSelected();
            }
        });

        showButton = new JButton(new AbstractAction("Show") {
            public void actionPerformed(ActionEvent e) {
                showSelected();
            }
        });

        panel.add(showButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(hideButton);
        panel.add(Box.createHorizontalGlue());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        return panel;
    }

    private Component createListPanel() {
        model = new DefaultListModel();
        list = new JList(model);

        list.setCellRenderer(new FilterRenderer());

        list.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    enableAppropriately();
                }
            }
        });

        return new JScrollPane(list);
    }

    private class FilterRenderer extends DefaultListCellRenderer {
        private Color defaultForegroundColor;

        private FilterRenderer() {
            defaultForegroundColor = getForeground();
        }

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            setIcon(null);  //just remove the icon entirely

            boolean isAllowed = isAllowed((String) value);

            if (isAllowed) {
                setForeground(defaultForegroundColor);
            } else {
                setForeground(Color.red);
            }

            return component;
        }
    }

    /**
     * Implement this to determine if this item is filtered or not. This is used by the list to indicate if this item is filtered or not.
     *
     * @param item the item in question
     * @return true if its filtered, false if not.
     */
    protected abstract boolean isAllowed(String item);

    public void enableAppropriately() {
        boolean isShowEnabled = false;
        boolean isHideEnabled = false;

        List<String> selectedObjects = getSelectedValues();
        if (selectedObjects.isEmpty()) {
            isShowEnabled = false;
            isHideEnabled = false;
        } else {  //we've got something selected, figure out how things should be enabled based on the state of what is selected.
            StateHolder stateHolder = new StateHolder();
            determineShowHideEnabledState(stateHolder, selectedObjects);

            //now reverse them. show is enabled, if hidden things are present.
            isShowEnabled = stateHolder.containsHiddenObjects;
            isHideEnabled = stateHolder.containsShownObjects;
        }

        showButton.setEnabled(isShowEnabled);
        hideButton.setEnabled(isHideEnabled);
    }

    /**
     * This determines the state of the show hide buttons. We only want to enable one if its appropriate. Because we have to handle multiple selection, we look for both hidden and shown items. We stop
     * as soon as we find both or we're done with the list.
     *
     * @param stateHolder where we store our state.
     * @param selectedObjects the objects to search.
     */
    protected void determineShowHideEnabledState(StateHolder stateHolder, List<String> selectedObjects) {
        Iterator<String> iterator = selectedObjects.iterator();

        //iterate through them all or until we've found both a hidden and shown object.
        while (iterator.hasNext() && (!stateHolder.containsHiddenObjects || !stateHolder.containsShownObjects)) {
            String object = iterator.next();
            if (isAllowed(object)) {
                stateHolder.containsShownObjects = true;
            } else {
                stateHolder.containsHiddenObjects = true;
            }
        }
    }

    /**
     * Just a holder for 2 variables.
     */
    protected class StateHolder {
        boolean containsHiddenObjects;
        boolean containsShownObjects;
    }

    protected List<String> getSelectedValues() {
        Object[] objects = list.getSelectedValues();
        if (objects == null || objects.length == 0) {
            return Collections.emptyList();
        }

        List<String> nodes = new ArrayList<String>();

        for (int index = 0; index < objects.length; index++) {
            Object object = objects[index];
            nodes.add((String) object);
        }

        return nodes;
    }

    private void hideSelected() {
        List<String> selection = getSelectedValues();

        hideSelected(selection);

        enableAppropriately();  //now update the buttons to reflect the change

        list.repaint();
    }

    protected abstract void hideSelected(List<String> selection);

    private void showSelected() {
        List<String> selection = getSelectedValues();

        showSelected(selection);

        enableAppropriately();  //now update the buttons to reflect the change

        list.repaint();
    }

    protected abstract void showSelected(List<String> selection);

    public void populate(List<String> items) {
        model.clear();

        Iterator<String> iterator = items.iterator();

        while (iterator.hasNext()) {
            String item = iterator.next();
            model.addElement(item);
        }
    }
}
