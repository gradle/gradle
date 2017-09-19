/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;

import java.util.Set;

public class NativeVariant implements SoftwareComponentInternal {
    private final String name;
    private final Usage linkUsage;
    private final Configuration linkElements;
    private final Usage runtimeUsage;
    private final Set<? extends PublishArtifact> runtimeArtifacts;
    private final Configuration runtimeElementsConfiguration;

    public NativeVariant(String name, Usage usage, Set<? extends PublishArtifact> artifacts, Configuration dependencies) {
        this.name = name;
        this.linkUsage = null;
        this.linkElements = null;
        this.runtimeUsage = usage;
        this.runtimeArtifacts = artifacts;
        this.runtimeElementsConfiguration = dependencies;
    }

    public NativeVariant(String name, Usage linkUsage, Configuration linkElements, Usage runtimeUsage, Configuration runtimeElements) {
        this.name = name;
        this.linkUsage = linkUsage;
        this.linkElements = linkElements;
        this.runtimeUsage = runtimeUsage;
        this.runtimeArtifacts = runtimeElements.getAllArtifacts();
        this.runtimeElementsConfiguration = runtimeElements;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<? extends UsageContext> getUsages() {
        if (linkElements == null) {
            return ImmutableSet.of(new SimpleUsage(runtimeUsage, runtimeArtifacts, runtimeElementsConfiguration));
        } else {
            return ImmutableSet.of(new SimpleUsage(linkUsage, linkElements.getAllArtifacts(), linkElements), new SimpleUsage(runtimeUsage, runtimeArtifacts, runtimeElementsConfiguration));
        }
    }

    private static class SimpleUsage implements UsageContext {
        private final Usage usage;
        private final Set<? extends PublishArtifact> artifacts;
        private final Set<ModuleDependency> dependencies;

        SimpleUsage(Usage usage, Set<? extends PublishArtifact> artifacts, Configuration configuration) {
            this.usage = usage;
            this.artifacts = artifacts;
            this.dependencies = configuration.getAllDependencies().withType(ModuleDependency.class);
        }

        @Override
        public Usage getUsage() {
            return usage;
        }

        @Override
        public Set<? extends PublishArtifact> getArtifacts() {
            return artifacts;
        }

        @Override
        public Set<ModuleDependency> getDependencies() {
            return dependencies;
        }
    }
}
