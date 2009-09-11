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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.ConfigurationHandler
import org.gradle.api.internal.artifacts.IvyService
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer

/**
 * @author Hans Dockter
 */
class DefaultConfigurationHandler extends DefaultConfigurationContainer implements ConfigurationHandler {
    def DefaultConfigurationHandler(IvyService ivyService) {
        super(ivyService)
    }

    // These are here to make Groovy 1.6 happy

    public Configuration add(String name) {
        super.add(name)
    }

    public Configuration add(String name, Closure configureClosure) {
        super.add(name, configureClosure)
    }

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
