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
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.plugins.Usage;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.gradle.api.plugins.JavaPlugin.API_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME;

/**
 * A SoftwareComponent representing a library that runs on a java virtual machine.
 */
public class JavaLibrary implements SoftwareComponentInternal {
    private final UsageContext runtimeUsage = new RuntimeUsageContext();
    private final UsageContext compileUsage = new CompileUsageContext();
    private final LinkedHashSet<PublishArtifact> artifacts = new LinkedHashSet<PublishArtifact>();
    private final ConfigurationContainer configurations;

    public JavaLibrary(ArchivePublishArtifact jarArtifact, ConfigurationContainer configurations) {
        this.artifacts.add(jarArtifact);
        this.configurations = configurations;
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
                // this configuration is purely virtual, intended to build the correct set of dependencies when we publish
                // We cannot use the new runtimeClasspath configuration because it would include local, runtime only dependencies
                // and we still need, for backwards compatibility, things from the `runtime` configuration, so we end
                // up building a configuration which is the union of both `runtime` and `runtimeElements`
                Configuration runtimeConfiguration = configurations.getByName(RUNTIME_CONFIGURATION_NAME);
                Configuration runtimeElementsConfiguration = configurations.getByName(RUNTIME_ELEMENTS_CONFIGURATION_NAME);
                Configuration runtimePublishConfiguration = configurations.detachedConfiguration();
                runtimePublishConfiguration.extendsFrom(runtimeConfiguration, runtimeElementsConfiguration);
                dependencies = runtimePublishConfiguration.getAllDependencies();
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
}
