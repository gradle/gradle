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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.groovy.scripts.ScriptSource;

public class DefaultScriptHandlerFactory implements ScriptHandlerFactory {
    private final DependencyManagementServices dependencyManagementServices;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final FileResolver fileResolver;
    private final ProjectFinder projectFinder = new UnknownProjectFinder("Cannot use project dependencies in a script classpath definition.");

    public DefaultScriptHandlerFactory(DependencyManagementServices dependencyManagementServices,
                                       FileResolver fileResolver,
                                       DependencyMetaDataProvider dependencyMetaDataProvider) {
        this.dependencyManagementServices = dependencyManagementServices;
        this.fileResolver = fileResolver;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
    }

    public ScriptHandlerInternal create(ScriptSource scriptSource, ClassLoaderScope classLoaderScope) {
        return create(scriptSource, classLoaderScope, new BasicDomainObjectContext());
    }

    public ScriptHandlerInternal create(ScriptSource scriptSource, ClassLoaderScope classLoaderScope, DomainObjectContext context) {
        DependencyResolutionServices services = dependencyManagementServices.create(fileResolver, dependencyMetaDataProvider, projectFinder, context);
        return new DefaultScriptHandler(scriptSource, services, classLoaderScope);
    }

}
