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

public class SerializedParametersActionExecutionSpec extends AbstractSerializedActionExecutionSpec implements IsolatedClassloaderActionExecutionSpec {
    private final String displayName;
    private final Class<?> implementationClass;
    private final byte[] serializedParameters;
    private final ClassLoaderStructure classLoaderStructure;

    public SerializedParametersActionExecutionSpec(Class<?> implementationClass, String displayName, Object[] params, ClassLoaderStructure classLoaderStructure) {
        this.implementationClass = implementationClass;
        this.serializedParameters = serialize(params);
        this.classLoaderStructure = classLoaderStructure;
        this.displayName = displayName;
    }

    @Override
    public Class<?> getImplementationClass() {
        return implementationClass;
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
    public ClassLoaderStructure getClassLoaderStructure() {
        return classLoaderStructure;
    }

    public byte[] getSerializedParameters() {
        return serializedParameters;
    }

    public ActionExecutionSpec deserialize(ClassLoader classLoader) {
        try {
            Object[] params = (Object[]) deserialize(serializedParameters, classLoader);
            return new SimpleActionExecutionSpec(implementationClass, displayName, params);
        } catch (Exception e) {
            throw new WorkSerializationException("Could not deserialize unit of work", e);
        }
    }
}
