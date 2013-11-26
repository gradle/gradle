/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.resolve.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;

public class DependencyResolvingClasspathProvider implements Factory<ClassPath> {

    private final DependencyResolutionServices dependencyResolutionServices;
    private final Dependency dependency;
    private final Action<? super RepositoryHandler> repositoriesConfigurer;

    public DependencyResolvingClasspathProvider(DependencyResolutionServices dependencyResolutionServices, Dependency dependency, Action<? super RepositoryHandler> repositoriesConfigurer) {
        this.dependencyResolutionServices = dependencyResolutionServices;
        this.dependency = dependency;
        this.repositoriesConfigurer = repositoriesConfigurer;
    }

    public ClassPath create() {
        Configuration configuration = dependencyResolutionServices.getConfigurationContainer().detachedConfiguration(dependency);
        repositoriesConfigurer.execute(dependencyResolutionServices.getResolveRepositoryHandler());
        return new DefaultClassPath(configuration.resolve());
    }

}
