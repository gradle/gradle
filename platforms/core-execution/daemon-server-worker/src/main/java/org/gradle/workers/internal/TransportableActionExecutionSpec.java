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
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class TransportableActionExecutionSpec {
    protected final String implementationClassName;
    private final byte[] serializedParameters;
    private final ClassLoaderStructure classLoaderStructure;
    private final File baseDir;
    private final Set<String> additionalAllowedServicesClassNames;
    private final File projectCacheDir;
    private final Map<String, String> systemProperties;
    private final Set<String> clearedSystemProperties;

    public TransportableActionExecutionSpec(String implementationClassName, byte[] serializedParameters, ClassLoaderStructure classLoaderStructure, File baseDir, File projectCacheDir, Set<String> additionalAllowedServicesClassNames) {
        this(implementationClassName, serializedParameters, classLoaderStructure, baseDir, projectCacheDir, additionalAllowedServicesClassNames, Collections.emptyMap(), Collections.emptySet());
    }

    public TransportableActionExecutionSpec(String implementationClassName, byte[] serializedParameters, ClassLoaderStructure classLoaderStructure, File baseDir, File projectCacheDir, Set<String> additionalAllowedServicesClassNames, Map<String, String> systemProperties, Set<String> clearedSystemProperties) {
        this.implementationClassName = implementationClassName;
        this.serializedParameters = serializedParameters;
        this.classLoaderStructure = classLoaderStructure;
        this.baseDir = baseDir;
        this.projectCacheDir = projectCacheDir;
        this.additionalAllowedServicesClassNames = additionalAllowedServicesClassNames;
        this.systemProperties = systemProperties;
        this.clearedSystemProperties = clearedSystemProperties;
    }

    /**
     * Returns a copy of this spec that sets the given system properties and clears the others while the work runs in a worker daemon.
     */
    public TransportableActionExecutionSpec withSystemProperties(Map<String, String> systemProperties, Set<String> clearedSystemProperties) {
        return new TransportableActionExecutionSpec(implementationClassName, serializedParameters, classLoaderStructure, baseDir, projectCacheDir, additionalAllowedServicesClassNames, systemProperties, clearedSystemProperties);
    }

    /**
     * System properties to set while the work runs in a worker daemon.
     */
    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }

    /**
     * System properties the worker daemon was started with, but which must be cleared while the work runs.
     */
    public Set<String> getClearedSystemProperties() {
        return clearedSystemProperties;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public Set<String> getAdditionalAllowedServicesClassNames() {
        return additionalAllowedServicesClassNames;
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

    public File getProjectCacheDir() {
        return projectCacheDir;
    }
}
