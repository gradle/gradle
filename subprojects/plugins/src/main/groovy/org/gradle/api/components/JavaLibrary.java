/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.components;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.internal.component.SoftwareComponentInternal;

/**
 * A SoftwareComponent representing a library that runs on a java virtual machine.
 */
@Incubating
public class JavaLibrary implements SoftwareComponentInternal {

    private final Configuration runtimeConfiguration;

    public JavaLibrary(Configuration runtimeConfiguration) {
        this.runtimeConfiguration = runtimeConfiguration;
    }

    public String getName() {
        return "java";
    }

    public PublishArtifactSet getArtifacts() {
        return runtimeConfiguration.getArtifacts();
    }

    public DependencySet getRuntimeDependencies() {
        return runtimeConfiguration.getAllDependencies();
    }
}
