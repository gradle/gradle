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

import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.async.AsyncConnection;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.model.Model;

class DefaultProjectConnection implements ProjectConnection {
    private final AsyncConnection connection;
    private final ModelMapping modelMapping = new ModelMapping();
    private ProtocolToModelAdapter adapter;
    private final ConnectionParameters parameters;

    public DefaultProjectConnection(AsyncConnection connection, ProtocolToModelAdapter adapter, ConnectionParameters parameters) {
        this.connection = connection;
        this.parameters = parameters;
        this.adapter = adapter;
    }

    public void close() {
        connection.stop();
    }

    public <T extends Model> T getModel(Class<T> viewType) {
        return model(viewType).get();
    }

    public <T extends Model> void getModel(final Class<T> viewType, final ResultHandler<? super T> handler) {
        model(viewType).get(handler);
    }

    public BuildLauncher newBuild() {
        return new DefaultBuildLauncher(connection, parameters);
    }

    public <T extends Model> ModelBuilder<T> model(Class<T> modelType) {
        return new DefaultModelBuilder<T, Class>(modelType, mapToProtocol(modelType), connection, adapter, parameters);
    }

    private Class mapToProtocol(Class<? extends Model> viewType) {
        Class protocolViewType = modelMapping.getInternalType(viewType);
        if (protocolViewType == null) {
            throw new UnknownModelException(
                    "Unknown model: '" + viewType.getSimpleName() + "'.\n"
                        + "Most likely you are trying to acquire a model for a class that is not a valid Tooling API model class.\n"
                        + "Review the documentation on the version of Tooling API you use to find out what models can be build."
            );
        }
        return protocolViewType;
    }
}
