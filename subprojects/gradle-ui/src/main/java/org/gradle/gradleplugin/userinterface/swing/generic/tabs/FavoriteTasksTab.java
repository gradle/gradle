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
import org.gradle.gradleplugin.foundation.favorites.FavoriteTask;
import org.gradle.gradleplugin.foundation.favorites.FavoritesEditor;
import org.gradle.gradleplugin.foundation.settings.SettingsNode;
import org.gradle.gradleplugin.userinterface.swing.generic.SwingEditFavoriteInteraction;
import org.gradle.gradleplugin.userinterface.swing.generic.SwingExportInteraction;
import org.gradle.gradleplugin.userinterface.swing.generic.SwingGradleExecutionWrapper;
import org.gradle.gradleplugin.userinterface.swing.generic.SwingImportInteraction;
import org.gradle.gradleplugin.userinterface.swing.generic.Utility;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 This displays a list of favorites and allows the user to add/remove items as
 well as change their order.

 @author mhunsicker
  */
public class FavoriteTasksTab implements GradleTab, GradlePluginLord.GeneralPluginObserver, FavoritesEditor.FavoriteTasksObserver {
    private GradlePluginLord gradlePluginLord;
    private FavoritesEditor favoritesEditor;

    private SwingGradleExecutionWrapper swingGradleWrapper;

    private SettingsNode settingsNode;

    private JPanel mainPanel;
    private DefaultListModel model;
    private JList list;

    private JPopupMenu popupMenu;
    private JMenuItem executeMenuItem;
    private JMenuItem removeFavoritesMenuItem;

    private JButton executeButton;
    private JButton addButton;
    private JButton editButton;
    private JButton removeButton;
    private JButton moveUpButton;
    private JButton moveDownButton;
    private JButton importButton;
    private JButton exportButton;

    public FavoriteTasksTab(GradlePluginLord gradlePluginLord, SwingGradleExecutionWrapper swingGradleWrapper, SettingsNode settingsNode) {
        this.gradlePluginLord = gradlePluginLord;
        this.swingGradleWrapper = swingGradleWrapper;
        this.settingsNode = settingsNode;

        this.favoritesEditor = gradlePluginLord.getFavoritesEditor();

        gradlePluginLord.addGeneralPluginObserver(this, true);
        favoritesEditor.addFavoriteTasksObserver(this, true);

        //read in our settings before we populate things
        favoritesEditor.serializeIn(settingsNode);
    }

    public String getName() {
        return "Favorites";
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
        populate();
    }

    private void setupUI() {
        mainPanel = new JPanel(new BorderLayout());

        mainPanel.add(createButtonPanel(), BorderLayout.NORTH);
        mainPanel.add(createListPanel(), BorderLayout.CENTER);

        setupPopupMenu();
    }

    private Component createButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        executeButton = Utility.createButton(getClass(), "execute.png", "Execute the selected command", new AbstractAction("Execute") {
            public void actionPerformed(ActionEvent e) {
                executeSelectedTask();
            }
        });

        addButton = Utility.createButton(getClass(), "add.png", "Adds a new favorite gradle command", new AbstractAction("Add...") {
            public void actionPerformed(ActionEvent e) {
                addTask();
            }
        });

        editButton = Utility.createButton(getClass(), "edit.png", "Edit the selected favorite", new AbstractAction("Edit...") {
            public void actionPerformed(ActionEvent e) {
                editTask();
            }
        });

        removeButton = Utility.createButton(getClass(), "remove.png", "Delete the selected favorite", new AbstractAction("Remove") {
            public void actionPerformed(ActionEvent e) {
                removeSelectedFavorites();
            }
        });

        moveUpButton = Utility.createButton(getClass(), "move-up.png", "Moves the selected favorites up", new AbstractAction("Move Up") {
            public void actionPerformed(ActionEvent e) {
                favoritesEditor.moveFavoritesBefore(getSelectedFavoriteTasks());
            }
        });

        moveDownButton = Utility.createButton(getClass(), "move-down.png", "Moves the selected favorites down", new AbstractAction("Move Down") {
            public void actionPerformed(ActionEvent e) {
                favoritesEditor.moveFavoritesAfter(getSelectedFavoriteTasks());
            }
        });

        importButton = Utility.createButton(getClass(), "import.png", "Imports current favorite settings", new AbstractAction("Import...") {
            public void actionPerformed(ActionEvent e) {
                importFavorites();
            }
        });

        exportButton = Utility.createButton(getClass(), "export.png", "Exports current favorite settings", new AbstractAction("Export...") {
            public void actionPerformed(ActionEvent e) {
                exportFavorites();
            }
        });

