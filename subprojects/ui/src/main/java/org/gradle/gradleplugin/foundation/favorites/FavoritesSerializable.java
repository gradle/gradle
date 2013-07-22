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
package org.gradle.gradleplugin.foundation.favorites;

import org.gradle.gradleplugin.foundation.settings.SettingsNode;
import org.gradle.gradleplugin.foundation.settings.SettingsSerializable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Inner class that handles serializing favorites. You can either pass it a favorites list and serialize them out or use the default constructor and serialize it. This allows you to serialize a
 * favorites list with or without an editor.
 */class FavoritesSerializable implements SettingsSerializable {
    private List<FavoriteTask> favorites;

    private static final String FAVORITE_ELEMENT_TAG = "favorite";
    private static final String FULL_COMMAND_LINE = "full-command-line";
    private static final String DISPLAY_NAME = "display-name";
    private static final String SHOW_OUTPUT = "show-output";
    private static final String ROOT_TAG = "favorites";
    private static final String FAVORITES_SIZE = "favorites-size";
    private static final String FAVORITE_PREFIX = "favorite_";

    //use this constructor if you're serializing OUT

    public FavoritesSerializable(List<FavoriteTask> favorites) {
        this.favorites = favorites;
    }

    //use this constructor if you're serialzing IN

    public FavoritesSerializable() {
        favorites = new ArrayList<FavoriteTask>();
    }

    //call this to get the favorites that were serilized in.

    public List<FavoriteTask> getFavorites() {
        return favorites;
    }

    /**
     * Call this to saves the current settings.
     *
     * @param settings where you save the settings.
     */
    public void serializeOut(SettingsNode settings) {
        serializeOut(settings, favorites);
    }

    public static void serializeOut(SettingsNode settings, List<FavoriteTask> favorites) {
        SettingsNode rootNode = settings.addChildIfNotPresent(ROOT_TAG);
        rootNode.removeAllChildren(); //clear out whatever may have already been there

        Iterator<FavoriteTask> iterator = favorites.iterator();
        while (iterator.hasNext()) {
            FavoriteTask favoriteTask = iterator.next();

            SettingsNode taskNode = rootNode.addChild(FAVORITE_ELEMENT_TAG);
            taskNode.setValueOfChild(FULL_COMMAND_LINE, favoriteTask.getFullCommandLine());
            taskNode.setValueOfChild(DISPLAY_NAME, favoriteTask.getDisplayName());
            taskNode.setValueOfChildAsBoolean(SHOW_OUTPUT, favoriteTask.alwaysShowOutput());
        }
    }

    /**
     * Call this to read in this object's settings. The reverse of serializeOut.
     *
     * @param settings where you read your settings.
     */
    public void serializeIn(SettingsNode settings) {
        serializeIn(settings, favorites);
    }

    public static void serializeIn(SettingsNode settings, List<FavoriteTask> favorites) {
        favorites.clear();  //remove everything already there

        SettingsNode rootElement = settings.getChildNode(ROOT_TAG);
        if (rootElement == null) {
            return;
        }

        Iterator<SettingsNode> iterator = rootElement.getChildNodes(FAVORITE_ELEMENT_TAG).iterator();
        while (iterator.hasNext()) {
            SettingsNode taskNode = iterator.next();

            String fullCommandLine = taskNode.getValueOfChild(FULL_COMMAND_LINE, null);
            if (fullCommandLine != null) {
                String displayName = taskNode.getValueOfChild(DISPLAY_NAME, fullCommandLine);
                boolean showOutput = taskNode.getValueOfChildAsBoolean(SHOW_OUTPUT, false);

                addFavoriteTask(favorites, fullCommandLine, displayName, showOutput);
            }
        }
    }

    private static void addFavoriteTask(List<FavoriteTask> favorites, String fullCommandLine, String displayName, boolean alwaysShowOutput) {
        if (displayName == null) {
            displayName = fullCommandLine;
        }

        favorites.add(new FavoriteTask(fullCommandLine, displayName, alwaysShowOutput));
    }
}
