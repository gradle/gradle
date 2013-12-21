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
package org.gradle.gradleplugin.foundation.favorites;

import org.gradle.foundation.CommandLineAssistant;
import org.gradle.foundation.TaskView;
import org.gradle.foundation.common.ObserverLord;
import org.gradle.foundation.common.ReorderableList;
import org.gradle.gradleplugin.foundation.DOM4JSerializer;
import org.gradle.gradleplugin.foundation.ExtensionFileFilter;
import org.gradle.gradleplugin.foundation.settings.SettingsNode;
import org.gradle.gradleplugin.foundation.settings.SettingsSerializable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This holds onto and edits favorite tasks. 'Favorite tasks' provides a quick way to run frequently used tasks.
 */
public class FavoritesEditor implements SettingsSerializable {
    private ReorderableList<FavoriteTask> favoriteTasks = new ReorderableList<FavoriteTask>();

    private ObserverLord<FavoriteTasksObserver> favoriteTasksObserverLord = new ObserverLord<FavoriteTasksObserver>();

    public interface FavoriteTasksObserver {
        /**
         * Notification that the favorites list has changed.
         */
        public void favoritesChanged();

        /**
         * Notification that the favorites were re-ordered
         *
         * @param favoritesReordered the favorites that were reordered
         */
        public void favoritesReordered(List<FavoriteTask> favoritesReordered);
    }

    public FavoritesEditor() {
    }

    public List<FavoriteTask> getFavoriteTasks() {
        return Collections.unmodifiableList(favoriteTasks);
    }

    public void addFavoriteTasksObserver(FavoritesEditor.FavoriteTasksObserver observer, boolean inEventQueue) {
        favoriteTasksObserverLord.addObserver(observer, inEventQueue);
    }

    public void removeFavoriteTasksObserver(FavoritesEditor.FavoriteTasksObserver observer) {
        favoriteTasksObserverLord.removeObserver(observer);
    }

    public FavoriteTask getFavorite(String fullCommandLine) {
        Iterator<FavoriteTask> taskIterator = favoriteTasks.iterator();
        while (taskIterator.hasNext()) {
            FavoriteTask favoriteTask = taskIterator.next();

            if (fullCommandLine.equals(favoriteTask.getFullCommandLine())) {
                return favoriteTask;
            }
        }

        return null;
    }

    public FavoriteTask getFavoriteByDisplayName(String displayName) {
        Iterator<FavoriteTask> taskIterator = favoriteTasks.iterator();
        while (taskIterator.hasNext()) {
            FavoriteTask favoriteTask = taskIterator.next();

            if (displayName.equals(favoriteTask.getDisplayName())) {
                return favoriteTask;
            }
        }

        return null;
    }

    public FavoriteTask getFavorite(TaskView task) {
        return getFavorite(task.getFullTaskName());
    }

    /**
     * Fires off a notification that the favorite tasks have changed.
     */
    private void notifyFavoritesChanged() {
        favoriteTasksObserverLord.notifyObservers(new ObserverLord.ObserverNotification<FavoriteTasksObserver>() {
            public void notify(FavoriteTasksObserver observer) {
                observer.favoritesChanged();
            }
        });
    }

    //

    public enum AddMultipleResult {
        AddSeparately, AddAsSingleCommand, Cancel
    }

    /**

     */
    public interface AddMultipleFavoritesInteraction {
        /**
         * This is called when you try to add multiple tasks at once. You should prompt the user whether or not they want to add each task separately or together as one task with multiple commands.
         *
         * @param tasksSample the individual tasks we will add
         * @param singleCommandSample a sample of what the single command would look like
         * @return where or not to combine tasks into a single command line, false to add them as separate commands. See AddMultiple.
         */
        public AddMultipleResult promptUserToCombineTasks(List<TaskView> tasksSample, String singleCommandSample);
    }

