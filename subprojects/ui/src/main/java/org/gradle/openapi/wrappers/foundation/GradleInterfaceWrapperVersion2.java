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
package org.gradle.openapi.wrappers.foundation;

import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.favorites.FavoriteTask;
import org.gradle.gradleplugin.foundation.request.Request;
import org.gradle.openapi.external.foundation.GradleInterfaceVersion2;
import org.gradle.openapi.external.foundation.RequestVersion1;
import org.gradle.openapi.external.foundation.favorites.FavoriteTaskVersion1;
import org.gradle.openapi.wrappers.foundation.favorites.FavoriteTaskWrapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of GradleInterfaceVersion2 meant to help shield external users from internal changes. This adds new functionality to GradleInterfaceWrapperVersion1.
 */
public class GradleInterfaceWrapperVersion2 extends GradleInterfaceWrapperVersion1 implements GradleInterfaceVersion2 {

    public GradleInterfaceWrapperVersion2(GradlePluginLord gradlePluginLord) {
        super(gradlePluginLord);
    }

    private RequestVersion1 wrapRequest(Request request) {
        if (request == null) {
            return null;
        }

        return new RequestWrapper(request);
    }

    public RequestVersion1 refreshTaskTree2() {
        return wrapRequest(gradlePluginLord.addRefreshRequestToQueue());
    }

    /**
     * This refreshes the task tree. Useful if you know you've changed something behind gradle's back or when first displaying this UI.
     *
     * @param additionalCommandLineArguments additional command line arguments to be passed to gradle when refreshing the task tree.
     */
    public RequestVersion1 refreshTaskTree2(String additionalCommandLineArguments) {
        return wrapRequest(gradlePluginLord.addRefreshRequestToQueue(additionalCommandLineArguments));
    }

    public RequestVersion1 executeCommand2(String commandLineArguments, String displayName) {
        return wrapRequest(gradlePluginLord.addExecutionRequestToQueue(commandLineArguments, displayName));
    }

    /**
     * Executes several favorites commands at once as a single command. This has the affect of simply concatenating all the favorite command lines into a single line.
     *
     * @param favorites a list of favorites. If just one favorite, it executes it normally. If multiple favorites, it executes them all at once as a single command.
     */
    public RequestVersion1 executeFavorites(List<FavoriteTaskVersion1> favorites) {
        List<FavoriteTask> tasks = new ArrayList<FavoriteTask>();

        Iterator<FavoriteTaskVersion1> iterator = favorites.iterator();
        while (iterator.hasNext()) {
            FavoriteTaskVersion1 favoriteTaskVersion1 = iterator.next();
            FavoriteTaskWrapper wrapper = (FavoriteTaskWrapper) favoriteTaskVersion1;
            tasks.add(wrapper.getFavoriteTask());
        }

        return wrapRequest(gradlePluginLord.addExecutionRequestToQueue(tasks));
    }
}
