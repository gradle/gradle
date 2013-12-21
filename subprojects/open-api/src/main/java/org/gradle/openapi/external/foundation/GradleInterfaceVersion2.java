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

import org.gradle.openapi.external.foundation.favorites.FavoriteTaskVersion1;

import java.io.File;
import java.util.List;

/**
 * This is an abstraction from Gradle that allows you to retrieve projects and views from it.
 *
 * This is a mirror of GradlePluginLord inside Gradle, but this is meant to aid backward and forward compatibility by shielding you from direct changes within gradle.
 * @deprecated No replacement
 */
@Deprecated
public interface GradleInterfaceVersion2 extends GradleInterfaceVersion1 {

    /**
     * @return the version of gradle being run. This is basically the version from the jar file.
     */
    public String getVersion();

    /**
     * This refreshes the projects and task list.
     */
    public RequestVersion1 refreshTaskTree2();

    /**
     * This refreshes the task tree. Useful if you know you've changed something behind gradle's back or when first displaying this UI.
     *
     * @param additionalCommandLineArguments additional command line arguments to be passed to gradle when refreshing the task tree.
     * @return the request object. Useful if you want to track its completion via a RequestObserver
     */
    public RequestVersion1 refreshTaskTree2(String additionalCommandLineArguments);

    /**
     * Call this to execute the given gradle command.
     *
     * @param commandLineArguments the command line arguments to pass to gradle.
     * @param displayName the name displayed in the UI for this command
     * @return the request object. Useful if you want to track its completion via a RequestObserver
     */
    public RequestVersion1 executeCommand2(String commandLineArguments, String displayName);

    /**
     * Executes several favorites commands at once as a single command. This has the affect of simply concatenating all the favorite command lines into a single line.
     *
     * @param favorites a list of favorites. If just one favorite, it executes it normally. If multiple favorites, it executes them all at once as a single command. This blindly concatenates them so
     * it may wind up with duplicate tasks on the command line.
     * @return the request object. Useful if you want to track its completion via a RequestObserver
     */
    public RequestVersion1 executeFavorites(List<FavoriteTaskVersion1> favorites);

    /**
     * Sets a custom gradle executable. See getCustomGradleExecutable
     *
     * @param customGradleExecutor the path to an executable (or script/batch file)
     */
    public void setCustomGradleExecutable(File customGradleExecutor);

    /**
     * Adds an observer that is notified when Gradle commands are executed and completed.
     *
     * @param observer the observer that is notified
     */
    public void addRequestObserver(RequestObserverVersion1 observer);

    /**
     * Removes a request observer when you no longer wish to receive notifications about Gradle command being executed.
     *
     * @param observer the observer to remove
     */
    public void removeRequestObserver(RequestObserverVersion1 observer);
}