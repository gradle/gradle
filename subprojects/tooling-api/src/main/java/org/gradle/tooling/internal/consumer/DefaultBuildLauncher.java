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

import org.gradle.api.GradleException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.gradle.TaskListingTaskSelector;
import org.gradle.tooling.model.EntryPoint;
import org.gradle.tooling.model.Task;
import org.gradle.tooling.model.TaskSelector;

import java.util.*;

class DefaultBuildLauncher extends AbstractLongRunningOperation<DefaultBuildLauncher> implements BuildLauncher {
    private final AsyncConsumerActionExecutor connection;

    public DefaultBuildLauncher(AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        super(parameters);
        operationParamsBuilder.setTasks(Collections.<String>emptyList());
        this.connection = connection;
    }

    @Override
    protected DefaultBuildLauncher getThis() {
        return this;
    }

    public BuildLauncher forTasks(String... tasks) {
        operationParamsBuilder.setTasks(Arrays.asList(tasks));
        return this;
    }

    public BuildLauncher forTasks(Task... tasks) {
        forTasks(Arrays.asList(tasks));
        return this;
    }

    public BuildLauncher forTasks(Iterable<? extends Task> tasks) {
        List<String> taskPaths = new ArrayList<String>();
        for (Task task : tasks) {
            taskPaths.add(task.getPath());
        }
        operationParamsBuilder.setTasks(taskPaths);
        return this;
    }

    public BuildLauncher forEntryPoints(EntryPoint... entryPoints) {
        return forEntryPoints(Arrays.asList(entryPoints));
    }

    public BuildLauncher forEntryPoints(Iterable<? extends EntryPoint> entryPoints) {
        Set<String> taskPaths = new LinkedHashSet<String>();
        for (EntryPoint entryPoint : entryPoints) {
            if (entryPoint instanceof Task) {
                taskPaths.add(((Task) entryPoint).getPath());
            } else if (entryPoint instanceof TaskListingTaskSelector) {
                taskPaths.addAll(((TaskListingTaskSelector) entryPoint).getTasks());
            } else if (!(entryPoint instanceof TaskSelector)) {
                throw new GradleException("Only Task or TaskSelector instances are supported: "
                        + (entryPoint != null ? entryPoint.getClass() : "null"));
            }
        }
        operationParamsBuilder.setTasks(new ArrayList<String>(taskPaths));
        return this;
    }

    public void run() {
        BlockingResultHandler<Void> handler = new BlockingResultHandler<Void>(Void.class);
        run(handler);
        handler.getResult();
    }

    public void run(final ResultHandler<? super Void> handler) {
        final ConsumerOperationParameters operationParameters = operationParamsBuilder.setParameters(connectionParameters).build();
        connection.run(new ConsumerAction<Void>() {
            public ConsumerOperationParameters getParameters() {
                return operationParameters;
            }

            public Void run(ConsumerConnection connection) {
                return connection.run(Void.class, operationParameters);
            }
        }, new ResultHandlerAdapter(handler));
    }

    private class ResultHandlerAdapter extends org.gradle.tooling.internal.consumer.ResultHandlerAdapter<Void> {
        public ResultHandlerAdapter(ResultHandler<? super Void> handler) {
            super(handler);
        }

        @Override
        protected String connectionFailureMessage(Throwable failure) {
            return String.format("Could not execute build using %s.", connection.getDisplayName());
        }
    }
}
