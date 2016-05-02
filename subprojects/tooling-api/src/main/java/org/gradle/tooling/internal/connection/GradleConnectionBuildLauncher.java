/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.connection;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.CompositeConnectionParameters;
import org.gradle.tooling.internal.consumer.DefaultBuildLauncher;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.converters.FixedBuildIdentifierProvider;
import org.gradle.tooling.internal.gradle.ConsumerProvidedTask;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.Launchable;
import org.gradle.tooling.model.Task;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class GradleConnectionBuildLauncher extends DefaultBuildLauncher implements BuildLauncher, CompositeBuildLauncher {

    public GradleConnectionBuildLauncher(AsyncConsumerActionExecutor connection, CompositeConnectionParameters parameters) {
        super(connection, parameters);
    }

    @Override
    public BuildLauncher forTasks(String... tasks) {
        throw new UnsupportedOperationException(
            "Must specify build root directory when executing tasks by name on a GradleConnection: see `CompositeBuildLauncher.forTasks(File, String)`.");
    }

    @Override
    public DefaultBuildLauncher setStandardInput(InputStream inputStream) {
        throw new UnsupportedOperationException("This is unsupported for composite models from GradleConnections at this time.");
    }

    @Override
    protected void preprocessLaunchables(Iterable<? extends Launchable> launchables) {
        BuildIdentifier targetBuildIdentifier = null;
        for (Launchable launchable : launchables) {
            BuildIdentifier launchableBuildIdentifier = launchable.getProjectIdentifier().getBuildIdentifier();
            if (targetBuildIdentifier == null) {
                targetBuildIdentifier = launchableBuildIdentifier;
            } else if (!targetBuildIdentifier.equals(launchableBuildIdentifier)) {
                throw new IllegalArgumentException("All Launchables must originate from the same build.");
            }
        }
        operationParamsBuilder.setBuildIdentifier(targetBuildIdentifier);
    }

    @Override
    public BuildLauncher forTasks(File buildDirectory, String... tasks) {
        List<Task> taskList = new ArrayList<Task>(tasks.length);
        for (String task : tasks) {
            taskList.add(targetTask(task, buildDirectory));
        }
        return forTasks(taskList);
    }

    private Task targetTask(String task, File buildDirectory) {
        ConsumerProvidedTask taskObject = new ConsumerProvidedTask()
            .setName(task)
            .setPath(task)
            .setDescription("Task " + task)
            .setDisplayName("Task " + task);
        FixedBuildIdentifierProvider buildIdentifierProvider = new FixedBuildIdentifierProvider(new DefaultProjectIdentifier(new DefaultBuildIdentifier(buildDirectory), ":"));
        return new ProtocolToModelAdapter().adapt(Task.class, taskObject, buildIdentifierProvider);
    }
}
