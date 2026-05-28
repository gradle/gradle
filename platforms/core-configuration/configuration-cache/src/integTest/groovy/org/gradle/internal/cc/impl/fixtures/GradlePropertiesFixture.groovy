/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl.fixtures

import groovy.transform.SelfType
import org.gradle.internal.cc.impl.AbstractConfigurationCacheIntegrationTest

import static org.gradle.initialization.properties.GradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.initialization.properties.GradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX


/**
 * Fixture to configure Gradle properties in different ways.
 */
@SelfType(AbstractConfigurationCacheIntegrationTest)
trait GradlePropertiesFixture {
    enum PropertyApplication {
        COMMAND_LINE("withPropertiesOnCommandLine", "on command line"),
        SYSTEM_PROPERTY("withPropertiesInSystemProps", "in system properties"),
        ENV_VAR("withPropertiesInEnvVars", "in environment variables"),
        PROPERTIES_FILE("withPropertiesInGradlePropertiesFile", "in properties file")

        private final String method
        private final String description

        PropertyApplication(String method, String description) {
            this.method = method
            this.description = description
        }

        @Override
        String toString() { description }
    }

    def withPropertiesOnCommandLine(Map<String, String> properties) {
        properties.collect { name, value -> "-P$name=$value".toString() }.forEach(executer::withArgument)
    }

    def withPropertiesInSystemProps(Map<String, String> properties) {
        properties.collect { name, value -> "-D$SYSTEM_PROJECT_PROPERTIES_PREFIX$name=$value".toString() }.forEach(executer::withArgument)
    }

    def withPropertiesInEnvVars(Map<String, String> properties) {
        executer.withEnvironmentVars(
            properties.collectEntries { name, value ->
                [ENV_PROJECT_PROPERTIES_PREFIX + name, value ]
            }
        )
    }

    def withPropertiesInGradlePropertiesFile(Map<String, String> properties) {
        propertiesFile.writeProperties(properties)
    }

    def withProperties(Map<String, String> properties = [:], PropertyApplication applicationMethod) {
        this."${applicationMethod.method}"(properties)
    }
}
