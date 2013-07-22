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
package org.gradle.openapi.external.ui;

import org.gradle.openapi.external.foundation.GradleInterfaceVersion1;
import org.gradle.openapi.external.foundation.favorites.FavoritesEditorVersion1;

import javax.swing.*;
import java.io.File;

/**
 * This represents a basic gradle UI
 *
 * To use this, you'll want to get an instance of this from Gradle. Then setup your UI and add this to it via getComponent. Then call aboutToShow before you display your UI. Call close before you hide
 * your UI. You'll need to set the current directory (at any time) so gradle knows where your project is located.
 * @deprecated No replacement
 */
@Deprecated
public interface BasicGradleUIVersion1 {
    /**
     * Call this whenever you're about to show this panel. We'll do whatever initialization is necessary.
     */
    public void aboutToShow();

    //
    public interface CloseInteraction {
        /**
         * This is called if gradle tasks are being executed and you want to know if we can close. Ask the user.
         *
         * @return true if the user confirms cancelling the current tasks. False if not.
         */
        public boolean promptUserToConfirmClosingWhileBusy();
    }

    /**
     * Call this to determine if you can close this pane. if we're busy, we'll ask the user if they want to close.
     *
     * @param closeInteraction allows us to interact with the user
     * @return true if we can close, false if not.
     */
    public boolean canClose(CloseInteraction closeInteraction);

    /**
     * Call this before you close the pane. This gives it an opportunity to do cleanup. You probably should call canClose before this. It gives the app a chance to cancel if its busy.
     */
    public void close();

    /**
     * @return the root directory of your gradle project.
     */
    public File getCurrentDirectory();

    /**
     * @param currentDirectory the new root directory of your gradle project.
     */
    public void setCurrentDirectory(File currentDirectory);

    /**
     * @return the gradle home directory. Where gradle is installed.
     */
    public File getGradleHomeDirectory();

    /**
     * This is called to get a custom gradle executable file. If you don't run gradle.bat or gradle shell script to run gradle, use this to specify what you do run. Note: we're going to pass it the
     * arguments that we would pass to gradle so if you don't like that, see alterCommandLineArguments. Normally, this should return null.
     *
     * @return the Executable to run gradle command or null to use the default
     */
    public File getCustomGradleExecutable();

    /**
     * Call this to add an additional tab to the gradle UI. You can call this at any time.
     *
     * @param index the index of where to add the tab.
     * @param gradleTabVersion1 the tab to add.
     */
    public void addTab(int index, GradleTabVersion1 gradleTabVersion1);

    /**
     * Call this to remove one of your own tabs from this.
     *
     * @param gradleTabVersion1 the tab to remove
     */
    public void removeTab(GradleTabVersion1 gradleTabVersion1);

    /**
     * @return the total number of tabs.
     */
    public int getGradleTabCount();

    /**
     * @param index the index of the tab
     * @return the name of the tab at the specified index.
     */
    public String getGradleTabName(int index);

    /**
     * Returns the index of the gradle tab with the specified name.
     *
     * @param name the name of the tab
     * @return the index of the tab or -1 if not found
     */
    public int getGradleTabIndex(String name);

    /**
     * @return the currently selected tab
     */
    public int getCurrentGradleTab();

    /**
     * Makes the specified tab the current tab.
     *
     * @param index the index of the tab.
     */
    public void setCurrentGradleTab(int index);

    /**
     * This allows you to add a listener that can add additional command line arguments whenever gradle is executed. This is useful if you've customized your gradle build and need to specify, for
     * example, an init script.
     *
     * @param listener the listener that modifies the command line arguments.
     */
    public void addCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListenerVersion1 listener);

    public void removeCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListenerVersion1 listener);

    /**
     * Call this to execute the given gradle command.
     *
     * @param commandLineArguments the command line arguments to pass to gradle.
     * @param displayName the name displayed in the UI for this command
     */
    public void executeCommand(String commandLineArguments, String displayName);

    /**
     * This refreshes the task tree. Useful if you know you've changed something behind gradle's back or when first displaying this UI.
     */
    public void refreshTaskTree();

    /**
     * @return the output lord which shows the live output of all commands being executed. You can add observers to this as well as alter how it finds file links.
     */
    public OutputUILordVersion1 getOutputLord();

    //these were moved to OutputUILordVersion1, but remain here for backward compatibility
    public void addOutputObserver(OutputObserverVersion1 outputObserverVersion1);

    public void removeOutputObserver(OutputObserverVersion1 outputObserverVersion1);

    /**
     * Determines if commands are currently being executed or not.
     *
     * @return true if we're busy, false if not.
     */
    public boolean isBusy();

    /**
     * Determines whether output is shown only when errors occur or always
     *
     * @return true to only show output if errors occur, false to show it always.
     */
    public boolean getOnlyShowOutputOnErrors();

    /**
     * This adds the specified component to the setup panel. It is added below the last 'default' item. You can only add 1 component here, so if you need to add multiple things, you'll have to handle
     * adding that to yourself to the one component.
     *
     * @param component the component to add.
     */
    public void setCustomPanelToSetupTab(JComponent component);

    /**
     * This returns an object that works with lower level gradle and contains the current projects and tasks. You can also execute tasks from it and perform certain setup.
     *
     * @return a GradleInterfaceVersion1 object. It may also be GradleInterfaceVersion2 or a future version. You can check its type and then cast it as appropriate. This allows the caller to be
     *         backward compatible.
     */
    public GradleInterfaceVersion1 getGradleInterfaceVersion1();

    /**
     * Returns a FavoritesEditor. This is useful for getting a list of all favorites or modifying them.
     *
     * @return a FavoritesEditorVersion1. Use this to interact with the favorites.
     */
    public FavoritesEditorVersion1 getFavoritesEditor();
}
