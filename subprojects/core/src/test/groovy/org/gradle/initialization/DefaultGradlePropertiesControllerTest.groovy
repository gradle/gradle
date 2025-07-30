/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.initialization.properties.DefaultProjectPropertiesLoader
import org.gradle.initialization.properties.DefaultSystemPropertiesInstaller
import org.gradle.initialization.properties.MutableGradleProperties
import org.gradle.initialization.properties.ProjectPropertiesLoader
import org.gradle.initialization.properties.SystemPropertiesInstaller
import org.gradle.util.Path
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import static java.util.Collections.emptyMap
import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.initialization.IGradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX

class DefaultGradlePropertiesControllerTest extends Specification {

    private Map<String, String> prefixedEnvironmentVariables = emptyMap()
    private Map<String, String> prefixedSystemProperties = emptyMap()
    private Map<String, String> projectPropertiesArgs = emptyMap()
    private Map<String, String> systemPropertiesArgs = emptyMap()
    private File gradleUserHome = new File("gradleUserHome")
    private final rootBuildId = DefaultBuildIdentifier.ROOT

    private final Environment environment = Mock(Environment) {
        getSystemProperties() >> Mock(Environment.Properties) {
            byNamePrefix(SYSTEM_PROJECT_PROPERTIES_PREFIX) >> { prefixedSystemProperties }
        }
        getVariables() >> Mock(Environment.Properties) {
            byNamePrefix(ENV_PROJECT_PROPERTIES_PREFIX) >> { prefixedEnvironmentVariables }
        }
    }

    private final StartParameterInternal startParameter = Mock(StartParameterInternal) {
        getProjectProperties() >> { projectPropertiesArgs }
        getSystemPropertiesArgs() >> { systemPropertiesArgs }
        getGradleUserHomeDir() >> gradleUserHome
    }

    @Rule
    public SetSystemProperties sysProp = new SetSystemProperties()

    def "attached GradleProperties #method fails before loading"() {

        given:
        def controller = new DefaultGradlePropertiesController(Mock(IGradlePropertiesLoader), Mock(SystemPropertiesInstaller), Mock(ProjectPropertiesLoader))
        def properties = controller.getGradleProperties(rootBuildId)
        0 * controller.loadGradlePropertiesFrom(_, _, _)

        when:
        switch (method) {
            case "find": properties.find("anything"); break
            case "mergeProperties": properties.mergeProperties([:]); break
            default: assert false
        }

        then:
        thrown(IllegalStateException)

        where:
        method << ["find", "mergeProperties"]
    }

    def "attached GradleProperties methods succeed after loading"() {

        given:
        def loader = Mock(IGradlePropertiesLoader)
        def controller = new DefaultGradlePropertiesController(loader, Mock(SystemPropertiesInstaller), Mock(ProjectPropertiesLoader))
        def settingsDir = new File('.')
        def properties = controller.getGradleProperties(rootBuildId)
        def loadedProperties = Mock(MutableGradleProperties)
        1 * loader.loadGradleProperties(settingsDir) >> loadedProperties
        1 * loadedProperties.mergeProperties(_) >> [property: '42']
        1 * loadedProperties.find(_) >> '42'

        when:
        controller.loadGradlePropertiesFrom(rootBuildId, settingsDir, true)

        then:
        properties.find("property") == '42'
        properties.mergeProperties([:]) == [property: '42']
    }

    def "loadGradlePropertiesFrom is idempotent"() {

        given:
        // use a different File instance for each call to ensure it is compared by value
        def currentDir = { new File('.') }
        def loader = Mock(IGradlePropertiesLoader)
        def controller = new DefaultGradlePropertiesController(loader, Mock(SystemPropertiesInstaller), Mock(ProjectPropertiesLoader))
        def loadedProperties = Mock(MutableGradleProperties)

        when: "calling the method multiple times with the same value"
        controller.loadGradlePropertiesFrom(rootBuildId, currentDir(), true)
        controller.loadGradlePropertiesFrom(rootBuildId, currentDir(), true)

        then:
        1 * loader.loadGradleProperties(currentDir()) >> loadedProperties
    }

