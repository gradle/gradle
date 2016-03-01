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

package org.gradle.tooling.composite.internal;

import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.DefaultBuildLauncher;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;

import java.io.File;

public class CompositeBuildLauncher extends DefaultBuildLauncher {
    public CompositeBuildLauncher(File targetBuildDir, AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        super(connection, parameters);
        operationParamsBuilder.setCompositeTargetBuildRootDir(targetBuildDir);
    }

    public void run(final ResultHandler<? super Void> handler) {
        final ConsumerOperationParameters operationParameters = getConsumerOperationParameters();

        connection.run(new ConsumerAction<Void>() {
            public ConsumerOperationParameters getParameters() {
                return operationParameters;
            }

            public Void run(ConsumerConnection connection) {
                Void sink = connection.run(Void.class, operationParameters);
                return sink;
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