        panel.add(executeButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(addButton);
        panel.add(Box.createHorizontalStrut(5));
        panel.add(editButton);
        panel.add(Box.createHorizontalStrut(5));
        panel.add(removeButton);
        panel.add(Box.createHorizontalStrut(5));
        panel.add(moveUpButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(moveDownButton);
        panel.add(Box.createHorizontalGlue());
        panel.add(importButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(exportButton);

        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        return panel;
    }

    private Component createListPanel() {
        model = new DefaultListModel();
        list = new JList(model);

        list.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2)
                    executeSelectedTask();
                else
                   if( e.getButton() == MouseEvent.BUTTON3 )
                     handleRightClick( e );
            }
        });

        list.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting())
                    enableThingsAppropriately();
            }
        });

        return new JScrollPane(list);
    }

    private void populate() {
        model.clear();

        Iterator<FavoriteTask> taskIterator = favoritesEditor.getFavoriteTasks().iterator();
        while (taskIterator.hasNext()) {
            FavoriteTask favoriteTask = taskIterator.next();
            model.addElement(favoriteTask);
        }
    }


  private void handleRightClick( MouseEvent e )
  {
     Point point = e.getPoint();
     int index = list.locationToIndex( point );
     if( index != -1 )  //all of this is because the JList won't select things on right-click. Which means you won't be acting upon what you think you're acting upon.
     {
        if( !list.isSelectedIndex( index ) )
        {
           if( Utility.isCTRLDown( e.getModifiersEx() ) )
              list.addSelectionInterval( index, index ); //the CTRL key is down, just add this to our selection
           else
              list.setSelectedIndex( index );            //the CTRL key is not down, just replace the selection
           //we're not handling SHIFT! Nor are we handling OS X.
        }
     }
     enableThingsAppropriately();
     popupMenu.show( list, point.x, point.y );
  }

    /**
    Notification that we're about to reload the projects and tasks.
    */
    public void startingProjectsAndTasksReload() {
        //we don't really care.
    }

    /**
       Notification that the projects and tasks have been reloaded. You may want
       to repopulate or update your views.
       @param wasSuccessful true if they were successfully reloaded. False if an
                            error occurred so we no longer can show the projects
                            and tasks (probably an error in a .gradle file).
    */
    public void projectsAndTasksReloaded(boolean wasSuccessful) {
        //We need to repaint in case any are in error now, or no longer in error.
        list.repaint();

        //and possible change what is enabled
        enableThingsAppropriately();
    }

    /**
    Notification that the favorites list has changed. We'll repopulate and then
    save our changes immediately. The save is useful for IDE integration where we
    don't control the settings.
    */
    public void favoritesChanged() {
        populate();
        favoritesEditor.serializeOut(settingsNode);
    }

    /**
    Notification that the favorites were re-ordered. We'll update our list and
    save our changes immediately. The save is useful for IDE integration where we
    don't control the settings.

    @param favoritesReordered the favorites that were reordered
    */
    public void favoritesReordered(List<FavoriteTask> favoritesReordered) {
        Object[] previouslySelectedObjects = list.getSelectedValues();

        populate();

        list.clearSelection();
        //now go re-select the things that were moved
        if (previouslySelectedObjects != null)
            for (int index = 0; index < previouslySelectedObjects.length; index++) {
                Object previouslySelectedObject = previouslySelectedObjects[index];
                int listIndex = model.indexOf(previouslySelectedObject);
                if (listIndex != -1)
                    list.addSelectionInterval(listIndex, listIndex);
            }

        favoritesEditor.serializeOut(settingsNode);
    }

    private void setupPopupMenu() {
        popupMenu = new JPopupMenu();

        executeMenuItem = new JMenuItem(new AbstractAction("Execute") {
            public void actionPerformed(ActionEvent e) {
                executeSelectedTask();
            }
        });

        popupMenu.add(executeMenuItem);

        removeFavoritesMenuItem = new JMenuItem(new AbstractAction("Remove") {
            public void actionPerformed(ActionEvent e) {
                removeSelectedFavorites();
            }
        });

        popupMenu.add(removeFavoritesMenuItem);
    }

    private void executeSelectedTask() {
        FavoriteTask favoriteTask = (FavoriteTask) list.getSelectedValue();
        if (favoriteTask != null)
            swingGradleWrapper.executeTaskInThread(favoriteTask.getFullCommandLine(), favoriteTask.getDisplayName(), favoriteTask.alwaysShowOutput(), true, true);
    }

    private void removeSelectedFavorites() {
        List<FavoriteTask> favorites = getSelectedFavoriteTasks();
        favoritesEditor.removeFavorites(favorites);
    }

    private List<FavoriteTask> getSelectedFavoriteTasks() {
        Object[] objects = list.getSelectedValues();
        if (objects == null)
            return Collections.emptyList();

        List<FavoriteTask> favorites = new ArrayList<FavoriteTask>();
        for (int index = 0; index < objects.length; index++)
            favorites.add((FavoriteTask) objects[index]);

        return favorites;
    }

    private FavoriteTask getFirstSelectedFavoriteTask() {
        return (FavoriteTask) list.getSelectedValue();
    }

    /**
       Enables buttons and menu items based on what is selected.
    */
    private void enableThingsAppropriately() {
        Object[] objects = list.getSelectedValues();
        boolean hasSelection = objects != null && objects.length != 0;
        boolean hasSingleSelection = objects != null && objects.length == 1;

        executeMenuItem.setEnabled(hasSelection);
        removeFavoritesMenuItem.setEnabled(hasSelection);

        executeButton.setEnabled(hasSelection);
        removeButton.setEnabled(hasSelection);
        moveUpButton.setEnabled(hasSelection);
        moveDownButton.setEnabled(hasSelection);

        editButton.setEnabled(hasSingleSelection);  //only can edit if a single task is selected
    }

    /**
       This imports favorites from a file.
    */
    private void importFavorites() {
        favoritesEditor.importFromFile(new SwingImportInteraction(SwingUtilities.getWindowAncestor(mainPanel)));
    }

    /**
       This exports the favorites to a file.
    */
    private void exportFavorites() {
        favoritesEditor.exportToFile(new SwingExportInteraction(SwingUtilities.getWindowAncestor(mainPanel)));
    }

    /**
       Call this to prompt the user for a task to add.
    */
    private void addTask() {
        favoritesEditor.addFavorite(new SwingEditFavoriteInteraction(SwingUtilities.getWindowAncestor(mainPanel), "Add Favorite", true ));
    }

    private void editTask() {
        FavoriteTask selectedFavoriteTask = getFirstSelectedFavoriteTask();
        favoritesEditor.editFavorite(selectedFavoriteTask, new SwingEditFavoriteInteraction(SwingUtilities.getWindowAncestor(mainPanel), "Edit Favorite", false ));
    }
}
