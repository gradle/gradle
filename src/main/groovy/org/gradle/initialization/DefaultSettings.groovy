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

package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.api.artifacts.ResolverContainer
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.artifacts.repositories.InternalRepository
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.BaseSettings
import org.gradle.initialization.BuildSourceBuilder
import org.gradle.initialization.IProjectDescriptorRegistry
import org.gradle.api.artifacts.dsl.RepositoryHandler

/**
 * @author Hans Dockter
 */
public class DefaultSettings extends BaseSettings {
    public DefaultSettings() {}

    DefaultSettings(DependencyFactory dependencyFactory,
                    RepositoryHandler repositoryHandler,
                    ConfigurationContainerFactory configurationContainerFactory,
                    InternalRepository internalRepository,
                    IProjectDescriptorRegistry projectDescriptorRegistry,
                    BuildSourceBuilder buildSourceBuilder, File settingsDir, ScriptSource settingsScript, StartParameter startParameter) {
        super(dependencyFactory, repositoryHandler, configurationContainerFactory, internalRepository, projectDescriptorRegistry,
                buildSourceBuilder, settingsDir, settingsScript, startParameter)
    }

    def propertyMissing(String property) {
        return dynamicObjectHelper.getProperty(property)
    }

    void setProperty(String name, value) {
        dynamicObjectHelper.setProperty(name, value) 
    }
}
