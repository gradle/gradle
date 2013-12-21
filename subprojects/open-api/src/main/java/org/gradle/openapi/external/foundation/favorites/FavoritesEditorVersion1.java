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
package org.gradle.openapi.external.foundation.favorites;

import org.gradle.openapi.external.foundation.TaskVersion1;

import java.awt.*;
import java.util.List;

/**
 * This is an abstraction from Gradle that allows you to obtain and edit favorites.
 *
 * <p>This is a mirror of FavoritesEditor inside Gradle, but this is meant to aid backward and forward compatibility by shielding you from direct changes within gradle.
 * @deprecated No replacement
 */
@Deprecated
public interface FavoritesEditorVersion1 {

    /**
     * Adds the specified favorite.
     *
     * @param fullCommandLine the command line that this favorite executes
     * @param displayName a more user-friendly name for the command
     * @param alwaysShowOutput true to always show output when this favorite is executed. False to only show output when errors occur.
     * @return the favorite added
     */
    public FavoriteTaskVersion1 addFavorite(String fullCommandLine, String displayName, boolean alwaysShowOutput);

    /**
     * Sets new values on the specified favorite task. This provides a simple way to programmatically edit favorite tasks.
     *
     * @param favoriteTask the favorite to edit
     * @param newFullCommandLine the new command line
     * @param newDisplayName the new display name
     * @param newAlwaysShowOutput the new value for whether or not to always show output (vs only showing it when an error occurs).
     * @returns null if successful otherwise, an error suitable for displaying to the user.
     */
    public String editFavorite(FavoriteTaskVersion1 favoriteTask, String newFullCommandLine, String newDisplayName, boolean newAlwaysShowOutput);

    /**
     * @return a list of all favorites in the system
     */
    public List<FavoriteTaskVersion1> getFavoriteTasks();

    /**
     * Returns the favorite with the specified command line
     *
     * @param fullCommandLine the command line of the sought favorite
     * @return the matching favorite or null if no match found.
     */
    public FavoriteTaskVersion1 getFavorite(String fullCommandLine);

    /**
     * Returns the favorite with the specified display name
     *
     * @param displayName the display name of the sought favorite
     * @return the matching favorite or null if no match found.
     */
    public FavoriteTaskVersion1 getFavoriteByDisplayName(String displayName);

    /**
     * Returns the favorite with the specified task
     *
     * @param task the task of the sought favorite
     * @return the matching favorite or null if no match found.
     */
    public FavoriteTaskVersion1 getFavorite(TaskVersion1 task);

    /**
     * Display a Swing dialog prompting the user to enter a favorite.
     *
     * @param parent the parent window of the dialog.
     * @return the favorite that was added or null if the user canceled
     */
    public FavoriteTaskVersion1 promptUserToAddFavorite(Window parent);

    /**
     * Display a Swing dialog prompting the user to edit the specified favorite
     *
     * @param parent the parent window of the dialog
     * @param favorite the favorite to edit
     * @return true if the user made changes and accepted them, false if the user canceled.
     */
    public boolean promptUserToEditFavorite(Window parent, FavoriteTaskVersion1 favorite);

    /**
     * Removes the specified favorites.
     *
     * @param favoritesToRemove the favorites to remove
     */
    public void removeFavorites(List<FavoriteTaskVersion1> favoritesToRemove);
}
