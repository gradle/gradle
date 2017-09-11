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
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

public class NativeVariant implements SoftwareComponentInternal {
    private final String name;
    private final Usage linkUsage;
    private final Configuration linkElements;
    private final Usage runtimeUsage;
    private final Configuration runtimeElementsConfiguration;

    public NativeVariant(String name, @Nullable Usage linkUsage, @Nullable Configuration linkElements, Usage runtimeUsage, Configuration runtimeElements) {
        this.name = name;
        this.linkUsage = linkUsage;
        this.linkElements = linkElements;
        this.runtimeUsage = runtimeUsage;
        this.runtimeElementsConfiguration = runtimeElements;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<? extends UsageContext> getUsages() {
        if (linkElements == null) {
            return ImmutableSet.of(new SimpleUsage(runtimeUsage, runtimeElementsConfiguration));
        } else {
            return ImmutableSet.of(new SimpleUsage(linkUsage, linkElements), new SimpleUsage(runtimeUsage, runtimeElementsConfiguration));
        }
    }

    private static class SimpleUsage implements UsageContext {
        private final Usage usage;
        private final Set<PublishArtifact> artifacts;
        private final Set<ModuleDependency> dependencies;

        SimpleUsage(Usage usage, Configuration configuration) {
            this.usage = usage;
            this.artifacts = configuration.getAllArtifacts();
            Set<ModuleDependency> dependencies = configuration.getAllDependencies().withType(ModuleDependency.class);
            // Need to map project dependencies to external dependencies
            // TODO - let the publishing infrastructure do this
            // TODO - should deal with changes to target library's baseName
            Set<ModuleDependency> mapped = new LinkedHashSet<ModuleDependency>(dependencies.size());
            for (ModuleDependency dependency : dependencies) {
                if (dependency instanceof ProjectDependency) {
                    ProjectDependency projectDependency = (ProjectDependency) dependency;
                    mapped.add(new DefaultExternalModuleDependency(projectDependency.getGroup(), projectDependency.getName(), projectDependency.getVersion()));
                } else {
                    mapped.add(dependency);
                }
            }
            this.dependencies = mapped;
        }

        @Override
        public Usage getUsage() {
            return usage;
        }

        @Override
        public Set<PublishArtifact> getArtifacts() {
            return artifacts;
        }

        @Override
        public Set<ModuleDependency> getDependencies() {
            return dependencies;
        }
    }
}
