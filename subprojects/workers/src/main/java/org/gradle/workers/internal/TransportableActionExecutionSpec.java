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

public class TransportableActionExecutionSpec extends AbstractSerializedActionExecutionSpec implements IsolatedClassloaderActionExecutionSpec {
    private final String displayName;
    private final String implementationClassName;
    private final byte[] serializedParameters;
    private final ClassLoaderStructure classLoaderStructure;

    public TransportableActionExecutionSpec(String displayName, Class<?> implementationClass, Object[] params, ClassLoaderStructure classLoaderStructure) {
        this.displayName = displayName;
        this.implementationClassName = implementationClass.getName();
        this.serializedParameters = serialize(params);
        this.classLoaderStructure = classLoaderStructure;
    }

    public TransportableActionExecutionSpec(String displayName, String implementationClassName, byte[] serializedParameters, ClassLoaderStructure classLoaderStructure) {
        this.displayName = displayName;
        this.implementationClassName = implementationClassName;
        this.serializedParameters = serializedParameters;
        this.classLoaderStructure = classLoaderStructure;
    }

    public static TransportableActionExecutionSpec from(ActionExecutionSpec spec) {
        if (spec instanceof SerializedParametersActionExecutionSpec) {
            SerializedParametersActionExecutionSpec serializedSpec = (SerializedParametersActionExecutionSpec) spec;
            return new TransportableActionExecutionSpec(serializedSpec.getDisplayName(), serializedSpec.getImplementationClass().getName(), serializedSpec.getSerializedParameters(), serializedSpec.getClassLoaderStructure());
        } else if (spec instanceof CompilerActionExecutionSpec) {
            CompilerActionExecutionSpec compilerSpec = (CompilerActionExecutionSpec) spec;
            return new TransportableActionExecutionSpec(compilerSpec.getDisplayName(), compilerSpec.getImplementationClass(), compilerSpec.getParams(), compilerSpec.getClassLoaderStructure());
        } else if (spec instanceof TransportableActionExecutionSpec) {
            return (TransportableActionExecutionSpec) spec;
        } else {
            throw new IllegalArgumentException("Can't create a TransportableActionExecutionSpec from spec with type: " + spec.getClass().getSimpleName());
        }
    }

    @Override
    public ClassLoaderStructure getClassLoaderStructure() {
        return classLoaderStructure;
    }

    @Override
    public Class<?> getImplementationClass() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Object[] getParams() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ActionExecutionSpec deserialize(ClassLoader classLoader) {
        try {
            Class<?> implementationClass = classLoader.loadClass(implementationClassName);
            Object[] params = (Object[]) deserialize(serializedParameters, classLoader);
            return new SimpleActionExecutionSpec(implementationClass, displayName, params);
        } catch (Exception e) {
            throw new WorkSerializationException("Could not deserialize unit of work", e);
        }
    }
}
