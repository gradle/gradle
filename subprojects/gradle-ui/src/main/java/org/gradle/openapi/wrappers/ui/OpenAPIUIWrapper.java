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

import org.gradle.openapi.external.ui.AlternateUIInteractionVersion1;
import org.gradle.openapi.external.ui.GradleTabVersion1;
import org.gradle.openapi.external.ui.SettingsNodeVersion1;
import org.gradle.openapi.external.ui.SinglePaneUIVersion1;
import org.gradle.openapi.external.ui.CommandLineArgumentAlteringListenerVersion1;
import org.gradle.gradleplugin.userinterface.swing.generic.SinglePaneUIInstance;

import javax.swing.*;
import java.io.File;
import java.util.Map;
import java.util.HashMap;

/**
 This wraps a SinglePaneUIVersion1 for the purpose of being instantiated for
 an external tool such an IDE plugin. It wraps several interfaces and uses
 delegation in an effort to make this backward and forward compatible.

 @author mhunsicker
  */
public class OpenAPIUIWrapper implements SinglePaneUIVersion1 {
    private SinglePaneUIInstance singlePaneUIInstance;
    private Map<GradleTabVersion1, GradleTabVersionWrapper> tabMap = new HashMap<GradleTabVersion1, GradleTabVersionWrapper>();
    private Map<CommandLineArgumentAlteringListenerVersion1, CommandLineArgumentAlteringListenerWrapper> commandLineListenerMap = new HashMap<CommandLineArgumentAlteringListenerVersion1, CommandLineArgumentAlteringListenerWrapper>();

    public OpenAPIUIWrapper(SettingsNodeVersion1 settings, AlternateUIInteractionVersion1 alternateUIInteraction) {
        SettingsNodeVersionWrapper settingsVersionWrapper = new SettingsNodeVersionWrapper(settings);
        AlternateUIInteractionVersionWrapper alternateUIInteractionVersionWrapper = new AlternateUIInteractionVersionWrapper(alternateUIInteraction, settingsVersionWrapper);

        singlePaneUIInstance = new SinglePaneUIInstance(settingsVersionWrapper, alternateUIInteractionVersionWrapper);
    }

    /**
       @return the panel for this pane. This can be inserted directly into your UI.
    */
    public JComponent getComponent() {
        return singlePaneUIInstance.getComponent();
    }

    /**
       Call this whenever you're about to show this panel. We'll do whatever
       initialization is necessary.
    */
    public void aboutToShow() {
        singlePaneUIInstance.aboutToShow();
    }

    /**
     * Call this to deteremine if you can close this pane. if we're busy, we'll
     * ask the user if they want to close.
     *
     * @param closeInteraction allows us to interact with the user
     * @return true if we can close, false if not.
     */
    public boolean canClose(final CloseInteraction closeInteraction) {
        return singlePaneUIInstance.canClose(new SinglePaneUIInstance.CloseInteraction() {
            public boolean promptUserToConfirmClosingWhileBusy() {
                return closeInteraction.promptUserToConfirmClosingWhileBusy();
            }
        });
    }

    /**
       Call this before you close the pane. This gives it an opportunity to do
       cleanup. You probably should call canClose before this. It gives the
       app a chance to cancel if its busy.
    */
    public void close() {
        singlePaneUIInstance.close();
    }

    /**
       @return the root directory of your gradle project.
    */
    public File getCurrentDirectory() {
        return singlePaneUIInstance.getCurrentDirectory();
    }

    /**
       @param  currentDirectory the new root directory of your gradle project.
    */
    public void setCurrentDirectory(File currentDirectory) {
        singlePaneUIInstance.setCurrentDirectory(currentDirectory);
    }

    /**
     * @return the gradle home directory. Where gradle is installed.
     */
    public File getGradleHomeDirectory() {
        return singlePaneUIInstance.getGradlePluginLord().getGradleHomeDirectory();
    }

    /**
     * This is called to get a custom gradle executable file. If you don't run
     * gradle.bat or gradle shell script to run gradle, use this to specify
     * what you do run. Note: we're going to pass it the arguments that we would
     * pass to gradle so if you don't like that, see alterCommandLineArguments.
     * Normaly, this should return null.
     *
     * @return the Executable to run gradle command or null to use the default
     */
    public File getCustomGradleExecutable() {
        return singlePaneUIInstance.getGradlePluginLord().getCustomGradleExecutor();
    }

    /**
       Call this to add an additional tab to the gradle UI. You can call this
       at any time.
       @param  index             the index of where to add the tab.
       @param  gradleTabVersion1 the tab to add.
    */
    public void addTab(int index, GradleTabVersion1 gradleTabVersion1) {
        GradleTabVersionWrapper gradleVersionWrapper = new GradleTabVersionWrapper(gradleTabVersion1);

        //we have to store our wrapper so you can call remove tab using your passed-in object
        tabMap.put(gradleTabVersion1, gradleVersionWrapper);

        singlePaneUIInstance.addGradleTab(index, gradleVersionWrapper);
    }

    /**
       Call this to remove one of your own tabs from this.

       @param  gradleTabVersion1 the tab to remove
    */
    public void removeTab(GradleTabVersion1 gradleTabVersion1) {
        GradleTabVersionWrapper gradleTabVersionWrapper = tabMap.remove(gradleTabVersion1);
        if (gradleTabVersionWrapper != null)
            singlePaneUIInstance.removeGradleTab(gradleTabVersionWrapper);
    }

    /**
       @return the total number of tabs.
    */
    public int getGradleTabCount() {
        return singlePaneUIInstance.getGradleTabCount();
    }

    /**
       @param  index      the index of the tab
       @return the name of the tab at the specified index.
    */
    public String getGradleTabName(int index) {
        return singlePaneUIInstance.getGradleTabName(index);
    }


    /**
     * This allows you to add a listener that can add additional command line
     * arguments whenever gradle is executed. This is useful if you've customized
     * your gradle build and need to specify, for example, an init script.
     *
     * @param listener the listener that modifies the command line arguments.
     */
    public void addCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListenerVersion1 listener) {
        CommandLineArgumentAlteringListenerWrapper wrapper = new CommandLineArgumentAlteringListenerWrapper(listener);

        //we have to store our wrapper so you can call remove the listener using your passed-in object
        commandLineListenerMap.put(listener, wrapper);

        singlePaneUIInstance.getGradlePluginLord().addCommandLineArgumentAlteringListener(wrapper);
    }

    public void removeCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListenerVersion1 listener) {
        CommandLineArgumentAlteringListenerWrapper wrapper = commandLineListenerMap.remove(listener);
        if (wrapper != null)
            singlePaneUIInstance.getGradlePluginLord().removeCommandLineArgumentAlteringListener(wrapper);
    }

    /**
    Call this to execute the given gradle command.

    @param commandLineArguments  the command line arguments to pass to gradle.
    @param displayName           the name displayed in the UI for this command
    */
    public void executeCommand(String commandLineArguments, String displayName) {
        //we go through the Swing version because it allows you to specify a display name
        //for the command.
        singlePaneUIInstance.getSwingGradleWrapper().executeTaskInThread(commandLineArguments, displayName, false, true, true);
    }
}
