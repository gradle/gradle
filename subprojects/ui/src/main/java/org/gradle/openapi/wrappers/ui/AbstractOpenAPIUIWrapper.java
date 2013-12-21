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
package org.gradle.openapi.wrappers.ui;

import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.request.ExecutionRequest;
import org.gradle.gradleplugin.foundation.request.RefreshTaskListRequest;
import org.gradle.gradleplugin.foundation.request.Request;
import org.gradle.gradleplugin.userinterface.swing.generic.BasicGradleUI;
import org.gradle.openapi.external.foundation.GradleInterfaceVersion1;
import org.gradle.openapi.external.foundation.favorites.FavoritesEditorVersion1;
import org.gradle.openapi.external.ui.*;
import org.gradle.openapi.wrappers.foundation.favorites.FavoritesEditorWrapper;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of BasicGradleUI meant to help shield external users from internal changes. This also provides the basics for the UI regardless of whether the output is in a separate pane or not.
 */
public abstract class AbstractOpenAPIUIWrapper<U extends BasicGradleUI> {
    private U basicGradleUI;
    private Map<GradleTabVersion1, GradleTabVersionWrapper> tabMap = new HashMap<GradleTabVersion1, GradleTabVersionWrapper>();

    protected SettingsNodeVersionWrapper settingsVersionWrapper;
    protected AlternateUIInteractionVersionWrapper alternateUIInteractionVersionWrapper;
    protected GradleInterfaceVersion1 gradleInterfaceWrapper;

    private OutputUILordWrapper outputUILordWrapper;

    public AbstractOpenAPIUIWrapper(SettingsNodeVersion1 settings, AlternateUIInteractionVersion1 alternateUIInteraction) {
        settingsVersionWrapper = new SettingsNodeVersionWrapper(settings);
        alternateUIInteractionVersionWrapper = new AlternateUIInteractionVersionWrapper(alternateUIInteraction, settingsVersionWrapper);
    }

    public void initialize(U basicGradleUI) {
        this.basicGradleUI = basicGradleUI;
        basicGradleUI.getGradlePluginLord().addRequestObserver(new GradlePluginLord.RequestObserver() {
            /**
             Notification that a command is about to be executed. This is mostly useful
             for IDE's that may need to save their files.

             @param request the request that's about to be executed
             */
            public void aboutToExecuteRequest(Request request) {
                alternateUIInteractionVersionWrapper.aboutToExecuteCommand(request.getFullCommandLine());
            }

            public void executionRequestAdded(ExecutionRequest request) {
            }

            public void refreshRequestAdded(RefreshTaskListRequest request) {
            }

            public void requestExecutionComplete(Request request, int result, String output) {
            }
        }, false);

        outputUILordWrapper = new OutputUILordWrapper(basicGradleUI.getOutputUILord());
        gradleInterfaceWrapper = instantiateGradleInterfaceWrapper();
    }

    /**
     * This instantiates our GradleInterfaceVersion object. Additions have been made to it -- making new versions, so we have a choice of which one to load. We'll try to load the latest one first, if
     * that fails, we'll fall back on older versions. The latest version would fail to load because it depends on classes that are part of the Open API project and it can't find those classes. It
     * might not find them because this is loaded from the Open API and if its an older version, those classes won't exist.
     *
     * @return a version of GradleInterfaceVersionX
     */
    protected GradleInterfaceVersion1 instantiateGradleInterfaceWrapper() {
        try {
            //try to load the latest version. I'm explicitly using the full package name so any NoClassDefFoundErrors will only
            //occur within this try/catch block.
            return new org.gradle.openapi.wrappers.foundation.GradleInterfaceWrapperVersion2(basicGradleUI.getGradlePluginLord());
        } catch (NoClassDefFoundError e) {  //if that's not found, fall back to version 1
            return new org.gradle.openapi.wrappers.foundation.GradleInterfaceWrapperVersion1(basicGradleUI.getGradlePluginLord());
        }
    }

    public U getGradleUI() {
        return basicGradleUI;
    }

    /**
     * Call this whenever you're about to show this panel. We'll do whatever initialization is necessary.
     */
    public void aboutToShow() {
        basicGradleUI.aboutToShow();
    }

    /**
     * Call this to deteremine if you can close this pane. if we're busy, we'll ask the user if they want to close.
     *
     * @param closeInteraction allows us to interact with the user
     * @return true if we can close, false if not.
     */
    public boolean canClose(final BasicGradleUIVersion1.CloseInteraction closeInteraction) {
        return basicGradleUI.canClose(new BasicGradleUI.CloseInteraction() {
            public boolean promptUserToConfirmClosingWhileBusy() {
                return closeInteraction.promptUserToConfirmClosingWhileBusy();
            }
        });
    }

    /**
     * Call this before you close the pane. This gives it an opportunity to do cleanup. You probably should call canClose before this. It gives the app a chance to cancel if its busy.
     */
    public void close() {
        basicGradleUI.close();
    }

    /**
     * @return the root directory of your gradle project.
     */
    public File getCurrentDirectory() {
        return gradleInterfaceWrapper.getCurrentDirectory();
    }

    /**
     * @param currentDirectory the new root directory of your gradle project.
     */
    public void setCurrentDirectory(File currentDirectory) {
        gradleInterfaceWrapper.setCurrentDirectory(currentDirectory);
    }

    /**
     * @return the gradle home directory. Where gradle is installed.
     */
    public File getGradleHomeDirectory() {
        return gradleInterfaceWrapper.getGradleHomeDirectory();
    }

