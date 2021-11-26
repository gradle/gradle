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
import org.gradle.util.SetSystemProperties
import org.gradle.util.internal.WrapUtil
import org.junit.Rule
import spock.lang.Specification

import static java.util.Collections.emptyMap
import static org.gradle.api.Project.SYSTEM_PROP_PREFIX
import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.initialization.IGradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX
import static org.gradle.internal.Cast.uncheckedNonnullCast
import static org.gradle.util.internal.GUtil.map
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

class DefaultGradlePropertiesLoaderTest extends Specification {
    private DefaultGradlePropertiesLoader gradlePropertiesLoader
    private File gradleUserHomeDir
    private File settingsDir
    private File gradleInstallationHomeDir
    private Map<String, String> systemProperties = new HashMap<>()
    private Map<String, String> envProperties = new HashMap<>()
    private final StartParameterInternal startParameter = new StartParameterInternal()
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    @Rule
    public SetSystemProperties sysProp = new SetSystemProperties()

    final Environment environment = Mock(Environment)

    def setup() {
        gradleUserHomeDir = tmpDir.createDir("gradleUserHome")
        settingsDir = tmpDir.createDir("settingsDir")
        gradleInstallationHomeDir = tmpDir.createDir("gradleInstallationHome")
        gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        startParameter.setGradleUserHomeDir(gradleUserHomeDir)
        startParameter.setGradleHomeDir(gradleInstallationHomeDir)
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
        assertEquals("settings value", properties["settingsProp"])
    }

    def mergeAddsPropertiesFromUserPropertiesFile() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> [
            "userProp": "user value"
        ]

