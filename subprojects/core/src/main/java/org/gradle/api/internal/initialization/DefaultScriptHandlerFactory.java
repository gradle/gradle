/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.initialization;

import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.groovy.scripts.ScriptSource;

public class DefaultScriptHandlerFactory implements ScriptHandlerFactory {
    private final DependencyManagementServices dependencyManagementServices;
    private final FileCollectionFactory fileCollectionFactory;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final ScriptClassPathResolver scriptClassPathResolver;
    private final FileResolver fileResolver;
    private final ProjectFinder projectFinder = new UnknownProjectFinder("Cannot use project dependencies in a script classpath definition.");

    public DefaultScriptHandlerFactory(DependencyManagementServices dependencyManagementServices,
                                       FileResolver fileResolver,
                                       FileCollectionFactory fileCollectionFactory,
                                       DependencyMetaDataProvider dependencyMetaDataProvider,
                                       ScriptClassPathResolver scriptClassPathResolver) {
        this.dependencyManagementServices = dependencyManagementServices;
        this.fileResolver = fileResolver;
        this.fileCollectionFactory = fileCollectionFactory;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.scriptClassPathResolver = scriptClassPathResolver;
    }

    @Override
    public ScriptHandlerInternal create(ScriptSource scriptSource, ClassLoaderScope classLoaderScope) {
        return create(scriptSource, classLoaderScope, RootScriptDomainObjectContext.INSTANCE);
    }

    @Override
    public ScriptHandlerInternal create(ScriptSource scriptSource, ClassLoaderScope classLoaderScope, DomainObjectContext context) {
        DependencyResolutionServices services = dependencyManagementServices.create(fileResolver, fileCollectionFactory, dependencyMetaDataProvider, projectFinder, context);
        return new DefaultScriptHandler(scriptSource, services, classLoaderScope, scriptClassPathResolver);
    }
}
