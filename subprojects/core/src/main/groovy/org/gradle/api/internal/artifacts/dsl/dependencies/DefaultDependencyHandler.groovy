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

package org.gradle.api.internal.artifacts.dsl.dependencies

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.util.ConfigureUtil
import org.gradle.util.GUtil

class DefaultDependencyHandler implements DependencyHandler {
    ConfigurationContainer configurationContainer
    DependencyFactory dependencyFactory
    ProjectFinder projectFinder

    def DefaultDependencyHandler(ConfigurationContainer configurationContainer, DependencyFactory dependencyFactory,
                                 ProjectFinder projectFinder) {
        this.configurationContainer = configurationContainer
        this.dependencyFactory = dependencyFactory
        this.projectFinder = projectFinder
    }

    public Dependency add(String configurationName, Object dependencyNotation) {
        doAdd(configurationContainer[configurationName], dependencyNotation, null)
    }

    public Dependency add(String configurationName, Object dependencyNotation, Closure configureClosure) {
        doAdd(configurationContainer[configurationName], dependencyNotation, configureClosure)
    }

    Dependency create(Object dependencyNotation, Closure configureClosure = null) {
        def dependency = dependencyFactory.createDependency(dependencyNotation)
        ConfigureUtil.configure(configureClosure, dependency)
        dependency
    }

    private Dependency doAdd(Configuration configuration, Object dependencyNotation, Closure configureClosure) {
        if (dependencyNotation instanceof Configuration) {
            Configuration other = (Configuration) dependencyNotation;
            if (!configurationContainer.contains(other)) {
                throw new UnsupportedOperationException("Currently you can only declare dependencies on configurations from the same project.")
            }
            configuration.extendsFrom(other)
            return
        }

        def dependency = create(dependencyNotation, configureClosure)
        configuration.dependencies << dependency
        dependency
    }

    public Dependency module(Object notation) {
        module(notation, null)
    }

    public Dependency project(Map notation) {
        return dependencyFactory.createProjectDependencyFromMap(projectFinder, notation)
    }

    public Dependency module(Object notation, Closure configureClosure) {
        return dependencyFactory.createModule(notation, configureClosure)
    }

    public Dependency gradleApi() {
        return dependencyFactory.createDependency(DependencyFactory.ClassPathNotation.GRADLE_API);
    }

    public Dependency localGroovy() {
        return dependencyFactory.createDependency(DependencyFactory.ClassPathNotation.LOCAL_GROOVY);
    }

    public Object methodMissing(String name, Object args) {
        Configuration configuration = configurationContainer.findByName(name)
        if (configuration == null) {
            if (!getMetaClass().respondsTo(this, name, args.size())) {
                throw new MissingMethodException(name, this.getClass(), args);
            }
        }

        Object[] normalizedArgs = GUtil.collectionize(args)
        if (normalizedArgs.length == 2 && normalizedArgs[1] instanceof Closure) {
            return doAdd(configuration, normalizedArgs[0], (Closure) normalizedArgs[1])
        } else if (normalizedArgs.length == 1) {
            return doAdd(configuration, normalizedArgs[0], (Closure) null)
        }
        normalizedArgs.each {notation ->
            doAdd(configuration, notation, null)
        }
        return null;
    }
}