        when:
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        then:
        assertEquals("user value", properties.get("userProp"))
    }

    def mergeAddsPropertiesFromSettingsPropertiesFile() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> [
            "settingsProp": "settings value"
        ]

        when:
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        then:
        assertEquals("settings value", properties.get("settingsProp"))
    }

    def mergeAddsPropertiesFromEnvironmentVariablesWithPrefix() {
        given:
        envProperties = uncheckedNonnullCast(
            map(ENV_PROJECT_PROPERTIES_PREFIX + "envProp", "env value", "ignoreMe", "ignored")
        )

        when:
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        then:
        assertEquals("env value", properties.get("envProp"))
    }

    def mergeAddsPropertiesFromSystemPropertiesWithPrefix() {
        given:
        systemProperties = uncheckedNonnullCast(
            map(SYSTEM_PROJECT_PROPERTIES_PREFIX + "systemProp", "system value", "ignoreMe", "ignored")
        )

        when:
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        then:
        assertEquals("system value", properties.get("systemProp"))
    }

    def mergeAddsPropertiesFromStartParameter() {
        given:
        startParameter.setProjectProperties(uncheckedNonnullCast(map("paramProp", "param value")))

        when:
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        then:
        assertEquals("param value", properties.get("paramProp"))
    }

    def projectPropertiesHavePrecedenceOverInstallationPropertiesFile() {
        given:
        1 * environment.propertiesFile(fromDir(gradleInstallationHomeDir)) >> ["prop": "settings value"]
        Map<String, String> projectProperties = ["prop": "project value"]

        when:
        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties)

        then:
        "project value" == properties["prop"]
    }

    def projectPropertiesHavePrecedenceOverSettingsPropertiesFile() {
        given:
        1 * environment.propertiesFile(fromDir(settingsDir)) >> ["prop": "settings value"]
        Map<String, String> projectProperties = uncheckedNonnullCast(map("prop", "project value"))

        when:
        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties)

        then:
        assertEquals("project value", properties.get("prop"))
    }

    def userPropertiesFileHasPrecedenceOverSettingsPropertiesFile() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> ["prop": "user value"]
        1 * environment.propertiesFile(fromDir(settingsDir)) >> ["prop": "settings value"]

        when:
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        then:
        "user value" == properties["prop"]
    }

    def userPropertiesFileHasPrecedenceOverProjectProperties() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> ["prop": "user value"]
        1 * environment.propertiesFile(fromDir(settingsDir)) >> ["prop": "settings value"]
        Map<String, String> projectProperties = uncheckedNonnullCast(map("prop", "project value"))

        when:
        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties)

        then:
        assertEquals("user value", properties.get("prop"))
    }

    def environmentVariablesHavePrecedenceOverProjectProperties() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> map("prop", "user value")
        1 * environment.propertiesFile(fromDir(settingsDir)) >> map("prop", "settings value")
        Map<String, String> projectProperties = uncheckedNonnullCast(map("prop", "project value"))
        envProperties = uncheckedNonnullCast(map(ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value"))

        when:
        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties)

        then:
        assertEquals("env value", properties.get("prop"))
    }

    def systemPropertiesHavePrecedenceOverEnvironmentVariables() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> map("prop", "user value")
        1 * environment.propertiesFile(fromDir(settingsDir)) >> map("prop", "settings value")
        Map<String, String> projectProperties = uncheckedNonnullCast(map("prop", "project value"))
        envProperties = uncheckedNonnullCast(map(ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value"))
        systemProperties = uncheckedNonnullCast(map(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop", "system value"))

        when:
        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties)

        then:
        assertEquals("system value", properties.get("prop"))
    }

    def startParameterPropertiesHavePrecedenceOverSystemProperties() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> map("prop", "user value")
        1 * environment.propertiesFile(fromDir(settingsDir)) >> map("prop", "settings value")
        Map<String, String> projectProperties = uncheckedNonnullCast(map("prop", "project value"))
        envProperties = uncheckedNonnullCast(map(ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value"))
        systemProperties = uncheckedNonnullCast(map(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop", "system value"))
        startParameter.setProjectProperties(uncheckedNonnullCast(map("prop", "param value")))

        when:
        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties)

        then:
        assertEquals("param value", properties.get("prop"))
    }

    def loadSetsSystemProperties() {
        given:
        startParameter.setSystemPropertiesArgs(WrapUtil.toMap("systemPropArgKey", "systemPropArgValue"))
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> map(SYSTEM_PROP_PREFIX + ".userSystemProp", "userSystemValue")
        1 * environment.propertiesFile(fromDir(settingsDir)) >> map(
            SYSTEM_PROP_PREFIX + ".userSystemProp", "settingsSystemValue",
            SYSTEM_PROP_PREFIX + ".settingsSystemProp2", "settingsSystemValue2"
        )

        when:
        loadProperties()

        then:
        assertEquals("userSystemValue", System.getProperty("userSystemProp"))
        assertEquals("settingsSystemValue2", System.getProperty("settingsSystemProp2"))
        assertEquals("systemPropArgValue", System.getProperty("systemPropArgKey"))
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
        1 * environment.propertiesFile(fromDir(settingsDir)) >> map("prop1", "value", "prop2", "value")

        File otherSettingsDir = tmpDir.createDir("otherSettingsDir")
        1 * environment.propertiesFile(fromDir(otherSettingsDir)) >> map("prop1", "otherValue")

        when:
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        then:
        assertEquals("value", properties.get("prop1"))
        assertEquals("value", properties.get("prop2"))

        when:
        properties = loadPropertiesFrom(otherSettingsDir).mergeProperties(emptyMap())

        then:
        assertEquals("otherValue", properties.get("prop1"))
        assertNull(properties.get("prop2"))
    }

    def buildSystemProperties() {
        given:
        System.setProperty("gradle-loader-test", "value")

        expect:
        assertTrue(System.getProperties().containsKey("gradle-loader-test"))
        assertEquals("value", System.getProperties().get("gradle-loader-test"))
    }

    def startParameterSystemPropertiesHavePrecedenceOverPropertiesFiles() {
        given:
        1 * environment.propertiesFile(fromDir(gradleUserHomeDir)) >> map("systemProp.prop", "user value")
        1 * environment.propertiesFile(fromDir(settingsDir)) >> map("systemProp.prop", "settings value")
        systemProperties = uncheckedNonnullCast(map("prop", "system value"))
        startParameter.setSystemPropertiesArgs(WrapUtil.toMap("prop", "commandline value"))

        when:
        loadProperties()

        then:
        assertEquals("commandline value", System.getProperty("prop"))
    }

    private Map<String, String> loadAndMergePropertiesWith(Map<String, String> properties) {
        return loadProperties().mergeProperties(properties)
    }

    private GradleProperties loadProperties() {
        return loadPropertiesFrom(settingsDir)
    }

    private GradleProperties loadPropertiesFrom(File settingsDir) {
        return gradlePropertiesLoader.loadProperties(
            settingsDir,
            systemProperties,
            envProperties
        )
    }
}
