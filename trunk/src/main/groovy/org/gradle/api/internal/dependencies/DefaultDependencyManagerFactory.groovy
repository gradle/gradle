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

package org.gradle.api.internal.dependencies

import org.apache.ivy.Ivy
import org.gradle.api.DependencyManager
import org.gradle.api.DependencyManagerFactory
import org.gradle.api.dependencies.ArtifactDependency
import org.gradle.api.dependencies.ModuleDependency
import org.gradle.api.dependencies.ProjectDependency

/**
 * @author Hans Dockter
 */
class DefaultDependencyManagerFactory implements DependencyManagerFactory {
    File buildResolverDir

    DefaultDependencyManagerFactory() {}

    DefaultDependencyManagerFactory(File buildResolverDir) {
        this.buildResolverDir = buildResolverDir
    }

    DependencyManager createDependencyManager() {
        return new DefaultDependencyManager(
                Ivy.newInstance(),
                new DependencyFactory([ArtifactDependency, ModuleDependency, ProjectDependency]),
                new ArtifactFactory(),
                new SettingsConverter(),
                new ModuleDescriptorConverter(),
                new Report2Classpath(),
                buildResolverDir)
    }
}
