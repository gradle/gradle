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
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.ModelFetchParametersVersion1;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.tooling.model.Project;

import java.io.OutputStream;

public class DefaultModelBuilder<T extends Project> implements ModelBuilder<T> {
    private final Class<T> modelType;
    private final Class<? extends ProjectVersion3> protocolType;
    private final ConnectionVersion4 connection;
    private final ProtocolToModelAdapter adapter;
    private OutputStream stdout;
    private OutputStream stderr;

    public DefaultModelBuilder(Class<T> modelType, Class<? extends ProjectVersion3> protocolType, ConnectionVersion4 connection, ProtocolToModelAdapter adapter) {
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
        final ResultHandler<ProjectVersion3> adaptingHandler = new ResultHandler<ProjectVersion3>() {
            public void onComplete(ProjectVersion3 result) {
                handler.onComplete(adapter.adapt(modelType, result));

            }

            public void onFailure(GradleConnectionException failure) {
                handler.onFailure(failure);
            }
        };
        connection.getModel(protocolType, new ModelFetchParameters(), new ResultHandlerAdapter<ProjectVersion3>(adaptingHandler) {
            @Override
            protected String connectionFailureMessage(Throwable failure) {
                return String.format("Could not fetch model of type '%s' from %s.", modelType.getSimpleName(), connection.getDisplayName());
            }
        });
    }

    public ModelBuilder<T> setStandardOutput(OutputStream outputStream) {
        stdout = outputStream;
        return this;
    }

    public ModelBuilder<T> setStandardError(OutputStream outputStream) {
        stderr = outputStream;
        return this;
    }

    private class ModelFetchParameters implements ModelFetchParametersVersion1 {
        public OutputStream getStandardOutput() {
            return stdout;
        }

        public OutputStream getStandardError() {
            return stderr;
        }
    }
}
