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

import org.gradle.internal.isolation.Isolatable;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.io.File;

public class IsolatedParametersActionExecutionSpec<T extends WorkParameters> extends AbstractActionExecutionSpec<T> {
    private final Class<? extends WorkAction<T>> implementationClass;
    private final Isolatable<T> isolatedParams;

    public IsolatedParametersActionExecutionSpec(Class<? extends WorkAction<T>> implementationClass, String displayName, Isolatable<T> isolatedParams, ClassLoaderStructure classLoaderStructure, File baseDir, boolean usesInternalServices) {
        super(displayName, baseDir, usesInternalServices, classLoaderStructure);
        this.implementationClass = implementationClass;
        this.isolatedParams = isolatedParams;
    }

    @Override
    public Class<? extends WorkAction<T>> getImplementationClass() {
        return implementationClass;
    }

    @Override
    public T getParameters() {
        return isolatedParams.isolate();
    }

    public Isolatable<T> getIsolatedParams() {
        return isolatedParams;
    }
}
