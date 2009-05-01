/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.groovy.scripts.ScriptSource;

import java.io.File;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class SettingsFactory {
    private IProjectDescriptorRegistry projectDescriptorRegistry;
    private DependencyFactory dependencyFactory;
    private RepositoryHandler repositoryHandler;
    private ConfigurationContainerFactory configurationContainerFactory;
    private InternalRepository internalRepository;
    private BuildSourceBuilder buildSourceBuilder;

    public SettingsFactory(IProjectDescriptorRegistry projectDescriptorRegistry,
                           DependencyFactory dependencyFactory,
                           RepositoryHandler repositoryHandler,
                           ConfigurationContainerFactory configurationContainerFactory,
                           InternalRepository internalRepository,
                           BuildSourceBuilder buildSourceBuilder) {
        this.projectDescriptorRegistry = projectDescriptorRegistry;
        this.dependencyFactory = dependencyFactory;
        this.repositoryHandler = repositoryHandler;
        this.configurationContainerFactory = configurationContainerFactory;
        this.internalRepository = internalRepository;
        this.buildSourceBuilder = buildSourceBuilder;
    }

    public SettingsInternal createSettings(File settingsDir, ScriptSource settingsScript,
                                           Map<String, String> gradleProperties, StartParameter startParameter) {
        DefaultSettings settings = new DefaultSettings(dependencyFactory,
                repositoryHandler,
                configurationContainerFactory,
                internalRepository, projectDescriptorRegistry,
                buildSourceBuilder, settingsDir, settingsScript, startParameter);
        settings.getAdditionalProperties().putAll(gradleProperties);
        return settings;
    }
}
