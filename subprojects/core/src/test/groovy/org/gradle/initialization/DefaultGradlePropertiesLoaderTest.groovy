/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import static java.util.Collections.emptyMap
import static org.gradle.api.Project.SYSTEM_PROP_PREFIX
import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.initialization.IGradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX

class DefaultGradlePropertiesLoaderTest extends Specification {

    private final StartParameterInternal startParameter = Mock(StartParameterInternal)
    private final Environment environment = Mock(Environment)
    private final EnvironmentChangeTracker environmentChangeTracker = Mock(EnvironmentChangeTracker)
    private final GradleInternal gradleInternal = Mock(GradleInternal)
    private final DefaultGradlePropertiesLoader gradlePropertiesLoader = new DefaultGradlePropertiesLoader(
        startParameter,
        environment,
        environmentChangeTracker,
        gradleInternal
    )

    private File gradleUserHomeDir
    private File settingsDir
    private File gradleInstallationHomeDir
    private Map<String, String> prefixedSystemProperties = emptyMap()
    private Map<String, String> prefixedEnvironmentVariables = emptyMap()
    private Map<String, String> systemPropertiesArgs = emptyMap()
    private Map<String, String> projectPropertiesArgs = emptyMap()

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    @Rule
    public SetSystemProperties sysProp = new SetSystemProperties()

    def setup() {
        gradleUserHomeDir = tmpDir.createDir("gradleUserHome")
        settingsDir = tmpDir.createDir("settingsDir")
        gradleInstallationHomeDir = tmpDir.createDir("gradleInstallationHome")
        _ * startParameter.gradleUserHomeDir >> gradleUserHomeDir
        _ * startParameter.gradleHomeDir >> gradleInstallationHomeDir
        _ * startParameter.projectProperties >> { projectPropertiesArgs }
        _ * startParameter.systemPropertiesArgs >> { systemPropertiesArgs }
        _ * environment.systemProperties >> Mock(Environment.Properties) {
            _ * it.byNamePrefix(SYSTEM_PROJECT_PROPERTIES_PREFIX) >> { prefixedSystemProperties }
        }
        _ * environment.variables >> Mock(Environment.Properties) {
            _ * it.byNamePrefix(ENV_PROJECT_PROPERTIES_PREFIX) >> { prefixedEnvironmentVariables }
        }
    }

    private static File fromDir(File dir) {
        new File(dir, Project.GRADLE_PROPERTIES)
    }

    def mergeAddsPropertiesFromInstallationPropertiesFile() {
        given:
        1 * environment.propertiesFile(fromDir(gradleInstallationHomeDir)) >> [
            "settingsProp": "settings value"
        ]

        when:
        def properties = loadAndMergePropertiesWith(emptyMap())

        then:
        "settings value" == properties["settingsProp"]
    }

    def mergeAddsPropertiesFromUserPropertiesFile() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> [
            "userProp": "user value"
        ]

        when:
        def properties = loadAndMergePropertiesWith(emptyMap())

