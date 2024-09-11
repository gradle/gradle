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
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.groovy.scripts.ScriptSource;

public class DefaultScriptHandlerFactory implements ScriptHandlerFactory {
    private final DependencyManagementServices dependencyManagementServices;
    private final BuildLogicBuilder buildLogicBuilder;

    public DefaultScriptHandlerFactory(
        DependencyManagementServices dependencyManagementServices,
        BuildLogicBuilder buildLogicBuilder
    ) {
        this.dependencyManagementServices = dependencyManagementServices;
        this.buildLogicBuilder = buildLogicBuilder;
    }

    @Override
    public ScriptHandlerInternal create(ScriptSource scriptSource, ClassLoaderScope classLoaderScope, DomainObjectContext context) {
        DependencyResolutionServices services = dependencyManagementServices.newBuildscriptResolver(context);
        return getDefaultScriptHandler(scriptSource, classLoaderScope, services);
    }

    @Override
    public ScriptHandlerInternal createProjectScriptHandler(
        ScriptSource scriptSource,
        ClassLoaderScope classLoaderScope,
        FileResolver fileResolver,
        FileCollectionFactory fileCollectionFactory,
        ProjectInternal project
    ) {
        DependencyResolutionServices services = dependencyManagementServices.newProjectBuildscriptResolver(
            fileResolver,
            fileCollectionFactory,
            project
        );
        return getDefaultScriptHandler(scriptSource, classLoaderScope, services);
    }

    private DefaultScriptHandler getDefaultScriptHandler(
        ScriptSource scriptSource,
        ClassLoaderScope classLoaderScope,
        DependencyResolutionServices services
    ) {
        return services.getObjectFactory().newInstance(
            DefaultScriptHandler.class,
            scriptSource,
            services,
            classLoaderScope,
            buildLogicBuilder
        );
    }
}
