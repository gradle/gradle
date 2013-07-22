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
package org.gradle.openapi.wrappers.foundation.favorites;

import org.gradle.gradleplugin.foundation.favorites.FavoriteTask;
import org.gradle.gradleplugin.foundation.favorites.FavoritesEditor;
import org.gradle.gradleplugin.userinterface.swing.generic.SwingEditFavoriteInteraction;
import org.gradle.openapi.external.foundation.TaskVersion1;
import org.gradle.openapi.external.foundation.favorites.FavoriteTaskVersion1;
import org.gradle.openapi.external.foundation.favorites.FavoritesEditorVersion1;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of FavoritesEditorVersion1 meant to help shield external users from internal changes.
 */
public class FavoritesEditorWrapper implements FavoritesEditorVersion1 {
    private FavoritesEditor favoritesEditor;

    public FavoritesEditorWrapper(FavoritesEditor favoritesEditor) {
        this.favoritesEditor = favoritesEditor;
    }

    public FavoriteTaskVersion1 addFavorite(String fullCommandLine, String displayName, boolean alwaysShowOutput) {
        return convertFavoriteTask(favoritesEditor.addFavorite(fullCommandLine, displayName, alwaysShowOutput));
    }

    public String editFavorite(FavoriteTaskVersion1 favoriteTaskVersion1, final String newFullCommandLine, final String newDisplayName, final boolean newAlwaysShowOutput) {
        final StringHolder stringHolder = new StringHolder();
        FavoriteTask favoriteTask = getFavoriteTask(favoriteTaskVersion1);
        favoritesEditor.editFavorite(favoriteTask, new FavoritesEditor.EditFavoriteInteraction() {
            public boolean editFavorite(FavoritesEditor.EditibleFavoriteTask favoriteTask) {
                favoriteTask.fullCommandLine = newFullCommandLine;
                favoriteTask.displayName = newDisplayName;
                favoriteTask.alwaysShowOutput = newAlwaysShowOutput;
                return true;
            }

            public void reportError(String error) {
                stringHolder.string = error;
            }
        });

        return stringHolder.string;
    }

    //
    private class StringHolder {
        private String string;
    }

    private FavoriteTaskVersion1 convertFavoriteTask(FavoriteTask favoriteTask) {
        if (favoriteTask == null) {
            return null;
        }

        return new FavoriteTaskWrapper(favoriteTask);
    }

    public List<FavoriteTaskVersion1> getFavoriteTasks() {
        List<FavoriteTaskVersion1> returnedTasks = new ArrayList<FavoriteTaskVersion1>();
        Iterator<FavoriteTask> taskIterator = favoritesEditor.getFavoriteTasks().iterator();
        while (taskIterator.hasNext()) {
            FavoriteTask favoriteTask = taskIterator.next();
            returnedTasks.add(new FavoriteTaskWrapper(favoriteTask));
        }
        return returnedTasks;
    }

    public FavoriteTaskVersion1 getFavorite(String fullCommandLine) {
        return convertFavoriteTask(favoritesEditor.getFavorite(fullCommandLine));
    }

    public FavoriteTaskVersion1 getFavoriteByDisplayName(String displayName) {
        return convertFavoriteTask(favoritesEditor.getFavoriteByDisplayName(displayName));
    }

    public FavoriteTaskVersion1 getFavorite(TaskVersion1 task) {
        return convertFavoriteTask(favoritesEditor.getFavorite(task.getFullTaskName()));
    }

    public FavoriteTaskVersion1 promptUserToAddFavorite(Window parent) {
        FavoriteTask favoriteTask = favoritesEditor.addFavorite(new SwingEditFavoriteInteraction(parent, "Add Favorite", SwingEditFavoriteInteraction.SynchronizeType.OnlyIfAlreadySynchronized));
        return convertFavoriteTask(favoriteTask);
    }

    public boolean promptUserToEditFavorite(Window parent, FavoriteTaskVersion1 favorite) {
        FavoriteTask favoriteTask = getFavoriteTask(favorite);
        return favoritesEditor.editFavorite(favoriteTask, new SwingEditFavoriteInteraction(parent, "Edit Favorite", SwingEditFavoriteInteraction.SynchronizeType.OnlyIfAlreadySynchronized));
    }

    public void removeFavorites(List<FavoriteTaskVersion1> favoritesToRemove) {
        List<FavoriteTask> favoriteTasksToRemove = new ArrayList<FavoriteTask>();

        Iterator<FavoriteTaskVersion1> iterator = favoritesToRemove.iterator();
        while (iterator.hasNext()) {
            FavoriteTaskVersion1 favoriteTaskVersion1 = iterator.next();
            favoriteTasksToRemove.add(getFavoriteTask(favoriteTaskVersion1));
        }

        favoritesEditor.removeFavorites(favoriteTasksToRemove);
    }

    //gets the favorite task out of a FavoriteTaskVersion1.
    private FavoriteTask getFavoriteTask(FavoriteTaskVersion1 favoriteTaskVersion1) {
        return ((FavoriteTaskWrapper) favoriteTaskVersion1).getFavoriteTask();
    }
}
