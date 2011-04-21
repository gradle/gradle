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

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.tooling.model.Project;

import java.io.OutputStream;

public class DefaultModelBuilder<T extends Project> extends AbstractLongRunningOperation implements ModelBuilder<T> {
    private final Class<T> modelType;
    private final Class<? extends ProjectVersion3> protocolType;
    private final AsyncConnection connection;
    private final ProtocolToModelAdapter adapter;

    public DefaultModelBuilder(Class<T> modelType, Class<? extends ProjectVersion3> protocolType, AsyncConnection connection, ProtocolToModelAdapter adapter, ConnectionParameters parameters) {
        super(parameters);
        this.modelType = modelType;
        this.protocolType = protocolType;
        this.connection = connection;
        this.adapter = adapter;
    }

    public T get() throws GradleConnectionException {
        BlockingResultHandler<T> handler = new BlockingResultHandler<T>(modelType);
        get(handler);
        return handler.getResult();
    }

    public void get(final ResultHandler<? super T> handler) throws IllegalStateException {
        ResultHandler<ProjectVersion3> adaptingHandler = new ProtocolToModelAdaptingHandler(handler);
        connection.getModel(protocolType, operationParameters(), new ResultHandlerAdapter<ProjectVersion3>(adaptingHandler) {
            @Override
            protected String connectionFailureMessage(Throwable failure) {
                return String.format("Could not fetch model of type '%s' using %s.", modelType.getSimpleName(), connection.getDisplayName());
            }
        });
    }

    @Override
    public DefaultModelBuilder<T> setStandardOutput(OutputStream outputStream) {
        super.setStandardOutput(outputStream);
        return this;
    }

    @Override
    public DefaultModelBuilder<T> setStandardError(OutputStream outputStream) {
        super.setStandardError(outputStream);
        return this;
    }

    @Override
    public DefaultModelBuilder<T> addProgressListener(ProgressListener listener) {
        super.addProgressListener(listener);
        return this;
    }

    private class ProtocolToModelAdaptingHandler implements ResultHandler<ProjectVersion3> {
        private final ResultHandler<? super T> handler;

        public ProtocolToModelAdaptingHandler(ResultHandler<? super T> handler) {
            this.handler = handler;
        }

        public void onComplete(ProjectVersion3 result) {
            handler.onComplete(adapter.adapt(modelType, result));

        }

        public void onFailure(GradleConnectionException failure) {
            handler.onFailure(failure);
        }
    }
}
