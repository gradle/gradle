/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext;
import org.gradle.internal.lazy.Lazy;

class DefaultSharedDependencyResolutionServices implements SharedDependencyResolutionServices {
    private final Lazy<DependencyResolutionServices> dependencyResolutionServices;

    public DefaultSharedDependencyResolutionServices(DependencyManagementServices dependencyManagementServices,
                                                     FileResolver fileResolver,
                                                     FileCollectionFactory fileCollectionFactory,
                                                     DependencyMetaDataProvider dependencyMetaDataProvider) {
        dependencyResolutionServices = Lazy.locking().of(() -> dependencyManagementServices.create(fileResolver, fileCollectionFactory, dependencyMetaDataProvider, makeUnknownProjectFinder(), RootScriptDomainObjectContext.INSTANCE));
    }

    @Override
    public RepositoryHandler getResolveRepositoryHandler() {
        return dependencyResolutionServices.get().getResolveRepositoryHandler();
    }

    private static ProjectFinder makeUnknownProjectFinder() {
        return new UnknownProjectFinder("Project dependencies are not allowed in shared dependency resolution services");
    }
}
