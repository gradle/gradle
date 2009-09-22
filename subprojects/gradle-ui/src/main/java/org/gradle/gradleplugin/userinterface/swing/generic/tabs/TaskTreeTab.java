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

import org.gradle.foundation.ProjectView;
import org.gradle.foundation.TaskView;
import org.gradle.foundation.CommandLineAssistant;
import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.filters.AllowAllProjectAndTaskFilter;
import org.gradle.gradleplugin.foundation.filters.BasicFilterEditor;
import org.gradle.gradleplugin.foundation.filters.BasicProjectAndTaskFilter;
import org.gradle.gradleplugin.foundation.settings.SettingsNode;
import org.gradle.gradleplugin.userinterface.AlternateUIInteraction;
import org.gradle.gradleplugin.userinterface.swing.generic.SwingGradleExecutionWrapper;
import org.gradle.gradleplugin.userinterface.swing.generic.TaskTreeComponent;
import org.gradle.gradleplugin.userinterface.swing.generic.Utility;
import org.gradle.gradleplugin.userinterface.swing.generic.SwingAddMultipleFavoritesInteraction;
import org.gradle.gradleplugin.userinterface.swing.generic.filter.ProjectAndTaskFilterDialog;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 This displays a tree of projects and tasks.

 @author mhunsicker
  */
public class TaskTreeTab implements GradleTab, GradlePluginLord.GeneralPluginObserver {
    private final Logger logger = Logging.getLogger(TaskTreeTab.class);

    private static final String SHOW_DESCRIPTION = "show-description";

    private JPanel mainPanel;
    private GradlePluginLord gradlePluginLord;
    private SwingGradleExecutionWrapper swingGradleWrapper;
    private AlternateUIInteraction alternateUIInteraction;

    private TaskTreeComponent treeComponent;

    private JPopupMenu popupMenu;
    private JMenuItem addToFavoritesMenuItem;
    private JMenuItem executeMenuItem;
    private JMenuItem executeOnlyThisMenuItem;
    private JMenuItem filterOutMenuItem;
    private JMenuItem editFileMenuItem;

    private JButton refreshButton;
    private JButton executeButton;
    private JToggleButton toggleFilterButton;
    private JButton editFilterButton;

    private JCheckBox showDescriptionCheckBox;

    private BasicFilterEditor editor;

    private boolean isRefreshing = false;

    private Color defaultTreeBackground;
    private Color workingBackgroundColor = UIManager.getDefaults().getColor("Panel.background"); //just something to provide better feedback that we're working.
    private JScrollPane treeScrollPane;

    private SettingsNode settingsNode;

    public TaskTreeTab(GradlePluginLord gradlePluginLord, SwingGradleExecutionWrapper swingGradleWrapper, SettingsNode settingsNode, AlternateUIInteraction alternateUIInteraction) {
        this.gradlePluginLord = gradlePluginLord;
        this.swingGradleWrapper = swingGradleWrapper;
        this.settingsNode = settingsNode;
        this.alternateUIInteraction = alternateUIInteraction;

        gradlePluginLord.addGeneralPluginObserver(this, true);

        initializeFilterEditor();
    }

    /**
       This initializes our filter editor. We create a filter, serialize in our
       settings and then use that to create the editor. Lastly, we add an
       observer to the editor so we can save our changes immediately (useful for
       IDE integration where we don't control the settings).
    */
    private void initializeFilterEditor() {
        BasicProjectAndTaskFilter filter = new BasicProjectAndTaskFilter();
        filter.serializeIn(settingsNode);
        editor = new BasicFilterEditor(filter);

        editor.addFilterEditorObserver(new BasicFilterEditor.FilterEditorObserver() {
            public void filterChanged() {  //whenever changes are made, save them.
                editor.createFilter().serializeOut(settingsNode);
            }
        }, false);
    }

    public String getName() {
        return "Task Tree";
    }

