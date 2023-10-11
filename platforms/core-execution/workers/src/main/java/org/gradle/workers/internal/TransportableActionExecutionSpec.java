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

import java.io.File;

public class TransportableActionExecutionSpec {
    protected final String implementationClassName;
    private final byte[] serializedParameters;
    private final ClassLoaderStructure classLoaderStructure;
    private final File projectDir;
    private final File rootDir;
    private final boolean usesInternalServices;

    public TransportableActionExecutionSpec(String implementationClassName, byte[] serializedParameters, ClassLoaderStructure classLoaderStructure, File projectDir, File rootDir, boolean usesInternalServices) {
        this.implementationClassName = implementationClassName;
        this.serializedParameters = serializedParameters;
        this.classLoaderStructure = classLoaderStructure;
        this.projectDir = projectDir;
        this.rootDir = rootDir;
        this.usesInternalServices = usesInternalServices;
    }

    public File getProjectDir() {
        return projectDir;
    }

    public File getRootDir() {
        return rootDir;
    }

    public boolean isInternalServicesRequired() {
        return usesInternalServices;
    }

    public ClassLoaderStructure getClassLoaderStructure() {
        return classLoaderStructure;
    }

    public String getImplementationClassName() {
        return implementationClassName;
    }

    public byte[] getSerializedParameters() {
        return serializedParameters;
    }
}
