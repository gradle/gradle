/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.protocol.BuildParametersVersion1;
import org.gradle.tooling.model.Task;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class DefaultBuildLauncher extends AbstractLongRunningOperation implements BuildLauncher {
    private final List<String> tasks = new ArrayList<String>();
    private final AsyncConnection connection;

    public DefaultBuildLauncher(AsyncConnection connection, ConnectionParameters parameters) {
        super(parameters);
        this.connection = connection;
    }

    public BuildLauncher forTasks(String... tasks) {
        this.tasks.clear();
        this.tasks.addAll(Arrays.asList(tasks));
        return this;
    }

    public BuildLauncher forTasks(Task... tasks) {
        forTasks(Arrays.asList(tasks));
        return this;
    }

    public BuildLauncher forTasks(Iterable<? extends Task> tasks) {
        this.tasks.clear();
        for (Task task : tasks) {
            this.tasks.add(task.getPath());
        }
        return this;
    }

    @Override
    public DefaultBuildLauncher setStandardError(OutputStream outputStream) {
        super.setStandardError(outputStream);
        return this;
    }

    @Override
    public DefaultBuildLauncher setStandardOutput(OutputStream outputStream) {
        super.setStandardOutput(outputStream);
        return this;
    }

    @Override
    public DefaultBuildLauncher addProgressListener(ProgressListener listener) {
        super.addProgressListener(listener);
        return this;
    }

    public void run() {
        BlockingResultHandler<Void> handler = new BlockingResultHandler<Void>(Void.class);
        run(handler);
        handler.getResult();
    }

    public void run(final ResultHandler<? super Void> handler) {
        connection.executeBuild(new DefaultBuildParameters(), operationParameters(), new ResultHandlerAdapter<Void>(handler){
            @Override
            protected String connectionFailureMessage(Throwable failure) {
                return String.format("Could not execute build using %s.", connection.getDisplayName());
            }
        });
    }

    private class DefaultBuildParameters implements BuildParametersVersion1 {
        public List<String> getTasks() {
            return tasks;
        }
    }
}
