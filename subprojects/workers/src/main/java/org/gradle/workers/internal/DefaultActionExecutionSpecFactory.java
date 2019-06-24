/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal;

import org.gradle.internal.Cast;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.internal.snapshot.impl.IsolatedArray;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class DefaultActionExecutionSpecFactory implements ActionExecutionSpecFactory {
    private final IsolatableFactory isolatableFactory;
    private final IsolatableSerializerRegistry serializerRegistry;

    public DefaultActionExecutionSpecFactory(IsolatableFactory isolatableFactory, IsolatableSerializerRegistry serializerRegistry) {
        this.isolatableFactory = isolatableFactory;
        this.serializerRegistry = serializerRegistry;
    }

    @Override
    public TransportableActionExecutionSpec newTransportableSpec(String displayName, Class<?> implementationClass, Object[] params, ClassLoaderStructure classLoaderStructure) {
        return new TransportableActionExecutionSpec(displayName, implementationClass.getName(), serialize(isolatableFactory.isolate(params)), classLoaderStructure);
    }

    @Override
    public TransportableActionExecutionSpec newTransportableSpec(ActionExecutionSpec spec) {
        if (spec instanceof IsolatedParametersActionExecutionSpec) {
            IsolatedParametersActionExecutionSpec isolatedSpec = (IsolatedParametersActionExecutionSpec) spec;
            return new TransportableActionExecutionSpec(isolatedSpec.getDisplayName(), isolatedSpec.getImplementationClass().getName(), serialize(isolatedSpec.getIsolatedParams()), isolatedSpec.getClassLoaderStructure());
        } else if (spec instanceof TransportableActionExecutionSpec) {
            return (TransportableActionExecutionSpec) spec;
        } else {
            throw new IllegalArgumentException("Can't create a TransportableActionExecutionSpec from spec with type: " + spec.getClass().getSimpleName());
        }
    }

    @Override
    public IsolatedParametersActionExecutionSpec newIsolatedSpec(String displayName, Class<?> implementationClass, Object[] params, ClassLoaderStructure classLoaderStructure) {
        return new IsolatedParametersActionExecutionSpec(implementationClass, displayName, (IsolatedArray) isolatableFactory.isolate(params), classLoaderStructure);
    }

    @Override
    public SimpleActionExecutionSpec newSimpleSpec(ActionExecutionSpec spec) {
        if (spec instanceof TransportableActionExecutionSpec) {
            TransportableActionExecutionSpec transportableSpec = (TransportableActionExecutionSpec) spec;
            Object[] params = Cast.uncheckedCast(deserialize(transportableSpec.getSerializedParameters()).isolate());
            return new SimpleActionExecutionSpec(fromClassName(transportableSpec.getImplementationClassName()), transportableSpec.getDisplayName(), params, transportableSpec.getClassLoaderStructure());
        } else if (spec instanceof IsolatedParametersActionExecutionSpec) {
            IsolatedParametersActionExecutionSpec isolatedSpec = (IsolatedParametersActionExecutionSpec) spec;
            Object[] params = Cast.uncheckedCast(isolatedSpec.getIsolatedParams().isolate());
            return new SimpleActionExecutionSpec(isolatedSpec.getImplementationClass(), isolatedSpec.getDisplayName(), params, isolatedSpec.getClassLoaderStructure());
        } else {
            throw new IllegalArgumentException("Can't create a SimpleActionExecutionSpec from spec with type: " + spec.getClass().getSimpleName());
        }
    }

    Class<?> fromClassName(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new WorkSerializationException("Could not deserialize unit of work.", e);
        }
    }

    private byte[] serialize(Isolatable<?> isolatable) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        KryoBackedEncoder encoder = new KryoBackedEncoder(outputStream);
        try {
            serializerRegistry.writeIsolatable(encoder, isolatable);
            encoder.flush();
        } catch (Exception e) {
            throw new WorkSerializationException("Could not serialize unit of work.", e);
        }
        return outputStream.toByteArray();
    }

    private Isolatable<?> deserialize(byte[] bytes) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        KryoBackedDecoder decoder = new KryoBackedDecoder(inputStream);
        try {
            return serializerRegistry.readIsolatable(decoder);
        } catch (Exception e) {
            throw new WorkSerializationException("Could not deserialize unit of work.", e);
        }
    }
}