        then:
        "user value" == properties["userProp"]
    }

    def mergeAddsPropertiesFromSettingsPropertiesFile() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> [
            "settingsProp": "settings value"
        ]

        when:
        def properties = loadAndMergePropertiesWith(emptyMap())

        then:
        "settings value" == properties["settingsProp"]
    }

    def mergeAddsPropertiesFromEnvironmentVariablesWithPrefix() {
        given:
        prefixedEnvironmentVariables = [
            (ENV_PROJECT_PROPERTIES_PREFIX + "envProp"): "env value"
        ]

        when:
        def properties = loadAndMergePropertiesWith(emptyMap())

        then:
        "env value" == properties["envProp"]
    }

    def mergeAddsPropertiesFromSystemPropertiesWithPrefix() {
        given:
        prefixedSystemProperties = [
            (SYSTEM_PROJECT_PROPERTIES_PREFIX + "systemProp"): "system value"
        ]

        when:
        def properties = loadAndMergePropertiesWith(emptyMap())

        then:
        "system value" == properties["systemProp"]
    }

    def mergeAddsPropertiesFromStartParameter() {
        given:
        projectPropertiesArgs = ["paramProp": "param value"]

        when:
        def properties = loadAndMergePropertiesWith(emptyMap())

        then:
        "param value" == properties["paramProp"]
    }

    def projectPropertiesHavePrecedenceOverInstallationPropertiesFile() {
        given:
        1 * environment.propertiesFile(fromDir(gradleInstallationHomeDir)) >> ["prop": "settings value"]
        def projectProperties = ["prop": "project value"]

        when:
        def properties = loadAndMergePropertiesWith(projectProperties)

        then:
        "project value" == properties["prop"]
    }

    def projectPropertiesHavePrecedenceOverSettingsPropertiesFile() {
        given:
        1 * environment.propertiesFile(fromDir(settingsDir)) >> ["prop": "settings value"]
        def projectProperties = ["prop": "project value"]

        when:
        def properties = loadAndMergePropertiesWith(projectProperties)

        then:
        "project value" == properties["prop"]
    }

    def userPropertiesFileHasPrecedenceOverSettingsPropertiesFile() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> ["prop": "user value"]
        1 * environment.propertiesFile(fromDir(settingsDir)) >> ["prop": "settings value"]

        when:
        def properties = loadAndMergePropertiesWith(emptyMap())

        then:
        "user value" == properties["prop"]
    }

    def userPropertiesFileHasPrecedenceOverProjectProperties() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> ["prop": "user value"]
        1 * environment.propertiesFile(fromDir(settingsDir)) >> ["prop": "settings value"]
        def projectProperties = ["prop": "project value"]

        when:
        def properties = loadAndMergePropertiesWith(projectProperties)

        then:
        "user value" == properties["prop"]
    }

    def environmentVariablesHavePrecedenceOverProjectProperties() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> ["prop": "user value"]
        1 * environment.propertiesFile(fromDir(settingsDir)) >> ["prop": "settings value"]
        prefixedEnvironmentVariables = [(ENV_PROJECT_PROPERTIES_PREFIX + "prop"): "env value"]
        def projectProperties = ["prop": "project value"]

        when:
        def properties = loadAndMergePropertiesWith(projectProperties)

        then:
        "env value" == properties["prop"]
    }

    def systemPropertiesHavePrecedenceOverEnvironmentVariables() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> ["prop": "user value"]
        1 * environment.propertiesFile(fromDir(settingsDir)) >> ["prop": "settings value"]
        prefixedEnvironmentVariables = [(ENV_PROJECT_PROPERTIES_PREFIX + "prop"): "env value"]
        prefixedSystemProperties = [(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop"): "system value"]
        def projectProperties = ["prop": "project value"]

        when:
        def properties = loadAndMergePropertiesWith(projectProperties)

        then:
        "system value" == properties["prop"]
    }

    def startParameterPropertiesHavePrecedenceOverSystemProperties() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> ["prop": "user value"]
        1 * environment.propertiesFile(fromDir(settingsDir)) >> ["prop": "settings value"]
        projectPropertiesArgs = ["prop": "param value"]
        prefixedEnvironmentVariables = [(ENV_PROJECT_PROPERTIES_PREFIX + "prop"): "env value"]
        prefixedSystemProperties = [(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop"): "system value"]
        def projectProperties = ["prop": "project value"]

        when:
        def properties = loadAndMergePropertiesWith(projectProperties)

        then:
        "param value" == properties["prop"]
    }

    def loadSetsSystemProperties() {
        given:
        systemPropertiesArgs = ["systemPropArgKey": "systemPropArgValue"]
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> [
            (SYSTEM_PROP_PREFIX + ".userSystemProp"): "userSystemValue"
        ]
        1 * environment.propertiesFile(fromDir(settingsDir)) >> [
            (SYSTEM_PROP_PREFIX + ".userSystemProp"): "settingsSystemValue",
            (SYSTEM_PROP_PREFIX + ".settingsSystemProp2"): "settingsSystemValue2"
        ]

        when:
        loadProperties()

        then:
        "userSystemValue" == System.getProperty("userSystemProp")
        "settingsSystemValue2" == System.getProperty("settingsSystemProp2")
        "systemPropArgValue" == System.getProperty("systemPropArgKey")
    }

    def loadPropertiesWithNoExceptionForNonexistentGradleInstallationHomeAndUserHomeAndSettingsDir() {
        given:
        tmpDir.getTestDirectory().deleteDir()

        expect:
        loadProperties() != null
    }

    def loadPropertiesWithNoExceptionIfGradleInstallationHomeIsNotKnown() {
        given:
        gradleInstallationHomeDir = null

        expect:
        loadProperties() != null
    }

    def reloadsProperties() {
        given:
        1 * environment.propertiesFile(fromDir(settingsDir)) >> [
            "prop1": "value",
            "prop2": "value"
        ]

        File otherSettingsDir = tmpDir.createDir("otherSettingsDir")
        1 * environment.propertiesFile(fromDir(otherSettingsDir)) >> ["prop1": "otherValue"]

        when:
        def properties = loadAndMergePropertiesWith(emptyMap())

        then:
        "value" == properties["prop1"]
        "value" == properties["prop2"]

        when:
        properties = loadPropertiesFrom(otherSettingsDir).mergeProperties(emptyMap())

        then:
        "otherValue" == properties["prop1"]
        properties["prop2"].is null
    }

    def buildSystemProperties() {
        given:
        System.setProperty("gradle-loader-test", "value")

        expect:
        System.getProperties().containsKey("gradle-loader-test")
        "value" == System.getProperties().get("gradle-loader-test")
    }

    def startParameterSystemPropertiesHavePrecedenceOverPropertiesFiles() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> ["systemProp.prop": "user value"]
        1 * environment.propertiesFile(fromDir(settingsDir)) >> ["systemProp.prop": "settings value"]
        systemPropertiesArgs = ["prop": "commandline value"]
        prefixedSystemProperties = [:]

        when:
        loadProperties()

        then:
        "commandline value" == System.getProperty("prop")
    }

    private Map<String, String> loadAndMergePropertiesWith(Map<String, String> projectProperties) {
        return loadProperties().mergeProperties(projectProperties)
    }

    private GradleProperties loadProperties() {
        return loadPropertiesFrom(settingsDir)
    }

    private GradleProperties loadPropertiesFrom(File settingsDir) {
        return gradlePropertiesLoader.loadProperties(settingsDir)
    }
}