    def "loadGradlePropertiesFrom fails when called with different argument"() {

        given:
        def settingsDir = new File('a')
        def loader = Mock(IGradlePropertiesLoader)
        def controller = new DefaultGradlePropertiesController(loader, Mock(SystemPropertiesInstaller), Mock(ProjectPropertiesLoader))
        def loadedProperties = Mock(MutableGradleProperties)
        1 * loader.loadGradleProperties(settingsDir) >> loadedProperties

        when:
        controller.loadGradlePropertiesFrom(rootBuildId, settingsDir, true)
        controller.loadGradlePropertiesFrom(rootBuildId, new File('b'), true)

        then:
        thrown(IllegalStateException)
    }

    def "environment variables have precedence over project properties"() {
        given:
        def settingsDir = new File("settingsDir")
        def gradleUserHomePropertiesFile = propertiesFileFromDir(gradleUserHome)
        def settingsPropertiesFile = propertiesFileFromDir(settingsDir)

        1 * environment.propertiesFile(gradleUserHomePropertiesFile) >> ["prop": "user value"]
        1 * environment.propertiesFile(settingsPropertiesFile) >> ["prop": "settings value"]
        prefixedEnvironmentVariables = [(ENV_PROJECT_PROPERTIES_PREFIX + "prop"): "env value"]

        def projectProperties = ["prop": "project value"]
        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(gradlePropertiesLoader, Mock(SystemPropertiesInstaller), projectPropertiesLoader)
        def properties = controller.getGradleProperties(rootBuildId)

        when:
        controller.loadGradlePropertiesFrom(rootBuildId, settingsDir, true)

        then:
        "env value" == properties.mergeProperties(projectProperties)["prop"]
    }

