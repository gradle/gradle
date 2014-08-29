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
package org.gradle.gradleplugin.userinterface.swing.common;

import org.gradle.api.UncheckedIOException;
import org.gradle.gradleplugin.foundation.settings.SettingsNode;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * This class just helps do some of the mundane tasks of saving and restoring the location of something.
 */
public class PreferencesAssistant {
    private static final String WINDOW_X = "window_x";
    private static final String WINDOW_Y = "window_y";
    private static final String WINDOW_WIDTH = "window_width";
    private static final String WINDOW_HEIGHT = "window_height";
    private static final String EXTENDED_STATE = "extended-state";
    private static final String DIVIDER_LOCATION = "divider_location";
    private static final String DIRECTORY_NAME = "directory_name";

    public static SettingsNode saveSettings(SettingsNode settingsNode, Window window, String id, Class windowClass) {
        Point p = window.getLocation();
        Dimension size = window.getSize();

        SettingsNode childNode = settingsNode.addChildIfNotPresent(getPrefix(windowClass, id));

        childNode.setValueOfChildAsInt(WINDOW_X, p.x);
        childNode.setValueOfChildAsInt(WINDOW_Y, p.y);
        childNode.setValueOfChildAsInt(WINDOW_WIDTH, size.width);
        childNode.setValueOfChildAsInt(WINDOW_HEIGHT, size.height);

        return childNode;
    }

    /**
     * This version works for frames. It makes sure it doesn't save the extended state (maximized, iconified, etc) if its iconified. Doing so, causes problems when its restored.
     */
    public static void saveSettings(SettingsNode settingsNode, JFrame frame, String id, Class windowClass) {
        if (frame.getExtendedState() == JFrame.ICONIFIED) {
            return;
        }

        SettingsNode childNode = saveSettings(settingsNode, (Window) frame, id, windowClass);

        if (frame.getExtendedState() != JFrame.ICONIFIED) {
            childNode.setValueOfChildAsInt(EXTENDED_STATE, frame.getExtendedState());
        }
    }

    /**
     * Call this to restore the preferences that were saved via a call to save settings. Note: if no preferences are found it doesn't do anything.
     *
     * @param window the window who's settings to save
     * @param id a unique ID for these settings.
     * @param windowClass Any class. Just used for the preferences mechanism to obtain an instance. Making this an argument gives you more flexibility.
     */
    public static SettingsNode restoreSettings(SettingsNode settingsNode, Window window, String id, Class windowClass) {
        SettingsNode childNode = settingsNode.getChildNode(getPrefix(windowClass, id));
        if (childNode == null) {
            return null;
        }

        int x = childNode.getValueOfChildAsInt(WINDOW_X, window.getLocation().x);
        int y = childNode.getValueOfChildAsInt(WINDOW_Y, window.getLocation().y);
        int width = childNode.getValueOfChildAsInt(WINDOW_WIDTH, window.getSize().width);
        int height = childNode.getValueOfChildAsInt(WINDOW_HEIGHT, window.getSize().height);

        window.setLocation(x, y);
        window.setSize(width, height);

        return childNode;
    }

    /**
     * This restores the position of a frame. We not only restore the size, but we'll maximize it if its was maximized when saved.
     */
    public static void restoreSettings(SettingsNode settingsNode, JFrame frame, String id, Class windowClass) {
        SettingsNode childNode = restoreSettings(settingsNode, (Window) frame, id, windowClass);
        if (childNode == null) {
            return;
        }

        int extendedState = childNode.getValueOfChildAsInt(EXTENDED_STATE, frame.getExtendedState());

        if (extendedState != JFrame.ICONIFIED) {
            frame.setExtendedState(extendedState);
        }
    }

    public static void saveSettings(SettingsNode settingsNode, JSplitPane splitter, String id, Class splitterClass) {
        SettingsNode childNode = settingsNode.addChildIfNotPresent(getPrefix(splitterClass, id));

        childNode.setValueOfChildAsInt(DIVIDER_LOCATION, splitter.getDividerLocation());
    }

    public static void restoreSettings(SettingsNode settingsNode, JSplitPane splitter, String id, Class splitterClass) {
        SettingsNode childNode = settingsNode.getChildNode(getPrefix(splitterClass, id));
        if (childNode == null) {
            return;
        }

        int location = childNode.getValueOfChildAsInt(DIVIDER_LOCATION, splitter.getDividerLocation());
        splitter.setDividerLocation(location);
    }

    private static String getPrefix(Class aClass, String id) {
        return aClass.getSimpleName() + '_' + id;
    }

    /**
     * Saves the settings of the file chooser; and by settings I mean the 'last visited directory'.
     *
     * @param saveCurrentDirectoryVsSelectedFilesParent this should be true if you're selecting only directories, false if you're selecting only files. I don't know what if you allow both.
     */
    public static void saveSettings(SettingsNode settingsNode, JFileChooser fileChooser, String id, Class fileChooserClass, boolean saveCurrentDirectoryVsSelectedFilesParent) {
        SettingsNode childNode = settingsNode.addChildIfNotPresent(getPrefix(fileChooserClass, id));

        String save;
        try {
            if (saveCurrentDirectoryVsSelectedFilesParent) {
                save = fileChooser.getCurrentDirectory().getCanonicalPath();
            } else {
                save = fileChooser.getSelectedFile().getCanonicalPath();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (save != null) {
            childNode.setValueOfChild(DIRECTORY_NAME, save);
        }
    }

    public static void restoreSettings(SettingsNode settingsNode, JFileChooser fileChooser, String id, Class fileChooserClass) {
        SettingsNode childNode = settingsNode.getChildNode(getPrefix(fileChooserClass, id));
        if (childNode == null) {
            return;
        }

        String lastDirectory = childNode.getValueOfChild(DIRECTORY_NAME, null);

        if (lastDirectory != null) {
            fileChooser.setCurrentDirectory(new File(lastDirectory));
        }
    }
}