    /**
     * This is called to get a custom gradle executable file. If you don't run gradle.bat or gradle shell script to run gradle, use this to specify what you do run. Note: we're going to pass it the
     * arguments that we would pass to gradle so if you don't like that, see alterCommandLineArguments. Normally, this should return null.
     *
     * @return the Executable to run gradle command or null to use the default
     */
    public File getCustomGradleExecutable() {
        return gradleInterfaceWrapper.getCustomGradleExecutable();
    }

    /**
     * Call this to add an additional tab to the gradle UI. You can call this at any time.
     *
     * @param index the index of where to add the tab.
     * @param gradleTabVersion1 the tab to add.
     */
    public void addTab(int index, GradleTabVersion1 gradleTabVersion1) {
        GradleTabVersionWrapper gradleVersionWrapper = new GradleTabVersionWrapper(gradleTabVersion1);

        //we have to store our wrapper so you can call remove tab using your passed-in object
        tabMap.put(gradleTabVersion1, gradleVersionWrapper);

        basicGradleUI.addGradleTab(index, gradleVersionWrapper);
    }

    /**
     * Call this to remove one of your own tabs from this.
     *
     * @param gradleTabVersion1 the tab to remove
     */
    public void removeTab(GradleTabVersion1 gradleTabVersion1) {
        GradleTabVersionWrapper gradleTabVersionWrapper = tabMap.remove(gradleTabVersion1);
        if (gradleTabVersionWrapper != null) {
            basicGradleUI.removeGradleTab(gradleTabVersionWrapper);
        }
    }

    public int getGradleTabCount() {
        return basicGradleUI.getGradleTabCount();
    }

    /**
     * @param index the index of the tab
     * @return the name of the tab at the specified index.
     */
    public String getGradleTabName(int index) {
        return basicGradleUI.getGradleTabName(index);
    }

    /**
     * Returns the index of the gradle tab with the specified name.
     *
     * @param name the name of the tab
     * @return the index of the tab or -1 if not found
     */
    public int getGradleTabIndex(String name) {
        return basicGradleUI.getGradleTabIndex(name);
    }

    /**
     * @return the currently selected tab
     */
    public int getCurrentGradleTab() {
        return basicGradleUI.getCurrentGradleTab();
    }

    /**
     * Makes the specified tab the current tab.
     *
     * @param index the index of the tab.
     */
    public void setCurrentGradleTab(int index) {
        basicGradleUI.setCurrentGradleTab(index);
    }

    /**
     * This allows you to add a listener that can add additional command line arguments whenever gradle is executed. This is useful if you've customized your gradle build and need to specify, for
     * example, an init script.
     *
     * @param listener the listener that modifies the command line arguments.
     */
    public void addCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListenerVersion1 listener) {
        gradleInterfaceWrapper.addCommandLineArgumentAlteringListener(listener);
    }

    public void removeCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListenerVersion1 listener) {
        gradleInterfaceWrapper.removeCommandLineArgumentAlteringListener(listener);
    }

    public OutputUILordVersion1 getOutputLord() {
        return new OutputUILordWrapper(basicGradleUI.getOutputUILord());
    }

    public void addOutputObserver(OutputObserverVersion1 observer) {
        outputUILordWrapper.addOutputObserver(observer);
    }

    public void removeOutputObserver(OutputObserverVersion1 observer) {
        outputUILordWrapper.removeOutputObserver(observer);
    }

    /**
     * Call this to execute the given gradle command.
     *
     * @param commandLineArguments the command line arguments to pass to gradle.
     * @param displayName the name displayed in the UI for this command
     */
    public void executeCommand(String commandLineArguments, String displayName) {
        //we go through the Swing version because it allows you to specify a display name
        //for the command.
        basicGradleUI.executeCommand(commandLineArguments, displayName);
    }

    /**
     * This refreshes the task tree. Useful if you know you've changed something behind gradle's back or when first displaying this UI.
     */
    public void refreshTaskTree() {
        basicGradleUI.refreshTaskTree();
    }

    /**
     * Determines if commands are currently being executed or not.
     *
     * @return true if we're busy, false if not.
     */
    public boolean isBusy() {
        return getGradleUI().isBusy();
    }

    /**
     * Determines whether output is shown only when errors occur or always
     *
     * @return true to only show output if errors occur, false to show it always.
     */
    public boolean getOnlyShowOutputOnErrors() {
        return getGradleUI().getOutputUILord().getOnlyShowOutputOnErrors();
    }

    /**
     * This adds the specified component to the setup panel. It is added below the last 'default' item. You can only add 1 component here, so if you need to add multiple things, you'll have to handle
     * adding that to yourself to the one component.
     *
     * @param component the component to add.
     */
    public void setCustomPanelToSetupTab(JComponent component) {
        getGradleUI().setCustomPanelToSetupTab(component);
    }

    /**
     * Sets the font for the output text
     *
     * @param font the new font
     */
    public void setOutputTextFont(Font font) {
        getGradleUI().setOutputTextFont(font);
    }

    /**
     * @return an object that works with lower level gradle and contains the current projects and tasks.
     */
    public GradleInterfaceVersion1 getGradleInterfaceVersion1() {
        return gradleInterfaceWrapper;
    }

    /**
     * Returns a FavoritesEditor. This is useful for getting a list of all favorites or modifying them.
     *
     * @return a FavoritesEditorVersion1. Use this to interact with the favorites.
     */
    public FavoritesEditorVersion1 getFavoritesEditor() {
        return new FavoritesEditorWrapper(basicGradleUI.getGradlePluginLord().getFavoritesEditor());
    }
}
