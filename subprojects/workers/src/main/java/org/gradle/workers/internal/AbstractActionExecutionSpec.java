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

import org.gradle.workers.WorkParameters;

import java.io.File;

public abstract class AbstractActionExecutionSpec<T extends WorkParameters> implements ActionExecutionSpec<T> {
    protected final String displayName;
    protected final File baseDir;
    protected final boolean internalServicesRequired;
    protected final ClassLoaderStructure classLoaderStructure;

    public AbstractActionExecutionSpec(String displayName, File baseDir, boolean internalServicesRequired, ClassLoaderStructure classLoaderStructure) {
        this.displayName = displayName;
        this.baseDir = baseDir;
        this.internalServicesRequired = internalServicesRequired;
        this.classLoaderStructure = classLoaderStructure;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public boolean isInternalServicesRequired() {
        return internalServicesRequired;
    }

    @Override
    public File getBaseDir() {
        return baseDir;
    }

    @Override
    public ClassLoaderStructure getClassLoaderStructure() {
        return classLoaderStructure;
    }
}
