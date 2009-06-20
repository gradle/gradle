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

import org.gradle.api.initialization.dsl.ScriptClasspathHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.util.ConfigureUtil;
import groovy.lang.Closure;

import java.net.URLClassLoader;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.File;

public class DefaultScriptClasspathHandler implements ScriptClasspathHandler, ScriptClassLoaderProvider {
    private final RepositoryHandler repositoryHandler;
    private final DependencyHandler dependencyHandler;
    private final ProjectClassLoader classLoader;
    private final Configuration classpathConfiguration;

    public DefaultScriptClasspathHandler(RepositoryHandler repositoryHandler, DependencyHandler dependencyHandler,
                                         ConfigurationContainer configContainer, ClassLoader classLoader) {
        this.repositoryHandler = repositoryHandler;
        this.dependencyHandler = dependencyHandler;
        this.classLoader = new ProjectClassLoader(classLoader);
        classpathConfiguration = configContainer.add("classpath");
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

    private static class ProjectClassLoader extends URLClassLoader {
        public ProjectClassLoader(ClassLoader classLoader) {
            super(new URL[0], classLoader);
        }

        @Override
        protected void addURL(URL url) {
            super.addURL(url);
        }
    }
}
