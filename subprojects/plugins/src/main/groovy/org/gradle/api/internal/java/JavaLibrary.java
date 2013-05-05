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

package org.gradle.api.internal.java;

import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.Usage;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A SoftwareComponent representing a library that runs on a java virtual machine.
 */
public class JavaLibrary implements SoftwareComponentInternal {
    private final Usage runtimeUsage = new RuntimeUsage();
    private final LinkedHashSet<PublishArtifact> artifacts = new LinkedHashSet<PublishArtifact>();
    private final DependencySet runtimeDependencies;

    public JavaLibrary(PublishArtifact jarArtifact, DependencySet runtimeDependencies) {
        artifacts.add(jarArtifact);
        this.runtimeDependencies = runtimeDependencies;
    }

    public String getName() {
        return "java";
    }

    public Set<Usage> getUsages() {
        return Collections.singleton(runtimeUsage);
    }

    private class RuntimeUsage implements Usage {
        public String getName() {
            return "runtime";
        }

        public Set<PublishArtifact> getArtifacts() {
            return artifacts;
        }

        public Set<ModuleDependency> getDependencies() {
            return runtimeDependencies.withType(ModuleDependency.class);
        }
    }
}
