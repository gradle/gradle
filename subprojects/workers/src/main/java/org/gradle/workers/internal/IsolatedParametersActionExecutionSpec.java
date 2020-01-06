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

public class IsolatedParametersActionExecutionSpec<T extends WorkParameters> {
    private final Class<? extends WorkAction<T>> implementationClass;
    private final String actionImplementationClassName;
    private final Isolatable<T> isolatedParams;
    private final ClassLoaderStructure classLoaderStructure;
    private final File baseDir;
    private final boolean usesInternalServices;
    private final String displayName;

    public IsolatedParametersActionExecutionSpec(Class<? extends WorkAction<T>> implementationClass, String displayName, String actionImplementationClassName, Isolatable<T> isolatedParams, ClassLoaderStructure classLoaderStructure, File baseDir, boolean usesInternalServices) {
        this.implementationClass = implementationClass;
        this.displayName = displayName;
        this.actionImplementationClassName = actionImplementationClassName;
        this.isolatedParams = isolatedParams;
        this.classLoaderStructure = classLoaderStructure;
        this.baseDir = baseDir;
        this.usesInternalServices = usesInternalServices;
    }

    public String getDisplayName() {
        return displayName;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public ClassLoaderStructure getClassLoaderStructure() {
        return classLoaderStructure;
    }

    /**
     * The action to instantiate and execute, possibly a wrapper.
     */
    public Class<? extends WorkAction<T>> getImplementationClass() {
        return implementationClass;
    }

    /**
     * The action that will do the work.
     */
    public String getActionImplementationClassName() {
        return actionImplementationClassName;
    }

    public boolean isInternalServicesRequired() {
        return usesInternalServices;
    }

    public Isolatable<T> getIsolatedParams() {
        return isolatedParams;
    }
}
