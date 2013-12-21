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
package org.gradle.gradleplugin.userinterface.swing.generic.filter;

import org.gradle.foundation.visitors.AllProjectsAndTasksVisitor;
import org.gradle.foundation.visitors.UniqueNameProjectAndTaskVisitor;
import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.filters.BasicFilterEditor;
import org.gradle.gradleplugin.foundation.filters.BasicProjectAndTaskFilter;
import org.gradle.gradleplugin.userinterface.swing.generic.SwingExportInteraction;
import org.gradle.gradleplugin.userinterface.swing.generic.SwingImportInteraction;
import org.gradle.gradleplugin.userinterface.swing.generic.Utility;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * This dialog allows you to edit what tasks and projects are visible when a filter is enabled. Filters are used to weed out rarely-used things to make finding things easier.
 */
public class ProjectAndTaskFilterDialog {
    private JDialog dialog;
    private JPanel mainPanel;
    private TaskFilterEditorPanel taskFilterEditorPanel;
    private ProjectFilterEditorPanel projectFilterEditorPanel;

    private JCheckBox filterOutTasksWithNoDescriptionCheckBox;

    private BasicFilterEditor editor;
    private GradlePluginLord gradlePluginLord;

    private boolean saveResults;

    public ProjectAndTaskFilterDialog(Window parent, GradlePluginLord gradlePluginLord) {
        this.gradlePluginLord = gradlePluginLord;
        this.dialog = Utility.createDialog(parent, "Filter", true);

        setupUI();
    }

    /**
     * Call this to start editing the given filter.
     *
     * @param filter the filter to edit
     * @return a filter if the user OKs the changes, null if they cancel
     */
    public BasicProjectAndTaskFilter show(BasicProjectAndTaskFilter filter) {
        this.editor = new BasicFilterEditor(filter);

        if (mainPanel == null) {
            setupUI();
        }

        populate();

        taskFilterEditorPanel.enableAppropriately();
        projectFilterEditorPanel.enableAppropriately();

        dialog.setVisible(true);

        if (this.saveResults) {
            return editor.createFilter();
        }

        return null;
    }

    private void setupUI() {
        mainPanel = new JPanel(new BorderLayout());
        dialog.getContentPane().add(mainPanel);

        mainPanel.add(createOptionsPanel(), BorderLayout.NORTH);
        mainPanel.add(createCenterPanel(), BorderLayout.CENTER);
        mainPanel.add(createOkCancelPanel(), BorderLayout.SOUTH);

        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                close(false);
            }
        });

        dialog.setSize(600, 750);
        dialog.setLocationRelativeTo(dialog.getParent());
    }

    private Component createOptionsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        JButton importButton = new JButton(new AbstractAction("Import...") {
            public void actionPerformed(ActionEvent e) {
                importFilter();
            }
        });

        JButton exportButton = new JButton(new AbstractAction("Export...") {
            public void actionPerformed(ActionEvent e) {
                exportFilter();
            }
        });

        filterOutTasksWithNoDescriptionCheckBox = new JCheckBox(new AbstractAction("Hide Tasks With No Description") {
            public void actionPerformed(ActionEvent e) {
                filterOutTasksWithNoDescription();
            }
        });

        panel.add(filterOutTasksWithNoDescriptionCheckBox);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(importButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(exportButton);
        panel.add(Box.createHorizontalGlue());

        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        return panel;
    }

    private Component createOkCancelPanel() {
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

    /**
     * This creates the two list panels. This may seem odd, but I'm putting each of them into a BoxLayout then inside another BorderLayout. This is to make each of them as large as they can be and
     * divide the space evenly between them.
     */
    private Component createCenterPanel() {
        JPanel outterPanel = new JPanel();
        outterPanel.setLayout(new BoxLayout(outterPanel, BoxLayout.Y_AXIS));

        JPanel projectPanel = new JPanel(new BorderLayout());
        JPanel taskPanel = new JPanel(new BorderLayout());

        projectPanel.add(createProjectPanel(), BorderLayout.CENTER);
        taskPanel.add(createTasksPanel(), BorderLayout.CENTER);

        projectPanel.setBorder(BorderFactory.createTitledBorder("Projects"));
        taskPanel.setBorder(BorderFactory.createTitledBorder("Tasks"));

        outterPanel.add(projectPanel);
        outterPanel.add(Box.createVerticalStrut(10));
        outterPanel.add(taskPanel);

        return outterPanel;
    }

    private Component createTasksPanel() {
        taskFilterEditorPanel = new TaskFilterEditorPanel();

        return taskFilterEditorPanel.getComponent();
    }

    private Component createProjectPanel() {
        projectFilterEditorPanel = new ProjectFilterEditorPanel();

        return projectFilterEditorPanel.getComponent();
    }

    private void close(boolean saveResults) {
        this.saveResults = saveResults;
        dialog.setVisible(false);
    }

    /**
     * This imports a filter from a file.
     */
    private void importFilter() {
        if (editor.importFromFile(new SwingImportInteraction(dialog))) {
            taskFilterEditorPanel.getComponent().repaint();
            projectFilterEditorPanel.getComponent().repaint();
        }
    }

    /**
     * This exports a filter to a file.
     */
    private void exportFilter() {
        editor.exportToFile(new SwingExportInteraction(dialog));
    }

    /**
     * Populates our lists. We'll use a visitor to build up a list of unique names of projects and tasks. Then we'll sort them and add them to each filter editor panel.
     */
    private void populate() {
        UniqueNameProjectAndTaskVisitor visitor = new UniqueNameProjectAndTaskVisitor();

        AllProjectsAndTasksVisitor.visitProjectAndTasks(gradlePluginLord.getProjects(), visitor, null);

        List<String> taskNames = visitor.getSortedTaskNames();
        List<String> projectNames = visitor.getSortedProjectNames();

        taskFilterEditorPanel.populate(taskNames);
        projectFilterEditorPanel.populate(projectNames);

        filterOutTasksWithNoDescriptionCheckBox.setSelected(editor.filterOutTasksWithNoDescription());
    }

    private class TaskFilterEditorPanel extends AbstractFilterEditorPanel {
        protected boolean isAllowed(String item) {
            return editor.doesAllowTask(item);
        }

        protected void hideSelected(List<String> selection) {
            editor.hideTasksByName(selection);
        }

        protected void showSelected(List<String> selection) {
            editor.showTasksByName(selection);
        }
    }

    private class ProjectFilterEditorPanel extends AbstractFilterEditorPanel {
        protected boolean isAllowed(String item) {
            return editor.doesAllowProject(item);
        }

        protected void hideSelected(List<String> selection) {
            editor.hideProjectsByName(selection);
        }

        protected void showSelected(List<String> selection) {
            editor.showProjectsByName(selection);
        }
    }

    private void filterOutTasksWithNoDescription() {
        editor.setFilterOutTasksWithNoDescription(filterOutTasksWithNoDescriptionCheckBox.isSelected());
        taskFilterEditorPanel.getComponent().repaint();
        projectFilterEditorPanel.getComponent().repaint();
    }
}
