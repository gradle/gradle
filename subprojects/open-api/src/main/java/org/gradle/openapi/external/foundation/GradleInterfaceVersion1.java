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
package org.gradle.openapi.external.foundation;

import org.gradle.openapi.external.ui.CommandLineArgumentAlteringListenerVersion1;

import java.io.File;
import java.util.List;

/**
 * This is an abstraction from Gradle that allows you to retrieve projects and views from it.
 *
 * This is a mirror of GradlePluginLord inside Gradle, but this is meant to aid backward and forward compatibility by shielding you from direct changes within gradle.
 * @deprecated No replacement
 */
@Deprecated
public interface GradleInterfaceVersion1 {

    /**
     * @return the root projects. It probably only has one.
     */
    public List<ProjectVersion1> getRootProjects();

    /**
     * This refreshes the projects and task list.
     */
    public void refreshTaskTree();

    /**
     * Determines if commands are currently being executed or not.
     *
     * @return true if we're busy, false if not.
     */
    public boolean isBusy();

    /**
     * Call this to execute the given gradle command.
     *
     * @param commandLineArguments the command line arguments to pass to gradle.
     * @param displayName the name displayed in the UI for this command
     */
    public void executeCommand(String commandLineArguments, String displayName);

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
     * This allows you to add a listener that can add additional command line arguments whenever gradle is executed. This is useful if you've customized your gradle build and need to specify, for
     * example, an init script.
     *
     * @param listener the listener that modifies the command line arguments.
     */
    public void addCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListenerVersion1 listener);

    public void removeCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListenerVersion1 listener);
}
