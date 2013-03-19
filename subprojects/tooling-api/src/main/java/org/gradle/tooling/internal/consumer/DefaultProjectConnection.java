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
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.async.AsyncConnection;
import org.gradle.tooling.model.Model;

class DefaultProjectConnection implements ProjectConnection {
    private final AsyncConnection connection;
    private final ConnectionParameters parameters;

    public DefaultProjectConnection(AsyncConnection connection, ConnectionParameters parameters) {
        this.connection = connection;
        this.parameters = parameters;
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
        return new DefaultModelBuilder<T>(modelType, connection, parameters);
    }
}
