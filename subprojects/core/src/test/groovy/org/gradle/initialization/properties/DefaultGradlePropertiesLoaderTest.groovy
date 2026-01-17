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

import org.gradle.api.Project
import org.gradle.api.internal.StartParameterInternal
import org.gradle.initialization.Environment
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.initialization.properties.GradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.initialization.properties.GradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX

class DefaultGradlePropertiesLoaderTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    private Map<String, String> prefixedEnvironmentVariables = [:]
    private Map<String, String> prefixedSystemProperties = [:]
    private Map<String, String> projectPropertiesArgs = [:]

    private final StartParameterInternal startParameter = Mock(StartParameterInternal) {
        getProjectPropertiesUntracked() >> { projectPropertiesArgs }
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

    def "load gradle.properties from Gradle home"() {
        given:
        def expectedProperties = ["gradleHomeProp": "gradle home value"]
        def gradleHomeDir = tmpDir.createDir('gradle-home')
        withGradlePropertiesIn(gradleHomeDir, expectedProperties)

        1 * startParameter.getGradleHomeDir() >> gradleHomeDir

        when:
        def properties = gradlePropertiesLoader.loadFromGradleHome()

        then:
        properties == expectedProperties
    }

    def "load gradle.properties from Gradle user home"() {
        given:
        def expectedProperties = ["gradleUserHomeProp": "gradle user home value"]
        def gradleUserHomeDir = tmpDir.createDir("gradle-user-home")
        withGradlePropertiesIn(gradleUserHomeDir, expectedProperties)

        1 * startParameter.getGradleUserHomeDir() >> gradleUserHomeDir

        when:
        def properties = gradlePropertiesLoader.loadFromGradleUserHome()

        then:
        properties == expectedProperties
    }

    def "load gradle.properties from custom directory"() {
        given:
        def expectedProperties = ["customDirProp": "custom dir value"]
        def customDir = tmpDir.createDir("custom-dir")
        withGradlePropertiesIn(customDir, expectedProperties)

        when:
        def properties = gradlePropertiesLoader.loadFrom(customDir)

        then:
        properties == expectedProperties
    }

    def "return empty map when gradle.properties file does not exist"() {
        given:
        def customDir = tmpDir.createDir("customDir")

        when:
        def properties = gradlePropertiesLoader.loadFrom(customDir)

        then:
        properties == [:]
    }

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

    private static void withGradlePropertiesIn(TestFile dir, Map<String, String> properties) {
        dir.file(Project.GRADLE_PROPERTIES).writeProperties(properties)
    }
}
