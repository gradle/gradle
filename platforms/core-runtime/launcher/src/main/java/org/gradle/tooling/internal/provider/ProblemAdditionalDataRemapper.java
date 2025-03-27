/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.build.event.types.DefaultInternalPayloadSerializedAdditionalData;
import org.gradle.internal.build.event.types.DefaultInternalProxiedAdditionalData;
import org.gradle.internal.build.event.types.DefaultProblemDetails;
import org.gradle.internal.build.event.types.DefaultProblemEvent;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.classloader.ClassLoaderVisitor;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.impl.IsolatableSerializerRegistry;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalProblemDetailsVersion2;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.jspecify.annotations.NonNull;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProblemAdditionalDataRemapper implements BuildEventConsumer {

    private final PayloadSerializer payloadSerializer;
    private final BuildEventConsumer delegate;
    private final IsolatableSerializerRegistry isolatableSerializerRegistry;

    public ProblemAdditionalDataRemapper(PayloadSerializer payloadSerializer, BuildEventConsumer delegate, IsolatableSerializerRegistry isolatableSerializerRegistry) {
        this.payloadSerializer = payloadSerializer;
        this.delegate = delegate;
        this.isolatableSerializerRegistry = isolatableSerializerRegistry;
    }

    @Override
    public void dispatch(Object message) {
        remapAdditionalData(message);
        delegate.dispatch(message);
    }

    private void remapAdditionalData(Object message) {
        if (!(message instanceof DefaultProblemEvent)) {
            return;
        }
        DefaultProblemEvent problemEvent = (DefaultProblemEvent) message;
        InternalProblemDetailsVersion2 details = problemEvent.getDetails();
        if (!(details instanceof DefaultProblemDetails)) {
            return;
        }
        InternalAdditionalData additionalData = ((DefaultProblemDetails) details).getAdditionalData();
        if (!(additionalData instanceof DefaultInternalPayloadSerializedAdditionalData)) {
            return;
        }
        DefaultInternalPayloadSerializedAdditionalData serializedAdditionalData = (DefaultInternalPayloadSerializedAdditionalData) additionalData;
        SerializedPayload serializedType = (SerializedPayload) serializedAdditionalData.getSerializedType();

        Class<?> type = (Class<?>) payloadSerializer.deserialize(serializedType);
        if (type == null) {
            return;
        }

        byte[] isolatableBytes = serializedAdditionalData.getBytesForIsolatadObject();

        List<URL> classPath = getClassPath(type);

        VisitableURLClassLoader visitableURLClassLoader = new VisitableURLClassLoader("name", getClass().getClassLoader(), classPath);
        Object o = ClassLoaderUtils.executeInClassloader(visitableURLClassLoader, () -> {
            Isolatable<?> isolatable = isolatableSerializerRegistry.deserialize(isolatableBytes);
            return isolatable.isolate();
        });
        ((DefaultProblemDetails) details).setAdditionalData(new DefaultInternalProxiedAdditionalData(o, serializedType));
    }

    @NonNull
    private static List<URL> getClassPath(Class<?> type) {
        List<URL> classPath = new ArrayList<>();
        ((VisitableURLClassLoader) type.getClassLoader()).visit(new ClassLoaderVisitor() {
            @Override
            public void visitClassPath(URL[] urls) {
                Collections.addAll(classPath, urls);
            }
        });
        return classPath;
    }
}
