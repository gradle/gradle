/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.dsl.DependencyCollector;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyCollector;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.instantiation.generator.annotations.ManagedObjectCreator;
import org.gradle.internal.instantiation.generator.annotations.ManagedObjectProvider;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * A factory for dependency management types that can be instantiated by Gradle managed objects.
 */
@ManagedObjectProvider
@ServiceScope({Scope.Build.class, Scope.Project.class})
public class DependencyManagementMangedTypesFactory {

    private final Instantiator instantiator;
    private final DependencyFactoryInternal dependencyFactory;

    public DependencyManagementMangedTypesFactory(
        DependencyFactoryInternal dependencyFactory,
        InstantiatorFactory instantiatorFactory,
        ServiceRegistry serviceRegistry
    ) {
        this.dependencyFactory = dependencyFactory;
        this.instantiator = instantiatorFactory.decorate(serviceRegistry);
    }

    @ManagedObjectCreator
    public DependencyCollector dependencyCollector() {
        return instantiator.newInstance(DefaultDependencyCollector.class, dependencyFactory);
    }

}
