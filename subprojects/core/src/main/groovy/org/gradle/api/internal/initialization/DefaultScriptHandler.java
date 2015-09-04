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

import groovy.lang.Closure;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.net.URI;

public class DefaultScriptHandler implements ScriptHandler, ScriptHandlerInternal {
    private static final Logger LOGGER = Logging.getLogger(DefaultScriptHandler.class);

    private final ScriptSource scriptSource;
    private final ClassLoaderScope classLoaderScope;
    private final DependencyResolutionServices dependencyResolutionServices;
    // The following values are relatively expensive to create, so defer creation until required
    private RepositoryHandler repositoryHandler;
    private DependencyHandler dependencyHandler;
    private ConfigurationContainer configContainer;
    private Configuration classpathConfiguration;

    public DefaultScriptHandler(ScriptSource scriptSource, DependencyResolutionServices dependencyResolutionServices, ClassLoaderScope classLoaderScope) {
        this.dependencyResolutionServices = dependencyResolutionServices;
        this.scriptSource = scriptSource;
        this.classLoaderScope = classLoaderScope;
    }

    public void dependencies(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getDependencies());
    }

    @Override
    public void addScriptClassPathDependency(Object notation) {
        getDependencies().add(ScriptHandler.CLASSPATH_CONFIGURATION, notation);
    }

    @Override
    public ClassPath getScriptClassPath() {
        if (classpathConfiguration == null) {
            return new DefaultClassPath();
        }
        return new DefaultClassPath(classpathConfiguration.getFiles());
    }

    public DependencyHandler getDependencies() {
        defineConfiguration();
        if (dependencyHandler == null) {
            dependencyHandler = dependencyResolutionServices.getDependencyHandler();
        }
        return dependencyHandler;
    }

    public RepositoryHandler getRepositories() {
        if (repositoryHandler == null) {
            repositoryHandler = dependencyResolutionServices.getResolveRepositoryHandler();
        }
        return repositoryHandler;
    }

    public void repositories(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getRepositories());
    }

    public ConfigurationContainer getConfigurations() {
        defineConfiguration();
        return configContainer;
    }

    private void defineConfiguration() {
        // Defer creation and resolution of configuration until required. Short-circuit when script does not require classpath
        if (configContainer == null) {
            configContainer = dependencyResolutionServices.getConfigurationContainer();
        }
        if (classpathConfiguration == null) {
            classpathConfiguration = configContainer.create(CLASSPATH_CONFIGURATION);
        }
    }

    public File getSourceFile() {
        return scriptSource.getResource().getFile();
    }

    public URI getSourceURI() {
        return scriptSource.getResource().getURI();
    }

    public ClassLoader getClassLoader() {
        if (!classLoaderScope.isLocked()) {
            LOGGER.debug("Eager creation of script class loader for {}. This may result in performance issues.", scriptSource);
        }
        return classLoaderScope.getLocalClassLoader();
    }

}
