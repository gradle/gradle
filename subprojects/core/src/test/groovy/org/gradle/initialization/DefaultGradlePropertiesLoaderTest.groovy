/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.internal.Cast
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.GUtil
import org.gradle.util.internal.WrapUtil
import org.junit.Rule
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import static java.util.Collections.emptyMap
import static org.gradle.api.Project.SYSTEM_PROP_PREFIX
import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.initialization.IGradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX

@RestoreSystemProperties
class DefaultGradlePropertiesLoaderTest extends Specification {
    private DefaultGradlePropertiesLoader gradlePropertiesLoader
    private File gradleUserHomeDir
    private File settingsDir
    private File gradleInstallationHomeDir
    private Map<String, String> systemProperties = new HashMap<>()
    private Map<String, String> envProperties = new HashMap<>()
    private StartParameterInternal startParameter = new StartParameterInternal()
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def setup() {
        gradleUserHomeDir = tmpDir.createDir("gradleUserHome")
        settingsDir = tmpDir.createDir("settingsDir")
        gradleInstallationHomeDir = tmpDir.createDir("gradleInstallationHome")
        gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter)
        startParameter.setGradleUserHomeDir(gradleUserHomeDir)
        startParameter.setGradleHomeDir(gradleInstallationHomeDir)
    }

    private void writePropertyFile(File location, Map<String, String> propertiesMap) {
        Properties properties = new Properties()
        properties.putAll(propertiesMap)
        GUtil.saveProperties(properties, new File(location, Project.GRADLE_PROPERTIES))
    }

    def "merge adds properties from installation properties file"() {
        setup:
        writePropertyFile(gradleInstallationHomeDir, Cast.uncheckedNonnullCast(Cast.uncheckedNonnullCast(GUtil.map("settingsProp", "settings value"))))
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        expect:
        "settings value" == properties.get("settingsProp")
    }

    def "merge adds properties from user properties file"() {
        setup:
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("userProp", "user value")))
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        expect:
        "user value" == properties.get("userProp")
    }

    def "merge adds properties from settings properties file"() {
        setup:
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("settingsProp", "settings value")))
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        expect:
        "settings value" == properties.get("settingsProp")
    }

    def "merge adds properties from environment variables with prefix"() {
        setup:
        envProperties = Cast.uncheckedNonnullCast(GUtil.map(
            ENV_PROJECT_PROPERTIES_PREFIX + "envProp", "env value",
            "ignoreMe", "ignored"))
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        expect:
        "env value" == properties.get("envProp")
    }

    def "merge adds properties from system properties with prefix"() {
        setup:
        systemProperties = Cast.uncheckedNonnullCast(GUtil.map(
            SYSTEM_PROJECT_PROPERTIES_PREFIX + "systemProp", "system value",
            "ignoreMe", "ignored"))
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        expect:
        "system value" == properties.get("systemProp")
    }

    def "merge adds properties from start parameter"() {
        setup:
        startParameter.setProjectProperties(Cast.uncheckedNonnullCast(GUtil.map("paramProp", "param value")))
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        expect:
        "param value" == properties.get("paramProp")
    }

    def "project properties have precedence over installation properties file"() {
        setup:
        writePropertyFile(gradleInstallationHomeDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")))
        Map<String, String> projectProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "project value"))
        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties)

        expect:
        "project value" == properties.get("prop")
    }

    def "project properties have precedence over settings properties file"() {
        setup:
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")))
        Map<String, String> projectProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "project value"))
        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties)

        expect:
        "project value" ==  properties.get("prop")
    }

    def "user properties file has precedence over settings properties file"() {
        setup:
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "user value")))
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")))
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        expect:
        "user value" == properties.get("prop")
    }

    def "user properties file has precedence over project properties"() {
        setup:
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "user value")))
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")))
        Map<String, String> projectProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "project value"))
        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties)

        expect:
        "user value" == properties.get("prop")
    }

    def "environment variables have precedence over project properties"() {
        setup:
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "user value")))
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")))
        Map<String, String> projectProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "project value"))
        envProperties = Cast.uncheckedNonnullCast(GUtil.map(ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value"))
        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties)

        expect:
        "env value" == properties.get("prop")
    }

    def "system properties have precedence over environment variables"() {
        setup:
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "user value")))
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")))
        Map<String, String> projectProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "project value"))
        envProperties = Cast.uncheckedNonnullCast(GUtil.map(ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value"))
        systemProperties = Cast.uncheckedNonnullCast(GUtil.map(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop", "system value"))
        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties)

        expect:
        "system value" == properties.get("prop")
    }

    def "start parameter properties have precedence over system properties"() {
        setup:
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "user value")))
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop", "settings value")))
        Map<String, String> projectProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "project value"))
        envProperties = Cast.uncheckedNonnullCast(GUtil.map(ENV_PROJECT_PROPERTIES_PREFIX + "prop", "env value"))
        systemProperties = Cast.uncheckedNonnullCast(GUtil.map(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop", "system value"))
        startParameter.setProjectProperties(Cast.uncheckedNonnullCast(GUtil.map("prop", "param value")))
        Map<String, String> properties = loadAndMergePropertiesWith(projectProperties)

        expect:
        "param value" == properties.get("prop")
    }

    def "load sets system properties"() {
        setup:
        startParameter.setSystemPropertiesArgs(WrapUtil.toMap("systemPropArgKey", "systemPropArgValue"))
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map(SYSTEM_PROP_PREFIX + ".userSystemProp", "userSystemValue")))
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map(
            SYSTEM_PROP_PREFIX + ".userSystemProp", "settingsSystemValue",
            SYSTEM_PROP_PREFIX + ".settingsSystemProp2", "settingsSystemValue2")))
        loadProperties()

        expect:
        "userSystemValue" == System.getProperty("userSystemProp")
        "settingsSystemValue2" == System.getProperty("settingsSystemProp2")
        "systemPropArgValue" == System.getProperty("systemPropArgKey")
    }

    def "load properties with no exception for nonexistent Gradle installation home and user home and settings dir"() {
        expect:
        tmpDir.getTestDirectory().deleteDir()
        loadProperties()
    }

    def "load properties with no exception if Gradle installation home is not known"() {
        setup:
        gradleInstallationHomeDir = null

        expect:
        loadProperties()
    }

    def "reloads properties"() {
        setup:
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop1", "value", "prop2", "value")))
        File otherSettingsDir = tmpDir.createDir("otherSettingsDir")
        writePropertyFile(otherSettingsDir, Cast.uncheckedNonnullCast(GUtil.map("prop1", "otherValue")))
        Map<String, String> properties = loadAndMergePropertiesWith(emptyMap())

        expect:
        "value" == properties.get("prop1")
        "value" == properties.get("prop2")

        when:
        properties = loadPropertiesFrom(otherSettingsDir).mergeProperties(emptyMap())

        then:
        "otherValue" == properties.get("prop1")
        properties.get("prop2") == null
    }

    def "build system properties"() {
        setup:
        System.setProperty("gradle-loader-test", "value")

        expect:
        gradlePropertiesLoader.getAllSystemProperties().containsKey("gradle-loader-test") == true
        "value" == gradlePropertiesLoader.getAllSystemProperties().get("gradle-loader-test")
    }

    def "start parameter system properties have precedence over properties files"() {
        setup:
        writePropertyFile(gradleUserHomeDir, Cast.uncheckedNonnullCast(GUtil.map("systemProp.prop", "user value")))
        writePropertyFile(settingsDir, Cast.uncheckedNonnullCast(GUtil.map("systemProp.prop", "settings value")))
        systemProperties = Cast.uncheckedNonnullCast(GUtil.map("prop", "system value"))
        startParameter.setSystemPropertiesArgs(WrapUtil.toMap("prop", "commandline value"))
        loadProperties()

        expect:
        "commandline value" == System.getProperty("prop")
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
            startParameter,
            systemProperties,
            envProperties
        )
    }
}
