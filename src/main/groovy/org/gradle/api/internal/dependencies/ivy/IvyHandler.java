/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.dependencies.ivy;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.dependencies.ivy.SettingsConverter;
import org.gradle.api.internal.dependencies.ivy.ModuleDescriptorConverter;
import org.gradle.api.dependencies.ResolveInstruction;
import org.gradle.api.dependencies.PublishInstruction;
import org.gradle.api.dependencies.ResolverContainer;
import org.gradle.api.dependencies.Configuration;
import org.gradle.api.internal.dependencies.DependencyContainerInternal;
import org.gradle.api.internal.dependencies.ArtifactContainer;
import org.gradle.api.internal.dependencies.ConfigurationContainer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.File;

/**
 * @author Hans Dockter
 */
public interface IvyHandler {
    Ivy ivy(List<DependencyResolver> dependencyResolvers, List<DependencyResolver> publishResolvers, File gradleUserHome, Map<String, ModuleDescriptor> clientModuleRegistry);

    SettingsConverter getSettingsConverter();

    ModuleDescriptorConverter getModuleDescriptorConverter();

    IvyFactory getIvyFactory();

    BuildResolverHandler getBuildResolverHandler();
    
    ResolveReport getLastResolveReport();

    List<File> resolve(String conf, Set<? extends Configuration> configurations, DependencyContainerInternal dependencyContainer, List<DependencyResolver> dependencyResolvers,
                              ResolveInstruction resolveInstruction, File gradleUserHome);

    ResolveReport resolveAsReport(String conf, Set<? extends Configuration> configurations, DependencyContainerInternal dependencyContainer, List<DependencyResolver> dependencyResolvers,
                              ResolveInstruction resolveInstruction, File gradleUserHome);

    void publish(String configuration, PublishInstruction publishInstruction,
                        List<DependencyResolver> publishResolvers, ConfigurationContainer configurationContainer,
                        DependencyContainerInternal dependencyContainer,
                        ArtifactContainer artifactContainer, File gradleUserHome);

    List<File> resolveFromReport(String conf, ResolveReport resolveReport);
}