    public Component createComponent() {
        setupUI();

        enableThingsAppropriately();

        return mainPanel;
    }

    /**
       Notification that this component is about to be shown. Do whatever
       initialization you choose.
    */
    public void aboutToShow() {
        resetShowDescription(); //make sure that our setting is pushed to the tree's setting.

        //when we start up, refresh our list.
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (gradlePluginLord.isSetupComplete())
                    refresh();
                else
                    showTextInViewport("Cannot show tasks until configuration is complete. See Setup tab.");
            }
        });
    }

    public void setupUI() {
        mainPanel = new JPanel(new BorderLayout());

        mainPanel.add(createTopPanel(), BorderLayout.NORTH);
        mainPanel.add(createTreePanel(), BorderLayout.CENTER);

        setupPopupMenu();
    }

    private Component createTopPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        refreshButton = Utility.createButton(getClass(), "refresh.png", "Refreshes the task tree", new AbstractAction("Refresh") {
            public void actionPerformed(ActionEvent e) {
                refresh();
            }
        });

        executeButton = Utility.createButton(getClass(), "execute.png", "Execute the selected tasks", new AbstractAction("Execute") {
            public void actionPerformed(ActionEvent e) {
                executeSelectedTasks();
            }
        });

        toggleFilterButton = Utility.createToggleButton( getClass(), "filter.png", "Toggles the view to show either everything or only the filtered items", new AbstractAction("Filter") {
            public void actionPerformed(ActionEvent e) {
                populate();
            }
        });
       
        toggleFilterButton.setSelected(true);

        editFilterButton = Utility.createButton(getClass(), "edit-filter.png", "Edits the filter to control what is visible", new AbstractAction("Edit Filter...") {
            public void actionPerformed(ActionEvent e) {
                configureFilter();
            }
        });

        showDescriptionCheckBox = new JCheckBox("Description", true);
        showDescriptionCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                resetShowDescription();
            }
        });

        showDescriptionCheckBox.setSelected(settingsNode.getValueOfChildAsBoolean(SHOW_DESCRIPTION, showDescriptionCheckBox.isSelected()));

        panel.add(refreshButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(executeButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(toggleFilterButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(showDescriptionCheckBox);
        panel.add(Box.createHorizontalGlue());
        panel.add(editFilterButton);

        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        return panel;
    }

    private Component createTreePanel() {
        treeComponent = new TaskTreeComponent(gradlePluginLord, new TaskTreeComponent.Interaction() {
            public void rightClick(JTree tree, int x, int y) {
                enableThingsAppropriately();
                popupMenu.show(tree, x, y);
            }

            public void taskInvoked(TaskView task, boolean isCtrlKeyDown) {
                if (isCtrlKeyDown)
                    swingGradleWrapper.executeTaskInThread(task, false, "-a");
                else
                    swingGradleWrapper.executeTaskInThread(task, false);
            }

            public void projectInvoked(ProjectView project) {
                executeDefaultTasksInProject(project);
            }
        });

        treeComponent.getTree().addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                enableThingsAppropriately();
            }
        });

        defaultTreeBackground = treeComponent.getTree().getBackground();

        treeScrollPane = new JScrollPane();

        treeComponent.getTree().setBackground(workingBackgroundColor);  //change the color to better indicate that
        showTextInViewport("Has not built projects/tasks yet.");

        return treeScrollPane;
    }

    /**
       Replaces the tree with a label of text. This is used when there's nothing
       in the tree, but perhaps a 'working' or error message.

       @param  text       the text to display
    */
    private void showTextInViewport(String text) {
        treeScrollPane.getViewport().removeAll();

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(Box.createHorizontalGlue());
        panel.add(new JLabel(text));
        panel.add(Box.createHorizontalGlue());

        treeScrollPane.getViewport().add(panel);
        treeScrollPane.revalidate();
    }

    /**
       Puts the tree in the main view. This is used once we've gathered the
       projects and tasks and want to display them in the tree.
    */
    private void showTreeInViewport() {
        treeScrollPane.getViewport().removeAll();
        treeScrollPane.getViewport().add(treeComponent.getTree());
        treeScrollPane.revalidate();
    }


    /**
       Call this to repopulate the tree. Useful if new tasks have been created.
    */
    private void refresh() {
        isRefreshing = true;

        enableThingsAppropriately();

        treeComponent.getTree().setBackground(workingBackgroundColor);
        showTextInViewport("Refreshing projects and tasks.");
        try {
            swingGradleWrapper.refreshTaskTree();
        }
        catch (Exception e) {  //if something bad happens, clear our current state
            logger.error("Refreshing", e);
            isRefreshing = false;
            enableThingsAppropriately();
        }
    }

    /**
       This populates (and repopulates) the tree.
    */
    private void populate() {
        if (toggleFilterButton.isSelected())
            treeComponent.populate(editor.createFilter());
        else
            treeComponent.populate(new AllowAllProjectAndTaskFilter());

        //reset the background to indicate that we're populated
        treeComponent.getTree().setBackground(defaultTreeBackground);

        showTreeInViewport();
    }

    private void executeSelectedTasks(String... additionCommandLineOptions) {
        List<TaskView> taskViews = treeComponent.getSelectedTasks();
        String singleCommandLine = CommandLineAssistant.combineTasks( taskViews, additionCommandLineOptions  );
        if( singleCommandLine == null )
           return;

        swingGradleWrapper.executeTaskInThread( singleCommandLine, singleCommandLine, false, true, true );
    }

    /**
    Notification that we're about to reload the projects and tasks.

    */
    public void startingProjectsAndTasksReload() {
        treeComponent.getTree().setBackground(workingBackgroundColor);
        showTextInViewport("Building projects/tasks.");
    }

    /**
       Notification that the projects and tasks have been reloaded. You may want
       to repopulate or update your views.
       @param wasSuccessful true if they were successfully reloaded. False if an
                            error occurred so we no longer can show the projects
                            and tasks (probably an error in a .gradle file).
    */
    public void projectsAndTasksReloaded(boolean wasSuccessful) {
        isRefreshing = false;
        enableThingsAppropriately();

        if (!wasSuccessful)
            showTextInViewport("Error");
        else
            populate();
    }

    /**
       Builds the popup menu
    */
    private void setupPopupMenu() {
        popupMenu = new JPopupMenu();

        executeMenuItem = new JMenuItem(new AbstractAction("Execute") {
            public void actionPerformed(ActionEvent e) {
                executeSelectedTasks();
            }
        });
        popupMenu.add(executeMenuItem);

        executeOnlyThisMenuItem = new JMenuItem(new AbstractAction("Execute Ignoring Dependencies (-a)") {
            public void actionPerformed(ActionEvent e) {
                executeSelectedTasks("-a");
            }
        });
        popupMenu.add(executeOnlyThisMenuItem);

        popupMenu.addSeparator();

        addToFavoritesMenuItem = new JMenuItem(new AbstractAction("Add To Favorites") {
            public void actionPerformed(ActionEvent e) {
                addSelectedToFavorites();
            }
        });
        popupMenu.add(addToFavoritesMenuItem);

        filterOutMenuItem = new JMenuItem(new AbstractAction("Hide") {
            public void actionPerformed(ActionEvent e) {
                hideSelection();
            }
        });
        popupMenu.add(filterOutMenuItem);

        editFileMenuItem = new JMenuItem(new AbstractAction("Edit File") {
            public void actionPerformed(ActionEvent e) {
                editSelectedFiles();
            }
        });
        popupMenu.add(editFileMenuItem);
    }

    /**
       Enables buttons and menu items based on what is selected.
    */
    private void enableThingsAppropriately() {
        boolean hasSelection = treeComponent.getTree().getSelectionPath() != null;
        boolean canDoThings = !isRefreshing && treeComponent.isPopulated() && hasSelection; //can't be refreshing, is populated, and  hasSelections

        refreshButton.setEnabled(!isRefreshing);

        addToFavoritesMenuItem.setEnabled(canDoThings);
        executeMenuItem.setEnabled(canDoThings);
        executeOnlyThisMenuItem.setEnabled(canDoThings);

        executeButton.setEnabled(canDoThings);

        if (alternateUIInteraction.doesSupportEditingFiles())   //I'll allow this to be dynamic. If we start supporting editing while running (say a user configured a setting to use a specific external tool), then we'll allow it.
        {
            editFileMenuItem.setVisible(true);
            boolean hasProjectsSelected = treeComponent.hasProjectsSelected();
            editFileMenuItem.setEnabled(hasProjectsSelected && canDoThings);
        } else
            editFileMenuItem.setVisible(false);  //just hide it if we don't support this
    }

    /**
       Adds whatever is selected to the favorites.
    */
    private void addSelectedToFavorites() {
       List<TaskView> tasks = treeComponent.getSelectedTasks();

       gradlePluginLord.getFavoritesEditor().addMutlipleFavorites( tasks, false, new SwingAddMultipleFavoritesInteraction( SwingUtilities.getWindowAncestor(mainPanel) ) );
    }

    /**
       This displays a dialog that allows the user to determine what shows up in
       the tree. We give the filter dialog a filter rather than handing it out
       editor so teh user can cancel. That is, the dialog uses its own editor
       which it modifies freely and throws away. This way, if the user cancels,
       we dodon't have to deal with restoring the previous values in our local
       editor.
    */
    private void configureFilter() {
        ProjectAndTaskFilterDialog dialog = new ProjectAndTaskFilterDialog(SwingUtilities.getWindowAncestor(mainPanel), gradlePluginLord);

        BasicProjectAndTaskFilter newFilter = dialog.show(editor.createFilter());
        if (newFilter != null) //if the user didn't cancel...
        {
            editor.initializeFromFilter(newFilter);
            populate();
        }
    }

    /**
       Call this to filter out the currently selected items.
    */
    private void hideSelection() {
        TaskTreeComponent.MultipleSelection multipleSelection = treeComponent.getSelectedProjectsAndTasks();
        if (!multipleSelection.projects.isEmpty() || !multipleSelection.tasks.isEmpty()) {
            editor.hideProjects(multipleSelection.projects);
            editor.hideTasks(multipleSelection.tasks);

            populate(); //unfortunately, we have to repopulate now.
        }
    }

    /**
       This resets whether the description is shown or not based on the check
       box. The tree component does the real work.
    */
    private void resetShowDescription() {
        settingsNode.setValueOfChildAsBoolean(SHOW_DESCRIPTION, showDescriptionCheckBox.isSelected());   //save it immediately
        treeComponent.setShowDescription(showDescriptionCheckBox.isSelected());
    }

    /**
       This opens the selected files. This gets the 'parent' of this to do it
       for us. This facilitates using this inside an IDE (you get the IDE to
       open it).
    */
    private void editSelectedFiles() {
        TaskTreeComponent.MultipleSelection tasks = treeComponent.getSelectedProjectsAndTasks();

        List<File> files = new ArrayList<File>();
        Iterator<ProjectView> iterator = tasks.projects.iterator();
        while (iterator.hasNext()) {
            ProjectView projectView = iterator.next();
            files.add(projectView.getBuildFile());
        }

        if (!files.isEmpty())
            alternateUIInteraction.editFiles(files);
    }


    /**
       This executes all default tasks in the specified project.

       @param  project    the project to execute.
    */
    private void executeDefaultTasksInProject(ProjectView project) {
        Iterator<TaskView> iterator = project.getDefaultTasks().iterator();
        while (iterator.hasNext()) {
            TaskView task = iterator.next();
            swingGradleWrapper.executeTaskInThread(task, false);
        }
    }
}
