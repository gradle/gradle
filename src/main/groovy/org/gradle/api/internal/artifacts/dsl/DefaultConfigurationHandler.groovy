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

package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.Rule
import org.gradle.api.artifacts.ProjectDependenciesBuildInstruction
import org.gradle.api.artifacts.dsl.ConfigurationHandler
import org.gradle.api.internal.artifacts.IvyService
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.util.ConfigureUtil
import org.gradle.api.artifacts.Configuration

/**
 * @author Hans Dockter
 */
class DefaultConfigurationHandler extends DefaultConfigurationContainer implements ConfigurationHandler {
    private boolean configuring

    def DefaultConfigurationHandler(IvyService ivyService,
                                    ProjectDependenciesBuildInstruction projectDependenciesBuildInstruction) {
        super(ivyService, projectDependenciesBuildInstruction)
        addRule([apply: {String name ->
            if (configuring) {
                add(name)
            }
        }] as Rule)
    }

    void configure(Closure configureClosure) {
        configuring = true
        try {
            ConfigureUtil.configure(configureClosure, this)
        } finally {
            configuring = false
        }
    }

    // These are here to make Groovy 1.6 happy

    public Configuration findByName(String name) {
        super.findByName(name)
    }

    public Configuration getByName(String name) {
        super.getByName(name)
    }

    public Configuration getByName(String name, Closure configureClosure) {
        super.getByName(name, configureClosure)
    }

    public Configuration getAt(String name) {
        super.getAt(name)
    }
}
