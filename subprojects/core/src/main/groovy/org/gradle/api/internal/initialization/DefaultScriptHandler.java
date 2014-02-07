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
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Factory;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.net.URI;

public class DefaultScriptHandler implements ScriptHandler {

    private final ScriptSource scriptSource;
    private final RepositoryHandler repositoryHandler;
    private final DependencyHandler dependencyHandler;
    private final ConfigurationContainer configContainer;
    private final Factory<ClassLoader> classLoaderFactory;
    private final Configuration classpathConfiguration;

    public DefaultScriptHandler(
            ScriptSource scriptSource, RepositoryHandler repositoryHandler,
            DependencyHandler dependencyHandler, ConfigurationContainer configContainer,
            Factory<ClassLoader> classLoaderFactory
    ) {
        this.repositoryHandler = repositoryHandler;
        this.dependencyHandler = dependencyHandler;
        this.scriptSource = scriptSource;
        this.configContainer = configContainer;
        this.classLoaderFactory = classLoaderFactory;
        classpathConfiguration = configContainer.create(CLASSPATH_CONFIGURATION);
    }

    public void dependencies(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, dependencyHandler);
    }

    protected Configuration getClasspathConfiguration() {
        return classpathConfiguration;
    }

    public DependencyHandler getDependencies() {
        return dependencyHandler;
    }

    public RepositoryHandler getRepositories() {
        return repositoryHandler;
    }

    public void repositories(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, repositoryHandler);
    }

    public ConfigurationContainer getConfigurations() {
        return configContainer;
    }

    public File getSourceFile() {
        return scriptSource.getResource().getFile();
    }

    public URI getSourceURI() {
        return scriptSource.getResource().getURI();
    }

    public ClassLoader getClassLoader() {
        return classLoaderFactory.create();
    }

}
