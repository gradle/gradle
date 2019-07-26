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

import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

public class TransportableActionExecutionSpec<T extends WorkParameters> implements ActionExecutionSpec<T> {
    private final String displayName;
    private final String implementationClassName;
    private final byte[] serializedParameters;
    private final ClassLoaderStructure classLoaderStructure;

    public TransportableActionExecutionSpec(String displayName, String implementationClassName, byte[] serializedParameters, ClassLoaderStructure classLoaderStructure) {
        this.displayName = displayName;
        this.implementationClassName = implementationClassName;
        this.serializedParameters = serializedParameters;
        this.classLoaderStructure = classLoaderStructure;
    }

    @Override
    public ClassLoaderStructure getClassLoaderStructure() {
        return classLoaderStructure;
    }

    @Override
    public Class<? extends WorkAction<T>> getImplementationClass() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public T getParameters() {
        throw new UnsupportedOperationException();
    }

    public String getImplementationClassName() {
        return implementationClassName;
    }

    public byte[] getSerializedParameters() {
        return serializedParameters;
    }
}
