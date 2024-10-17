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

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.Ping;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.TestLauncher;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class DefaultProjectConnection implements ProjectConnection {
    private final AsyncConsumerActionExecutor connection;
    private final ConnectionParameters parameters;
    private final ProjectConnectionCloseListener listener;

    public DefaultProjectConnection(AsyncConsumerActionExecutor connection, ConnectionParameters parameters, ProjectConnectionCloseListener listener) {
        this.connection = connection;
        this.parameters = parameters;
        this.listener = listener;
    }

    @Override
    public void close() {
        connection.stop();
        listener.connectionClosed(this);
    }

    @Override
    public Ping ping() {
        return new DefaultPing(connection, this.parameters);
    }

    void disconnect() {
        connection.disconnect();
    }

    @Override
    public <T> T getModel(Class<T> modelType) {
        return model(modelType).get();
    }

    @Override
    public <T> void getModel(final Class<T> modelType, final ResultHandler<? super T> handler) {
        model(modelType).get(handler);
    }

    @Override
    public BuildLauncher newBuild() {
        return new DefaultBuildLauncher(connection, parameters);
    }

    @Override
    public TestLauncher newTestLauncher() {
        return new DefaultTestLauncher(connection, parameters);
    }

    @Override
    public <T> ModelBuilder<T> model(Class<T> modelType) {
        if (!modelType.isInterface()) {
            throw new IllegalArgumentException(String.format("Cannot fetch a model of type '%s' as this type is not an interface.", modelType.getName()));
        }
        return new DefaultModelBuilder<T>(modelType, connection, parameters);
    }

    @Override
    public <T> BuildActionExecuter<T> action(final BuildAction<T> buildAction) {
        return new DefaultBuildActionExecuter<T>(buildAction, connection, parameters);
    }

    @Override
    public BuildActionExecuter.Builder action() {
        return new DefaultBuildActionExecuter.Builder(connection, parameters);
    }

    @Override
    public void notifyDaemonsAboutChangedPaths(List<Path> changedPaths) {
        final List<String> absolutePaths = new ArrayList<String>(changedPaths.size());
        for (Path changedPath : changedPaths) {
            if (!changedPath.isAbsolute()) {
                throw new IllegalArgumentException(String.format("Changed path '%s' is not absolute", changedPath));
            }
            absolutePaths.add(changedPath.toString());
        }
        final ConsumerOperationParameters.Builder operationParamsBuilder = ConsumerOperationParameters.builder();
        operationParamsBuilder.setCancellationToken(new DefaultCancellationTokenSource().token());
        operationParamsBuilder.setParameters(parameters);
        operationParamsBuilder.setEntryPoint("Notify daemons about changed paths API");
        connection.run(
            new ConsumerAction<Void>() {
                @Override
                public ConsumerOperationParameters getParameters() {
                    return operationParamsBuilder.build();
                }

                @Override
                public Void run(ConsumerConnection connection) {
                    connection.notifyDaemonsAboutChangedPaths(absolutePaths, getParameters());
                    return null;
                }
            },
            new ResultHandlerAdapter<Void>(new BlockingResultHandler<Void>(Void.class),
                new ConnectionExceptionTransformer(new ConnectionExceptionTransformer.ConnectionFailureMessageProvider() {
                    @Override
                    public String getConnectionFailureMessage(Throwable throwable) {
                        return String.format("Could not notify daemons about changed paths: %s.", connection.getDisplayName());
                    }
                })
            ));
    }
}
