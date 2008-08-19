/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.dependencies;

import org.gradle.api.DependencyManager;
import org.gradle.api.DependencyManagerFactory;
import org.gradle.api.Project;
import org.gradle.util.WrapUtil;

import java.io.File;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultDependencyManagerFactory implements DependencyManagerFactory {
    private File buildResolverDir;

    public DefaultDependencyManagerFactory() {
    }

    public DefaultDependencyManagerFactory(File buildResolverDir) {
        this.buildResolverDir = buildResolverDir;
    }

    public DependencyManager createDependencyManager(Project project) {
        Set<IDependencyImplementationFactory> dependencyImpls = WrapUtil.toSet(
                new ModuleDependencyFactory(new DefaultExcludeRuleContainerFactory()),
                new ArtifactDependencyFactory(),
                new ProjectDependencyFactory());
        DefaultDependencyManager dependencyManager = new DefaultDependencyManager(
                new DefaultIvyFactory(),
                new DependencyFactory(dependencyImpls),
                new ArtifactFactory(),
                new SettingsConverter(),
                new ModuleDescriptorConverter(),
                new DefaultDependencyResolver(new Report2Classpath()),
                new DefaultDependencyPublisher(),
                buildResolverDir,
                new DefaultExcludeRuleContainer());
        dependencyManager.setProject(project);
        return dependencyManager;
    }

    public File getBuildResolverDir() {
        return buildResolverDir;
    }

    public void setBuildResolverDir(File buildResolverDir) {
        this.buildResolverDir = buildResolverDir;
    }
}
