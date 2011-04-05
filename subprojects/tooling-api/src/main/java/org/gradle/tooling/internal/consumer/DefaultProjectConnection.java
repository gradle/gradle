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
import org.gradle.tooling.internal.protocol.BuildableProjectVersion1;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.HierarchicalProjectVersion1;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion1;
import org.gradle.tooling.model.BuildableProject;
import org.gradle.tooling.model.HierarchicalProject;
import org.gradle.tooling.model.Project;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;

import java.util.HashMap;
import java.util.Map;

class DefaultProjectConnection implements ProjectConnection {
    private final ConnectionVersion4 connection;
    private final Map<Class<? extends Project>, Class<? extends ProjectVersion3>> modelTypeMap = new HashMap<Class<? extends Project>, Class<? extends ProjectVersion3>>();
    private ProtocolToModelAdapter adapter;

    public DefaultProjectConnection(ConnectionVersion4 connection, ProtocolToModelAdapter adapter) {
        this.connection = new CloseableConnection(connection);
        this.adapter = adapter;
        modelTypeMap.put(Project.class, ProjectVersion3.class);
        modelTypeMap.put(BuildableProject.class, BuildableProjectVersion1.class);
        modelTypeMap.put(HierarchicalProject.class, HierarchicalProjectVersion1.class);
        modelTypeMap.put(HierarchicalEclipseProject.class, HierarchicalEclipseProjectVersion1.class);
        modelTypeMap.put(EclipseProject.class, EclipseProjectVersion3.class);
    }

    public void close() {
        connection.stop();
    }

    public <T extends Project> T getModel(Class<T> viewType) {
        BlockingResultHandler<T> handler = new BlockingResultHandler<T>(viewType);
        getModel(viewType, handler);

        return handler.getResult();
    }

    public <T extends Project> void getModel(final Class<T> viewType, final ResultHandler<? super T> handler) {
        final ResultHandler<ProjectVersion3> adaptingHandler = new ResultHandler<ProjectVersion3>() {
            public void onComplete(ProjectVersion3 result) {
                handler.onComplete(adapter.adapt(viewType, result));

            }

            public void onFailure(GradleConnectionException failure) {
                handler.onFailure(failure);
            }
        };
        connection.getModel(mapToProtocol(viewType), new ResultHandlerAdapter<ProjectVersion3>(adaptingHandler) {
            @Override
            protected String connectionFailureMessage(Throwable failure) {
                return String.format("Could not fetch model of type '%s' from %s.", viewType.getSimpleName(), connection.getDisplayName());
            }
        });
    }

    public BuildLauncher newBuild() {
        return new DefaultBuildLauncher(connection);
    }

    private Class<? extends ProjectVersion3> mapToProtocol(Class<? extends Project> viewType) {
        Class<? extends ProjectVersion3> protocolViewType = modelTypeMap.get(viewType);
        if (protocolViewType == null) {
            throw new UnsupportedVersionException(String.format("Model of type '%s' is not supported.", viewType.getSimpleName()));
        }
        return protocolViewType;
    }

}
