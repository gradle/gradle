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
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion1;
import org.gradle.tooling.model.BuildableProject;
import org.gradle.tooling.model.HierarchicalProject;
import org.gradle.tooling.model.Project;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;
import org.gradle.util.UncheckedException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

class DefaultProjectConnection implements ProjectConnection {
    private final ConnectionVersion3 connection;
    private final Map<Class<? extends Project>, Class<? extends ProjectVersion3>> modelTypeMap = new HashMap<Class<? extends Project>, Class<? extends ProjectVersion3>>();
    private ProtocolToModelAdapter adapter;
    private AtomicBoolean closed = new AtomicBoolean();

    public DefaultProjectConnection(ConnectionVersion3 connection, ProtocolToModelAdapter adapter) {
        this.connection = connection;
        this.adapter = adapter;
        modelTypeMap.put(Project.class, ProjectVersion3.class);
        modelTypeMap.put(BuildableProject.class, BuildableProjectVersion1.class);
        modelTypeMap.put(HierarchicalProject.class, HierarchicalProjectVersion1.class);
        modelTypeMap.put(HierarchicalEclipseProject.class, HierarchicalEclipseProjectVersion1.class);
        modelTypeMap.put(EclipseProject.class, EclipseProjectVersion3.class);
    }

    public void close() {
        if (!closed.getAndSet(true)) {
            connection.stop();
        }
    }

    public <T extends Project> T getModel(Class<T> viewType) {
        if (closed.get()) {
            throw new IllegalStateException("This connection has been closed.");
        }

        final BlockingQueue<Object> queue = new ArrayBlockingQueue<Object>(1);
        getModel(viewType, new ResultHandler<T>() {
            public void onComplete(T result) {
                queue.add(result);
            }

            public void onFailure(GradleConnectionException failure) {
                queue.add(failure);
            }
        });

        Object result;
        try {
            result = queue.take();
        } catch (InterruptedException e) {
            throw UncheckedException.asUncheckedException(e);
        }

        if (result instanceof GradleConnectionException) {
            throw (GradleConnectionException) result;
        }
        return viewType.cast(result);
    }

    public <T extends Project> void getModel(final Class<T> viewType, final ResultHandler<? super T> handler) {
        connection.getModel(mapToProtocol(viewType), new ResultHandlerVersion1<ProjectVersion3>() {
            public void onComplete(ProjectVersion3 result) {
                handler.onComplete(adapter.adapt(viewType, result));
            }

            public void onFailure(Throwable failure) {
                handler.onFailure(new GradleConnectionException(String.format("Could not fetch model of type '%s' from %s.", viewType.getSimpleName(), connection.getDisplayName()), failure));
            }
        });
    }

    private Class<? extends ProjectVersion3> mapToProtocol(Class<? extends Project> viewType) {
        Class<? extends ProjectVersion3> protocolViewType = modelTypeMap.get(viewType);
        if (protocolViewType == null) {
            throw new UnsupportedVersionException(String.format("Model of type '%s' is not supported.", viewType.getSimpleName()));
        }
        return protocolViewType;
    }
}
