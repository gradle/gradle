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

import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.classloader.MutableURLClassLoader;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DefaultScriptHandlerFactory implements ScriptHandlerFactory {
    private final DependencyManagementServices dependencyManagementServices;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final Map<Collection<Object>, MutableURLClassLoader> classLoaderCache = new HashMap<Collection<Object>, MutableURLClassLoader>();
    private final FileResolver fileResolver;
    private final ProjectFinder projectFinder = new ProjectFinder() {
        public ProjectInternal getProject(String path) {
            throw new UnknownProjectException("Cannot use project dependencies in a script classpath definition.");
        }
    };

    public DefaultScriptHandlerFactory(DependencyManagementServices dependencyManagementServices,
                                       FileResolver fileResolver,
                                       DependencyMetaDataProvider dependencyMetaDataProvider) {
        this.dependencyManagementServices = dependencyManagementServices;
        this.fileResolver = fileResolver;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
    }

    public ScriptHandlerInternal create(ScriptSource scriptSource, ClassLoader parentClassLoader) {
        return create(scriptSource, parentClassLoader, new BasicDomainObjectContext());
    }

    public ScriptHandlerInternal create(ScriptSource scriptSource, ClassLoader parentClassLoader, DomainObjectContext context) {
        DependencyResolutionServices services = dependencyManagementServices.create(fileResolver, dependencyMetaDataProvider, projectFinder, context);
        RepositoryHandler repositoryHandler = services.getResolveRepositoryHandler();
        ConfigurationContainer configurationContainer = services.getConfigurationContainer();
        DependencyHandler dependencyHandler = services.getDependencyHandler();
        Collection<Object> key = Arrays.asList(scriptSource.getClassName(), parentClassLoader);
        MutableURLClassLoader classLoader = classLoaderCache.get(key);
        if (classLoader == null) {
            classLoader = new MutableURLClassLoader(parentClassLoader);
            classLoaderCache.put(key, classLoader);
            return new DefaultScriptHandler(scriptSource, repositoryHandler, dependencyHandler, configurationContainer, classLoader);
        }

        return new NoClassLoaderUpdateScriptHandler(classLoader, repositoryHandler, dependencyHandler, scriptSource, configurationContainer);
    }

    private static class BasicDomainObjectContext implements DomainObjectContext {
        public String absoluteProjectPath(String name) {
            return name;
        }
    }
}
