/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.internal.StartParameterInternal
import org.gradle.initialization.properties.DefaultProjectPropertiesLoader
import org.gradle.initialization.properties.ProjectPropertiesLoader
import spock.lang.Specification

import static java.util.Collections.emptyMap
import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.initialization.IGradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX

class DefaultProjectPropertiesLoaderTest extends Specification {

    private Map<String, String> prefixedEnvironmentVariables = emptyMap()
    private Map<String, String> prefixedSystemProperties = emptyMap()
    private Map<String, String> projectPropertiesArgs = emptyMap()

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

    private final ProjectPropertiesLoader projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)

    def "load properties from environment variables with prefix"() {
        given:
        prefixedEnvironmentVariables = [
            (ENV_PROJECT_PROPERTIES_PREFIX + "envProp"): "env value"
        ]

        when:
        def properties = projectPropertiesLoader.loadProjectProperties()

        then:
        "env value" == properties["envProp"]
    }

    def "load properties from system properties with prefix"() {
        given:
        prefixedSystemProperties = [
            (SYSTEM_PROJECT_PROPERTIES_PREFIX + "systemProp"): "system value"
        ]

        when:
        def properties = projectPropertiesLoader.loadProjectProperties()

        then:
        "system value" == properties["systemProp"]
    }

    def "load properties from start parameter"() {
        given:
        projectPropertiesArgs = ["paramProp": "param value"]

        when:
        def properties = projectPropertiesLoader.loadProjectProperties()

        then:
        "param value" == properties["paramProp"]
    }
}
