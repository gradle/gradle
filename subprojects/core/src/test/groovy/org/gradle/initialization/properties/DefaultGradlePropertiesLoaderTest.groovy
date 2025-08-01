/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.initialization.properties

import org.gradle.api.internal.StartParameterInternal
import org.gradle.initialization.Environment
import spock.lang.Specification

import static org.gradle.initialization.properties.GradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.initialization.properties.GradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX

class DefaultGradlePropertiesLoaderTest extends Specification {

    private Map<String, String> prefixedEnvironmentVariables = [:]
    private Map<String, String> prefixedSystemProperties = [:]
    private Map<String, String> projectPropertiesArgs = [:]

    private final StartParameterInternal startParameter = Mock(StartParameterInternal) {
        getProjectProperties() >> { projectPropertiesArgs }
    }

    private final Environment environment = Mock(Environment) {
        getSystemProperties() >> Mock(Environment.Properties) {
            byNamePrefix(SYSTEM_PROJECT_PROPERTIES_PREFIX) >> { prefixedSystemProperties }
        }
        getVariables() >> Mock(Environment.Properties) {
            byNamePrefix(ENV_PROJECT_PROPERTIES_PREFIX) >> { prefixedEnvironmentVariables }
        }
    }

    private final GradlePropertiesLoader gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)

    def "load properties from environment variables with prefix"() {
        given:
        prefixedEnvironmentVariables = [
            (ENV_PROJECT_PROPERTIES_PREFIX + "envProp"): "env value"
        ]

        when:
        def properties = gradlePropertiesLoader.loadFromEnvironmentVariables()

        then:
        properties["envProp"] == "env value"
    }

    def "load properties from system properties with prefix"() {
        given:
        prefixedSystemProperties = [
            (SYSTEM_PROJECT_PROPERTIES_PREFIX + "systemProp"): "system value"
        ]

        when:
        def properties = gradlePropertiesLoader.loadFromSystemProperties()

        then:
        properties["systemProp"] == "system value"
    }

    def "load properties from start parameter"() {
        given:
        projectPropertiesArgs = ["paramProp": "param value"]

        when:
        def properties = gradlePropertiesLoader.loadFromStartParameterProjectProperties()

        then:
        properties["paramProp"] == "param value"
    }
}