    def "system properties have precedence over environment variables"() {
        given:
        def settingsDir = new File("settingsDir")
        def gradleUserHomePropertiesFile = propertiesFileFromDir(gradleUserHome)
        def settingsPropertiesFile = propertiesFileFromDir(settingsDir)

        1 * environment.propertiesFile(gradleUserHomePropertiesFile) >> ["prop": "user value"]
        1 * environment.propertiesFile(settingsPropertiesFile) >> ["prop": "settings value"]
        prefixedEnvironmentVariables = [(ENV_PROJECT_PROPERTIES_PREFIX + "prop"): "env value"]
        prefixedSystemProperties = [(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop"): "system value"]

        def projectProperties = ["prop": "project value"]
        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(gradlePropertiesLoader, Mock(SystemPropertiesInstaller), projectPropertiesLoader)
        def properties = controller.getGradleProperties(rootBuildId)

        when:
        controller.loadGradlePropertiesFrom(rootBuildId, settingsDir, true)

        then:
        "system value" == properties.mergeProperties(projectProperties)["prop"]
    }

    def "start parameter properties have precedence over system properties"() {
        given:
        def settingsDir = new File("settingsDir")
        def gradleUserHomePropertiesFile = propertiesFileFromDir(gradleUserHome)
        def settingsPropertiesFile = propertiesFileFromDir(settingsDir)

        1 * environment.propertiesFile(gradleUserHomePropertiesFile) >> ["prop": "user value"]
        1 * environment.propertiesFile(settingsPropertiesFile) >> ["prop": "settings value"]
        prefixedEnvironmentVariables = [(ENV_PROJECT_PROPERTIES_PREFIX + "prop"): "env value"]
        prefixedSystemProperties = [(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop"): "system value"]
        projectPropertiesArgs = ["prop": "param value"]

        def projectProperties = ["prop": "project value"]
        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(gradlePropertiesLoader, Mock(SystemPropertiesInstaller), projectPropertiesLoader)
        def properties = controller.getGradleProperties(rootBuildId)

        when:
        controller.loadGradlePropertiesFrom(rootBuildId, settingsDir, true)

        then:
        "param value" == properties.mergeProperties(projectProperties)["prop"]
    }

    def "load sets system properties"() {
        given:
        def settingsDir = new File("settingsDir")
        def gradleUserHomePropertiesFile = propertiesFileFromDir(gradleUserHome)
        def settingsPropertiesFile = propertiesFileFromDir(settingsDir)

        1 * environment.propertiesFile(gradleUserHomePropertiesFile) >> [
            (Project.SYSTEM_PROP_PREFIX + ".userSystemProp"): "userSystemValue"
        ]
        1 * environment.propertiesFile(settingsPropertiesFile) >> [
            (Project.SYSTEM_PROP_PREFIX + ".userSystemProp"): "settingsSystemValue",
            (Project.SYSTEM_PROP_PREFIX + ".settingsSystemProp2"): "settingsSystemValue2"
        ]
        systemPropertiesArgs = ["systemPropArgKey": "systemPropArgValue"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def systemPropertiesInstaller = new DefaultSystemPropertiesInstaller(Mock(EnvironmentChangeTracker), startParameter)
        def controller = new DefaultGradlePropertiesController(gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        when:
        controller.loadGradlePropertiesFrom(rootBuildId, settingsDir, setSystemProperties)

        then:
        (setSystemProperties ? "userSystemValue" : null) == System.getProperty("userSystemProp")
        (setSystemProperties ? "settingsSystemValue2" : null) == System.getProperty("settingsSystemProp2")
        (setSystemProperties ? "systemPropArgValue" : null) == System.getProperty("systemPropArgKey")

        where:
        setSystemProperties << [true, false]
    }

    def "start parameter system properties have precedence over properties files"() {
        given:
        def settingsDir = new File("settingsDir")
        def gradleUserHomePropertiesFile = propertiesFileFromDir(gradleUserHome)
        def settingsPropertiesFile = propertiesFileFromDir(settingsDir)

        1 * environment.propertiesFile(gradleUserHomePropertiesFile) >> ["prop": "user value"]
        1 * environment.propertiesFile(settingsPropertiesFile) >> ["prop": "settings value"]
        prefixedSystemProperties = [:]
        systemPropertiesArgs = ["prop": "commandline value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def systemPropertiesInstaller = new DefaultSystemPropertiesInstaller(Mock(EnvironmentChangeTracker), startParameter)
        def controller = new DefaultGradlePropertiesController(gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        when:
        controller.loadGradlePropertiesFrom(rootBuildId, settingsDir, true)

        then:
        "commandline value" == System.getProperty("prop")
    }

    def "different build identifiers maintain separate gradle properties"() {
        given:
        def settingsDir = new File("settingsDir")
        def gradleUserHomePropertiesFile = propertiesFileFromDir(gradleUserHome)
        def settingsPropertiesFile = propertiesFileFromDir(settingsDir)

        2 * environment.propertiesFile(gradleUserHomePropertiesFile) >> ["prop": "user value"]
        2 * environment.propertiesFile(settingsPropertiesFile) >> ["prop": "settings value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(gradlePropertiesLoader, Mock(SystemPropertiesInstaller), projectPropertiesLoader)

        def rootBuildId = this.rootBuildId
        def includedBuildId = new DefaultBuildIdentifier(Path.path(":included"))

        def rootProperties = controller.getGradleProperties(rootBuildId)
        def includedProperties = controller.getGradleProperties(includedBuildId)

        when:
        controller.loadGradlePropertiesFrom(rootBuildId, settingsDir, false)
        controller.loadGradlePropertiesFrom(includedBuildId, settingsDir, false)

        then:
        rootProperties.find("prop") == "user value"
        includedProperties.find("prop") == "user value"

        and: "properties are separate instances"
        !rootProperties.is(includedProperties)
    }

    def "unloading gradle properties for specific build id"() {
        given:
        def settingsDir = new File("settingsDir")
        def gradleUserHomePropertiesFile = propertiesFileFromDir(gradleUserHome)
        def settingsPropertiesFile = propertiesFileFromDir(settingsDir)

        1 * environment.propertiesFile(gradleUserHomePropertiesFile) >> ["prop": "user value"]
        1 * environment.propertiesFile(settingsPropertiesFile) >> ["prop": "settings value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(gradlePropertiesLoader, Mock(SystemPropertiesInstaller), projectPropertiesLoader)

        def buildId = rootBuildId
        def properties = controller.getGradleProperties(buildId)

        when: "properties are loaded"
        controller.loadGradlePropertiesFrom(buildId, settingsDir, false)

        then: "properties can be accessed"
        properties.find("prop") == "user value"

        when: "properties are unloaded"
        controller.unloadGradleProperties(buildId)

        and: "accessing properties fails"
        properties.find("prop")

        then:
        thrown(IllegalStateException)
    }

    private static File propertiesFileFromDir(File dir) {
        new File(dir, Project.GRADLE_PROPERTIES)
    }
}
