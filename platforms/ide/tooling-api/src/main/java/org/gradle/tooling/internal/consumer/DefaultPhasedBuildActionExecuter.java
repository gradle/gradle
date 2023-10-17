/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Transformer;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.IntermediateModelListener;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerAction;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.util.internal.CollectionUtils;

import java.util.Arrays;

public class DefaultPhasedBuildActionExecuter extends AbstractLongRunningOperation<DefaultPhasedBuildActionExecuter> implements BuildActionExecuter<Void> {
    private final PhasedBuildAction phasedBuildAction;
    private final AsyncConsumerActionExecutor connection;

    DefaultPhasedBuildActionExecuter(PhasedBuildAction phasedBuildAction, AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        super(parameters);
        operationParamsBuilder.setEntryPoint("PhasedBuildActionExecuter API");
        this.phasedBuildAction = phasedBuildAction;
        this.connection = connection;
    }

    @Override
    protected DefaultPhasedBuildActionExecuter getThis() {
        return this;
    }

    @Override
    public void setIntermediateModelListener(IntermediateModelListener intermediateModelListener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BuildActionExecuter<Void> forTasks(String... tasks) {
        operationParamsBuilder.setTasks(tasks != null ? Arrays.asList(tasks) : null);
        return getThis();
    }

    @Override
    public BuildActionExecuter<Void> forTasks(Iterable<String> tasks) {
        operationParamsBuilder.setTasks(tasks != null ? CollectionUtils.toList(tasks) : null);
        return getThis();
    }

    @Override
    public Void run() throws GradleConnectionException, IllegalStateException {
        BlockingResultHandler<Void> handler = new BlockingResultHandler<Void>(Void.class);
        run(handler);
        handler.getResult();
        return null;
    }

    @Override
    public void run(ResultHandler<? super Void> handler) throws IllegalStateException {
        final ConsumerOperationParameters operationParameters = getConsumerOperationParameters();
        connection.run(new ConsumerAction<Void>() {
            @Override
            public ConsumerOperationParameters getParameters() {
                return operationParameters;
            }

            @Override
            public Void run(ConsumerConnection connection) {
                connection.run(phasedBuildAction, operationParameters);
                return null;
            }
        }, new ResultHandlerAdapter<Void>(handler, new ExceptionTransformer(new Transformer<String, Throwable>() {
            @Override
            public String transform(Throwable throwable) {
                return String.format("Could not run phased build action using %s.", connection.getDisplayName());
            }
        })));
    }


}
