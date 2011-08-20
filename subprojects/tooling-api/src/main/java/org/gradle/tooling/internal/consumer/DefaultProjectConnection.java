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
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion1;
import org.gradle.tooling.model.*;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaProject;

import java.util.HashMap;
import java.util.Map;

class DefaultProjectConnection implements ProjectConnection {
    private final AsyncConnection connection;
    private final Map<Class<? extends Element>, Class<? extends ProjectVersion3>> modelTypeMap = new HashMap<Class<? extends Element>, Class<? extends ProjectVersion3>>();
    private ProtocolToModelAdapter adapter;
    private final ConnectionParameters parameters;

    public DefaultProjectConnection(AsyncConnection connection, ProtocolToModelAdapter adapter, ConnectionParameters parameters) {
        this.connection = connection;
        this.parameters = parameters;
        this.adapter = adapter;
        modelTypeMap.put(Project.class, ProjectVersion3.class);
        modelTypeMap.put(BuildableProject.class, BuildableProjectVersion1.class);
        modelTypeMap.put(HierarchicalProject.class, HierarchicalProjectVersion1.class);
        modelTypeMap.put(HierarchicalEclipseProject.class, HierarchicalEclipseProjectVersion1.class);
        modelTypeMap.put(EclipseProject.class, EclipseProjectVersion3.class);
        modelTypeMap.put(IdeaProject.class, InternalIdeaProject.class);
        modelTypeMap.put(GradleProject.class, InternalGradleProject.class);
        modelTypeMap.put(BasicIdeaProject.class, InternalBasicIdeaProject.class);
    }

    public void close() {
        connection.stop();
    }

    public <T extends Element> T getModel(Class<T> viewType) {
        return model(viewType).get();
    }

    public <T extends Element> void getModel(final Class<T> viewType, final ResultHandler<? super T> handler) {
        model(viewType).get(handler);
    }

    public BuildLauncher newBuild() {
        return new DefaultBuildLauncher(connection, parameters);
    }

    public <T extends Element> ModelBuilder<T> model(Class<T> modelType) {
        return new DefaultModelBuilder<T>(modelType, mapToProtocol(modelType), connection, adapter, parameters);
    }

    private Class<? extends ProjectVersion3> mapToProtocol(Class<? extends Element> viewType) {
        Class<? extends ProjectVersion3> protocolViewType = modelTypeMap.get(viewType);
        if (protocolViewType == null) {
            throw new UnsupportedVersionException(String.format("Model of type '%s' is not supported.", viewType.getSimpleName()));
        }
        return protocolViewType;
    }
}