    /**
     * Call this to add tasks as favorites. If you pass in multiple tasks, we'll prompt the user whether or not the tasks should be added as one favorite with multiple tasks or separate tasks.
     *
     * @param tasks the tasks to add. Their order in the list becomes the order in the single task if the user chooses this.
     * @param interaction how we interact with the user or other UI.
     */
    public void addMutlipleFavorites(List<TaskView> tasks, boolean alwaysShowOutput, AddMultipleFavoritesInteraction interaction) {
        if (tasks.isEmpty()) {
            return;
        }  //no tasks, bail

        if (tasks.size() == 1) {  //only 1 task. just add it
            addFavorite(tasks.get(0), alwaysShowOutput);
            return;
        }

        //multiple tasks. Ask the user what to do
        String singleCommandLine = CommandLineAssistant.combineTasks(tasks);

        //prompt the user what to do
        AddMultipleResult addMultipleResult = interaction.promptUserToCombineTasks(tasks, singleCommandLine);
        switch (addMultipleResult) {
            case Cancel:
                return;

            case AddSeparately:
                Iterator<TaskView> iterator = tasks.iterator();
                while (iterator.hasNext()) {
                    TaskView task = iterator.next();
                    addFavorite(task, alwaysShowOutput);
                }
                break;

            case AddAsSingleCommand:
                addFavorite(singleCommandLine, alwaysShowOutput);
                break;
        }
    }

    public FavoriteTask addFavorite(TaskView task, boolean alwaysShowOutput) {
        return addFavorite(task.getFullTaskName(), alwaysShowOutput);
    }

    public FavoriteTask addFavorite(String fullCommandLine, boolean alwaysShowOutput) {
        FavoriteTask favorite = addFavorite(fullCommandLine, fullCommandLine, alwaysShowOutput);
        if (favorite != null) {
            notifyFavoritesChanged();
        }

        return favorite;
    }

    public FavoriteTask addFavorite(String fullCommandLine, String displayName, boolean alwaysShowOutput) {
        if ((fullCommandLine == null || fullCommandLine.trim().equals("")) && (displayName == null || displayName.trim().equals("")))    //don't allow someone to add a blank favorite.
        {
            return null;
        }

        FavoriteTask favoriteTask = new FavoriteTask(fullCommandLine, displayName, alwaysShowOutput);
        favoriteTasks.add(favoriteTask);
        return favoriteTask;
    }

    /**
     * Call this to add the specified tasks as favorite tasks
     *
     * @param tasks the task to make a favorite.
     */
    public void addFavorites(List<TaskView> tasks, boolean alwaysShowOutput) {
        boolean addedFavorite = false;

        Iterator<TaskView> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            TaskView task = iterator.next();
            String fullTaskName = task.getFullTaskName();
            if (this.addFavorite(fullTaskName, alwaysShowOutput) != null) {
                addedFavorite = true;
            }
        }

