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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.gradle.api.plugins.JavaPlugin.*;

/**
 * A SoftwareComponent representing a library that runs on a java virtual machine.
 */
public class JavaLibrary implements SoftwareComponentInternal {
    private final LinkedHashSet<PublishArtifact> artifacts = new LinkedHashSet<PublishArtifact>();
    private final UsageContext runtimeUsage;
    private final UsageContext compileUsage;
    private final ConfigurationContainer configurations;

    public JavaLibrary(ConfigurationContainer configurations, PublishArtifact... artifacts) {
        Collections.addAll(this.artifacts, artifacts);
        this.configurations = configurations;
        this.runtimeUsage = new RuntimeUsageContext();
        this.compileUsage = new CompileUsageContext();
    }

    /**
     * This constructor should not be used, and is maintained only for backwards
     * compatibility with the widely used Shadow plugin.
     */
    @Deprecated
    public JavaLibrary(PublishArtifact jarArtifact, DependencySet runtimeDependencies) {
        this.artifacts.add(jarArtifact);
        this.runtimeUsage = new BackwardsCompatibilityUsageContext(Usage.FOR_RUNTIME, runtimeDependencies);
        this.compileUsage = new BackwardsCompatibilityUsageContext(Usage.FOR_COMPILE, runtimeDependencies);
        this.configurations = null;
    }

    public String getName() {
        return "java";
    }

    public Set<UsageContext> getUsages() {
        return ImmutableSet.of(runtimeUsage, compileUsage);
    }

    private class RuntimeUsageContext implements UsageContext {

        private DependencySet dependencies;

        @Override
        public Usage getUsage() {
            return Usage.FOR_RUNTIME;
        }

        public Set<PublishArtifact> getArtifacts() {
            return artifacts;
        }

        public Set<ModuleDependency> getDependencies() {
            if (dependencies == null) {
                dependencies = configurations.getByName(RUNTIME_ELEMENTS_CONFIGURATION_NAME).getAllDependencies();
            }
            return dependencies.withType(ModuleDependency.class);
        }
    }

    private class CompileUsageContext implements UsageContext {

        private DependencySet dependencies;

        @Override
        public Usage getUsage() {
            return Usage.FOR_COMPILE;
        }

        public Set<PublishArtifact> getArtifacts() {
            return artifacts;
        }

        public Set<ModuleDependency> getDependencies() {
            if (dependencies == null) {
                Configuration apiConfiguration = configurations.findByName(API_CONFIGURATION_NAME);
                if (apiConfiguration != null) {
                    dependencies = apiConfiguration.getAllDependencies();
                } else {
                    return Collections.emptySet();
                }
            }
            return dependencies.withType(ModuleDependency.class);
        }
    }

    private class BackwardsCompatibilityUsageContext implements UsageContext {

        private final Usage usage;
        private final DependencySet runtimeDependencies;

        private BackwardsCompatibilityUsageContext(Usage usage, DependencySet runtimeDependencies) {
            this.usage = usage;
            this.runtimeDependencies = runtimeDependencies;
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
            return runtimeDependencies.withType(ModuleDependency.class);
        }
    }
}
