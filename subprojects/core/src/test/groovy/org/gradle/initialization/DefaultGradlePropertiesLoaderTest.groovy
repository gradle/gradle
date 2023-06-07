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
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static java.util.Collections.emptyMap

class DefaultGradlePropertiesLoaderTest extends Specification {

    private final Environment environment = Mock(Environment)
    private final StartParameterInternal startParameter = Mock(StartParameterInternal)
    private final DefaultGradlePropertiesLoader gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)

    private File gradleUserHomeDir
    private File settingsDir
    private File gradleInstallationHomeDir

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def setup() {
        gradleUserHomeDir = tmpDir.createDir("gradleUserHome")
        settingsDir = tmpDir.createDir("settingsDir")
        gradleInstallationHomeDir = tmpDir.createDir("gradleInstallationHome")
        _ * startParameter.gradleUserHomeDir >> gradleUserHomeDir
        _ * startParameter.gradleHomeDir >> gradleInstallationHomeDir
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
        properties = loadPropertiesFrom(otherSettingsDir).getProperties()

        then:
        "otherValue" == properties["prop1"]
        properties["prop2"].is null
    }

    private Map<String, String> loadAndMergePropertiesWith(Map<String, String> projectProperties) {
        return loadProperties().mergeProperties(projectProperties)
    }

    private GradleProperties loadProperties() {
        return loadPropertiesFrom(settingsDir)
    }

    private GradleProperties loadPropertiesFrom(File settingsDir) {
        return gradlePropertiesLoader.loadGradleProperties(settingsDir)
    }
}
