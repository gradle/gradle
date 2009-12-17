/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class DefaultScriptHandler implements ScriptHandlerInternal {
    private final RepositoryHandler repositoryHandler;
    private final DependencyHandler dependencyHandler;
    private final ConfigurationContainer configContainer;
    private final MutableClassLoader classLoader;
    private final Configuration classpathConfiguration;

    public DefaultScriptHandler(RepositoryHandler repositoryHandler, DependencyHandler dependencyHandler,
                                ConfigurationContainer configContainer, ClassLoader parentClassLoader) {
        this.repositoryHandler = repositoryHandler;
        this.dependencyHandler = dependencyHandler;
        this.configContainer = configContainer;
        this.classLoader = new MutableClassLoader(parentClassLoader);
        classpathConfiguration = configContainer.add(CLASSPATH_CONFIGURATION);
    }

    public void dependencies(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, dependencyHandler);
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

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void updateClassPath() {
        for (File file : classpathConfiguration.getFiles()) {
            try {
                classLoader.addURL(file.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class MutableClassLoader extends URLClassLoader {
        public MutableClassLoader(ClassLoader parentClassLoader) {
            super(new URL[0], parentClassLoader);
        }

        @Override
        protected void addURL(URL url) {
            super.addURL(url);
        }
    }
}
