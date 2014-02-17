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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingMethodException;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.resolution.ArtifactResolutionQuery;
import org.gradle.util.CollectionUtils;
import org.gradle.util.ConfigureUtil;

import java.util.List;
import java.util.Map;

public class DefaultDependencyHandler extends GroovyObjectSupport implements DependencyHandler {
    private final ConfigurationContainer configurationContainer;
    private final DependencyFactory dependencyFactory;
    private final ProjectFinder projectFinder;
    private final ComponentMetadataHandler metadataHandler;
    private final ArtifactResolutionQueryFactory resolutionQueryFactory;

    public DefaultDependencyHandler(ConfigurationContainer configurationContainer, DependencyFactory dependencyFactory,
                                    ProjectFinder projectFinder, ComponentMetadataHandler metadataHandler,
                                    ArtifactResolutionQueryFactory resolutionQueryFactory) {
        this.configurationContainer = configurationContainer;
        this.dependencyFactory = dependencyFactory;
        this.projectFinder = projectFinder;
        this.metadataHandler = metadataHandler;
        this.resolutionQueryFactory = resolutionQueryFactory;
    }

    public Dependency add(String configurationName, Object dependencyNotation) {
        return add(configurationName, dependencyNotation, null);
    }

    public Dependency add(String configurationName, Object dependencyNotation, Closure configureClosure) {
        return doAdd(configurationContainer.findByName(configurationName), dependencyNotation, configureClosure);
    }

    public Dependency create(Object dependencyNotation) {
        return create(dependencyNotation, null);
    }

    public Dependency create(Object dependencyNotation, Closure configureClosure) {
        Dependency dependency = dependencyFactory.createDependency(dependencyNotation);
        return ConfigureUtil.configure(configureClosure, dependency);
    }

    private Dependency doAdd(Configuration configuration, Object dependencyNotation, Closure configureClosure) {
        if (dependencyNotation instanceof Configuration) {
            Configuration other = (Configuration) dependencyNotation;
            if (!configurationContainer.contains(other)) {
                throw new UnsupportedOperationException("Currently you can only declare dependencies on configurations from the same project.");
            }
            configuration.extendsFrom(other);
            return null;
        }

        Dependency dependency = create(dependencyNotation, configureClosure);
        configuration.getDependencies().add(dependency);
        return dependency;
    }

    public Dependency module(Object notation) {
        return module(notation, null);
    }

    public Dependency project(Map<String, ?> notation) {
        return dependencyFactory.createProjectDependencyFromMap(projectFinder, notation);
    }

    public Dependency module(Object notation, Closure configureClosure) {
        return dependencyFactory.createModule(notation, configureClosure);
    }

    public Dependency gradleApi() {
        return dependencyFactory.createDependency(DependencyFactory.ClassPathNotation.GRADLE_API);
    }

    public Dependency localGroovy() {
        return dependencyFactory.createDependency(DependencyFactory.ClassPathNotation.LOCAL_GROOVY);
    }

    public Object methodMissing(String name, Object args) {
        Object[] argsArray = (Object[]) args;
        Configuration configuration = configurationContainer.findByName(name);
        if (configuration == null) {
            throw new MissingMethodException(name, this.getClass(), argsArray);
        }

        List<?> normalizedArgs = CollectionUtils.flattenCollections(argsArray);
        if (normalizedArgs.size() == 2 && normalizedArgs.get(1) instanceof Closure) {
            return doAdd(configuration, normalizedArgs.get(0), (Closure) normalizedArgs.get(1));
        } else if (normalizedArgs.size() == 1) {
            return doAdd(configuration, normalizedArgs.get(0), null);
        } else {
            for (Object arg : normalizedArgs) {
                doAdd(configuration, arg, null);
            }
            return null;
        }
    }

    public void components(Action<? super ComponentMetadataHandler> configureAction) {
        configureAction.execute(getComponents());
    }

    public ComponentMetadataHandler getComponents() {
        return metadataHandler;
    }

    public ArtifactResolutionQuery createArtifactResolutionQuery() {
        return resolutionQueryFactory.createArtifactResolutionQuery();
    }
}