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

import org.gradle.tooling.GradleConnection;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.protocol.BuildVersion1;
import org.gradle.tooling.internal.protocol.ConnectionVersion1;
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseBuildVersion1;
import org.gradle.tooling.model.Build;
import org.gradle.tooling.model.eclipse.EclipseBuild;
import org.gradle.util.UncheckedException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

class DefaultGradleConnection implements GradleConnection {
    private final ConnectionVersion1 connection;
    private final Map<Class<? extends Build>, Class<? extends BuildVersion1>> modelTypeMap = new HashMap<Class<? extends Build>, Class<? extends BuildVersion1>>();
    private ProtocolToModelAdapter adapter;

    public DefaultGradleConnection(ConnectionVersion1 connection, ProtocolToModelAdapter adapter) {
        this.connection = connection;
        this.adapter = adapter;
        modelTypeMap.put(Build.class, BuildVersion1.class);
        modelTypeMap.put(EclipseBuild.class, EclipseBuildVersion1.class);
    }

    public <T extends Build> T getModel(Class<T> viewType) {
        final BlockingQueue<Object> queue = new ArrayBlockingQueue<Object>(20);
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

    public <T extends Build> void getModel(final Class<T> viewType, final ResultHandler<? super T> handler) {
        connection.getModel(mapToProtocol(viewType), new ResultHandlerVersion1<BuildVersion1>() {
            public void onComplete(BuildVersion1 result) {
                handler.onComplete(adapter.adapt(viewType, result));
            }

            public void onFailure(Throwable failure) {
                handler.onFailure(new GradleConnectionException(String.format("Could not fetch model of type '%s' from %s.", viewType.getSimpleName(), connection.getDisplayName()), failure));
            }
        });
    }

    private Class<? extends BuildVersion1> mapToProtocol(Class<? extends Build> viewType) {
        Class<? extends BuildVersion1> protocolViewType = modelTypeMap.get(viewType);
        if (protocolViewType == null) {
            throw new UnsupportedVersionException(String.format("Model of type '%s' is not supported.", viewType.getSimpleName()));
        }
        return protocolViewType;
    }
}