        if (addedFavorite)  //don't notify anyone unless we actually did something.
        {
            favoriteTasksObserverLord.notifyObservers(new ObserverLord.ObserverNotification<FavoritesEditor.FavoriteTasksObserver>() {
                public void notify(FavoritesEditor.FavoriteTasksObserver observer) {
                    observer.favoritesChanged();
                }
            });
        }
    }

    /**
     * Call this to add a favorite that isn't in the task list. This exists because you can add functionality to gradle that isn't really a task.
     *
     * @param addFavoriteInteraction allows us to interact with the user
     * @return the favorite that was added, or null if the user canceled
     */
    public FavoriteTask addFavorite(EditFavoriteInteraction addFavoriteInteraction) {
        FavoriteTask newFavorite = new FavoriteTask("", "", false);
        if (!editInternal(newFavorite, addFavoriteInteraction)) {
            return null;
        }

        favoriteTasks.add(newFavorite);

        notifyFavoritesChanged();
        return newFavorite;
    }

    /**
     * This is what you actually edit when you want to edit a favorite. I wanted the FavoriteTask object to be immutable so the only way to edit it is via this editor. This way any necessary
     * validation or notification will always be performed
     */
    public class EditibleFavoriteTask {
        public String fullCommandLine;
        public String displayName;
        public boolean alwaysShowOutput;

        public EditibleFavoriteTask(FavoriteTask favoriteTask) {
            this(favoriteTask.getFullCommandLine(), favoriteTask.getDisplayName(), favoriteTask.alwaysShowOutput());
        }

        public EditibleFavoriteTask(String fullCommandLine, String displayName, boolean alwaysShowOutput) {
            this.fullCommandLine = fullCommandLine;
            this.displayName = displayName;
            this.alwaysShowOutput = alwaysShowOutput;
        }

        /**
         * Determines if the display name and full command are equal. This is useful when editing a favorite to know if they should be kept synchronized.
         *
         * @return true if they are, false if not.
         */
        public boolean isDisplayNameAndFullCommandSynchronized() {
            if (displayName == null) {
                return fullCommandLine == null;
            }

            return displayName.equals(fullCommandLine);
        }

        @Override
        public String toString() {
            return displayName + " " + fullCommandLine;
        }
    }

    public interface EditFavoriteInteraction extends ValidationInteraction {
        public boolean editFavorite(EditibleFavoriteTask favoriteTask);
    }

    /**
     * Edits the specified favorite task.
     *
     * @param favoriteTask the task to edit.
     * @param editFavoriteInteraction how we interact with the user
     * @return true if we made changes, false if not.
     */
    public boolean editFavorite(FavoriteTask favoriteTask, EditFavoriteInteraction editFavoriteInteraction) {
        if (favoriteTask == null) {
            return false;
        }

        if (favoriteTasks.indexOf(favoriteTask) == -1) {
            return false;
        }  //not our favorite

        if (!editInternal(favoriteTask, editFavoriteInteraction)) {
            return false;
        }

        notifyFavoritesChanged();
        return true;
    }

    /**
     * This edits the specified favorite task. We create a EditableFavoriteTask so the user can trash it and cancel and it won't affect the original. We'll sit in a loop prompting the user to edit it
     * until no errors exist then we'll set the values on the original task.
     *
     * @param favoriteTask the task to edit.
     * @param editFavoriteInteraction how we interact with the user.
     * @return true if we edited it, false if not (the user canceled).
     */
    private boolean editInternal(FavoriteTask favoriteTask, EditFavoriteInteraction editFavoriteInteraction) {
        EditibleFavoriteTask workingCopy = new EditibleFavoriteTask(favoriteTask);
        boolean isValid = true;
        do {
            if (!editFavoriteInteraction.editFavorite(workingCopy)) {
                return false;
            }

            isValid = validateEditableFavoriteTask(workingCopy, favoriteTask, editFavoriteInteraction);
        } while (!isValid);

        favoriteTask.setFullCommandLine(workingCopy.fullCommandLine);

        favoriteTask.setDisplayName(workingCopy.displayName);
        favoriteTask.setAlwaysShowOutput(workingCopy.alwaysShowOutput);

        return true;
    }

    public interface ValidationInteraction {
        public void reportError(String error);
    }

    /**
     * This validates the editable favorite task. It makes sure the task name is specified and that it's not a duplicate.
     *
     * @param editableFavoriteTask the task your editing.
     * @param originalFavoriteTaskObject the original object. This is used to test for duplication. If its new and not in the favorites, that's OK.
     * @param validationInteraction how we report errors to the user.
     * @return true if the task is valid, false if not.
     */
    private boolean validateEditableFavoriteTask(EditibleFavoriteTask editibleFavoriteTask, FavoriteTask originalFavoriteTaskObject, ValidationInteraction validationInteraction) {
        if (editibleFavoriteTask.fullCommandLine == null || editibleFavoriteTask.fullCommandLine.trim().equals("")) {
            validationInteraction.reportError("Full task name must be specified");
            return false;
        }

        if (editibleFavoriteTask.displayName == null || editibleFavoriteTask.displayName.trim().equals("")) {
            validationInteraction.reportError("Display name must be specified");
            return false;
        }

        return true;
    }

    /**
     * Call this to remove the specified favorites from the favorite tasks.
     *
     * @param favoritesToRemove the favorite tasks to remove
     */
    public void removeFavorites(List<FavoriteTask> favoritesToRemove) {
        if (favoriteTasks.removeAll(favoritesToRemove)) {
            notifyFavoritesChanged();
        }
    }

    /**
     * This moves the specified favorites up.
     *
     * @param favoritesToMove .
     */
    public void moveFavoritesBefore(List<FavoriteTask> favoritesToMove) {
        moveFavorites(favoritesToMove, true);
    }

    public void moveFavoritesAfter(List<FavoriteTask> favoritesToMove) {
        moveFavorites(favoritesToMove, false);
    }

    private void moveFavorites(final List<FavoriteTask> favoritesToMove, boolean moveBefore) {
        if (moveBefore) {
            favoriteTasks.moveBefore(favoritesToMove);
        } else {
            favoriteTasks.moveAfter(favoritesToMove);
        }

        favoriteTasksObserverLord.notifyObservers(new ObserverLord.ObserverNotification<FavoriteTasksObserver>() {
            public void notify(FavoriteTasksObserver observer) {
                observer.favoritesReordered(favoriteTasks);
            }
        });
    }

    /**
     * Call this to save favorites to a file.
     */
    public void exportToFile(DOM4JSerializer.ExportInteraction exportInteraction) {
        DOM4JSerializer.exportToFile("favorites", exportInteraction, createFileFilter(), this);
    }

    /**
     * Call this to read favorites from a file. I'm going to use FavoritesSerializable rather than ourselves (even though we're a JDOMSerializable) so if something goes wrong, we won't wipe out our
     * current settings.
     */
    public boolean importFromFile(DOM4JSerializer.ImportInteraction importInteraction) {
        FavoritesSerializable serializable = new FavoritesSerializable();
        if (!DOM4JSerializer.importFromFile(importInteraction, createFileFilter(), serializable)) {
            return false;
        }

        //only if we succeed should we clear out the existing favorites
        favoriteTasks.clear();
        favoriteTasks.addAll(serializable.getFavorites());

        notifyFavoritesChanged();

        return true;
    }

    private ExtensionFileFilter createFileFilter() {
        return new ExtensionFileFilter(".favorite-tasks", "Favorite Tasks");
    }

    /**
     * Call this to saves the current settings.
     *
     * @param settings where you save the settings.
     */
    public void serializeOut(SettingsNode settings) {
        FavoritesSerializable.serializeOut(settings, favoriteTasks);
    }

    /**
     * Call this to read in this object's settings. The reverse of serializeOut.
     *
     * @param settings where you read your settings.
     */
    public void serializeIn(SettingsNode settings) {
        FavoritesSerializable.serializeIn(settings, favoriteTasks);
    }

    /**
     * This makes a copy of all the selected tasks.
     *
     * @param tasksToCopy the tasks to copy
     */
    public void duplicateFavorites(List<FavoriteTask> tasksToCopy, EditFavoriteInteraction editFavoriteInteraction) {
        if (tasksToCopy == null || tasksToCopy.isEmpty()) {
            return;
        }

        Iterator<FavoriteTask> iterator = tasksToCopy.iterator();
        while (iterator.hasNext()) {
            FavoriteTask taskToCopy = iterator.next();
            FavoriteTask newFavoriteTask = new FavoriteTask(taskToCopy.getFullCommandLine(), taskToCopy.getDisplayName(), taskToCopy.alwaysShowOutput());

            if (!editInternal(newFavoriteTask, editFavoriteInteraction)) {
                return;
            }
            favoriteTasks.add(newFavoriteTask);
        }

        notifyFavoritesChanged();
    }

    /**
     * This makes a copy of the selected task.
     *
     * @param taskToCopy the task to copy
     */
    public FavoriteTask duplicateFavorite(FavoriteTask taskToCopy, EditFavoriteInteraction editFavoriteInteraction) {

        if (taskToCopy == null) {
            return null;
        }

        FavoriteTask newFavoriteTask = new FavoriteTask(taskToCopy.getFullCommandLine(), taskToCopy.getDisplayName(), taskToCopy.alwaysShowOutput());
        if (!editInternal(newFavoriteTask, editFavoriteInteraction)) {
            return null;
        }
        favoriteTasks.add(newFavoriteTask);
        notifyFavoritesChanged();
        return newFavoriteTask;
    }

    /**
     * This combines all the command lines of the favorites list into a single command line. This is useful for allowing a user to execute multiple favorites at once. Note: this doesn't do anything
     * intelligent by trying to weed out duplicates. Gradle handles that nicely already.
     *
     * @param favoriteTasks the favorite tasks to combine
     * @return a single String of all the command combined.
     */
    public static String combineFavoriteCommandLines(List<FavoriteTask> favoriteTasks) {
        StringBuilder builder = new StringBuilder();

        Iterator<FavoriteTask> iterator = favoriteTasks.iterator();
        while (iterator.hasNext()) {
            FavoriteTask favoriteTask = iterator.next();

            builder.append(favoriteTask.getFullCommandLine());
            if (iterator.hasNext()) {
                builder.append(' ');
            }
        }

        return builder.toString();
    }
}
