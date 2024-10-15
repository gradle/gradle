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
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.model.Launchable;
import org.gradle.tooling.model.Task;

import java.util.Arrays;
import java.util.Collections;

public class DefaultBuildLauncher extends AbstractLongRunningOperation<DefaultBuildLauncher> implements BuildLauncher {
    protected final AsyncConsumerActionExecutor connection;

    public DefaultBuildLauncher(AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        super(parameters);
        operationParamsBuilder.setEntryPoint("BuildLauncher API");
        operationParamsBuilder.setTasks(Collections.<String>emptyList());
        this.connection = connection;
    }

    @Override
    protected DefaultBuildLauncher getThis() {
        return this;
    }

    @Override
    public BuildLauncher forTasks(String... tasks) {
        operationParamsBuilder.setTasks(Arrays.asList(tasks));
        return this;
    }

    @Override
    public BuildLauncher forTasks(Task... tasks) {
        forTasks(Arrays.asList(tasks));
        return this;
    }

    @Override
    public BuildLauncher forTasks(Iterable<? extends Task> tasks) {
        forLaunchables(tasks);
        return this;
    }

    @Override
    public BuildLauncher forLaunchables(Launchable... launchables) {
        return forLaunchables(Arrays.asList(launchables));
    }

    @Override
    public BuildLauncher forLaunchables(Iterable<? extends Launchable> launchables) {
        preprocessLaunchables(launchables);
        operationParamsBuilder.setLaunchables(launchables);
        return this;
    }

    protected void preprocessLaunchables(Iterable<? extends Launchable> launchables) {
    }

    @Override
    public void run() {
        BlockingResultHandler<Void> handler = new BlockingResultHandler<Void>(Void.class);
        run(handler);
        handler.getResult();
    }

    @Override
    public void run(final ResultHandler<? super Void> handler) {
        final ConsumerOperationParameters operationParameters = getConsumerOperationParameters();
        connection.run(new ConsumerAction<Void>() {
            @Override
            public ConsumerOperationParameters getParameters() {
                return operationParameters;
            }

            @Override
            public Void run(ConsumerConnection connection) {
                return connection.run(Void.class, operationParameters);
            }
        }, new ResultHandlerAdapter(handler));
    }

    private class ResultHandlerAdapter extends org.gradle.tooling.internal.consumer.ResultHandlerAdapter<Void> {
        public ResultHandlerAdapter(ResultHandler<? super Void> handler) {
            super(handler, DefaultBuildLauncher.this.createExceptionTransformer(new ConnectionExceptionTransformer.ConnectionFailureMessageProvider() {
                @Override
                public String getConnectionFailureMessage(Throwable throwable) {
                    return String.format("Could not execute build using %s.", connection.getDisplayName());
                }
            }));
        }
    }
}
