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
import org.gradle.api.internal.project.ProjectIdentity
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
import static org.gradle.initialization.properties.GradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.initialization.properties.GradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX

class DefaultGradlePropertiesControllerTest extends Specification {

    private Map<String, String> prefixedEnvironmentVariables = emptyMap()
    private Map<String, String> prefixedSystemProperties = emptyMap()
    private Map<String, String> projectPropertiesArgs = emptyMap()
    private Map<String, String> systemPropertiesArgs = emptyMap()
    private File gradleUserHome = new File("gradleUserHome")
    private final rootBuildId = DefaultBuildIdentifier.ROOT
    private final rootProjectId = new ProjectIdentity(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.ROOT, "root")

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

    private final IGradlePropertiesLoader gradlePropertiesLoader = Mock(IGradlePropertiesLoader)
    private final ProjectPropertiesLoader projectPropertiesLoader = Mock(ProjectPropertiesLoader)
    private final SystemPropertiesInstaller systemPropertiesInstaller = Mock(SystemPropertiesInstaller)

    @Rule
    public SetSystemProperties sysProp = new SetSystemProperties()

    def "attached GradleProperties #method fails before loading build properties"() {
        given:
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)
        def properties = controller.getGradleProperties(rootBuildId)
        0 * controller.loadGradleProperties(_, _, _)

        when:
        properties.find("anything")
        then:
        thrown(IllegalStateException)

        when:
        properties.getProperties()
        then:
        thrown(IllegalStateException)
    }

    def "attached GradleProperties #method fails before loading project properties"() {
        given:
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)
        def properties = controller.getGradleProperties(rootProjectId)
        0 * controller.loadGradleProperties(_, _)

        when:
        properties.find("anything")
        then:
        thrown(IllegalStateException)

        when:
        properties.getProperties()
        then:
        thrown(IllegalStateException)
    }

    def "attached GradleProperties methods succeed after loading"() {
        given:
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)
        def settingsDir = new File('.')
        def properties = controller.getGradleProperties(rootBuildId)
        1 * gradlePropertiesLoader.loadGradleProperties(settingsDir) >> new DefaultGradleProperties([property: '42'], [:])
        1 * projectPropertiesLoader.loadProjectProperties() >> [:]

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, false)

        then:
        properties.find("property") == '42'
        properties.getProperties() == [property: '42']
    }

    def 'loadGradleProperties is idempotent'() {
        given:
        // use a different File instance for each call to ensure it is compared by value
        def currentDir = { new File('.') }
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)
        def buildProperties = Mock(MutableGradleProperties)

        when: "calling the method multiple times with the same value"
        controller.loadGradleProperties(rootBuildId, currentDir(), false)
        controller.loadGradleProperties(rootBuildId, currentDir(), false)

        then:
        1 * gradlePropertiesLoader.loadGradleProperties(currentDir()) >> buildProperties
        1 * projectPropertiesLoader.loadProjectProperties() >> [:]
        _ * buildProperties.updateOverrideProperties(_)
    }

    def 'loadGradleProperties fails when called with different argument'() {
        given:
        def settingsDir = new File('a')
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)
        1 * gradlePropertiesLoader.loadGradleProperties(settingsDir) >> new DefaultGradleProperties([property: '42'], [:])
        1 * projectPropertiesLoader.loadProjectProperties() >> [:]

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, true)
        controller.loadGradleProperties(rootBuildId, new File('b'), true)

        then:
        thrown(IllegalStateException)
    }

    def "environment variables have precedence over project properties"() {
        given:
        def settingsDir = new File("settingsDir")
        def projectDir = new File("projectDir")

        prefixedEnvironmentVariables = [(ENV_PROJECT_PROPERTIES_PREFIX + "prop"): "env value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        1 * environment.propertiesFile(propertiesFileFromDir(gradleUserHome)) >> ["prop": "user value"]
        1 * environment.propertiesFile(propertiesFileFromDir(settingsDir)) >> ["prop": "settings value"]
        1 * environment.propertiesFile(propertiesFileFromDir(projectDir)) >> ["prop": "project value"]

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, false)
        def buildScopedProperties = controller.getGradleProperties(rootBuildId)
        then:
        buildScopedProperties.find("prop") == "env value"

        when:
        controller.loadGradleProperties(rootProjectId, projectDir)
        def projectScopedProperties = controller.getGradleProperties(rootProjectId)
        then:
        projectScopedProperties.find("prop") == "env value"
    }

    def "system properties have precedence over environment variables"() {
        given:
        def settingsDir = new File("settingsDir")
        def projectDir = new File("projectDir")

        prefixedEnvironmentVariables = [(ENV_PROJECT_PROPERTIES_PREFIX + "prop"): "env value"]
        prefixedSystemProperties = [(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop"): "system value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        1 * environment.propertiesFile(propertiesFileFromDir(gradleUserHome)) >> ["prop": "user value"]
        1 * environment.propertiesFile(propertiesFileFromDir(settingsDir)) >> ["prop": "settings value"]
        1 * environment.propertiesFile(propertiesFileFromDir(projectDir)) >> ["prop": "project value"]

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, false)
        def buildScopedProperties = controller.getGradleProperties(rootBuildId)
        then:
        buildScopedProperties.find("prop") == "system value"

        when:
        controller.loadGradleProperties(rootProjectId, projectDir)
        def projectScopedProperties = controller.getGradleProperties(rootProjectId)
        then:
        projectScopedProperties.find("prop") == "system value"
    }

    def "start parameter properties have precedence over system properties"() {
        given:
        def settingsDir = new File("settingsDir")
        def projectDir = new File("projectDir")

        prefixedEnvironmentVariables = [(ENV_PROJECT_PROPERTIES_PREFIX + "prop"): "env value"]
        prefixedSystemProperties = [(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop"): "system value"]
        projectPropertiesArgs = ["prop": "param value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, false)
        def buildScopedProperties = controller.getGradleProperties(rootBuildId)
        then:
        buildScopedProperties.find("prop") == "param value"

        when:
        controller.loadGradleProperties(rootProjectId, projectDir)
        def projectScopedProperties = controller.getGradleProperties(rootProjectId)
        then:
        projectScopedProperties.find("prop") == "param value"
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
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, setSystemProperties)

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
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, true)

        then:
        "commandline value" == System.getProperty("prop")
    }

    def "each build loads own gradle properties"() {
        given:
        def rootDir = new File("rootDir")
        def includedDir = new File(rootDir, "included")

        _ * environment.propertiesFile(propertiesFileFromDir(gradleUserHome)) >> [:]
        _ * environment.propertiesFile(propertiesFileFromDir(rootDir)) >> ["prop": "root value"]
        _ * environment.propertiesFile(propertiesFileFromDir(includedDir)) >> ["prop": "included value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, Mock(SystemPropertiesInstaller), projectPropertiesLoader)

        def rootBuildId = this.rootBuildId
        def includedBuildId = new DefaultBuildIdentifier(Path.path(":included"))

        def rootProperties = controller.getGradleProperties(rootBuildId)
        def includedProperties = controller.getGradleProperties(includedBuildId)

        when:
        controller.loadGradleProperties(rootBuildId, rootDir, false)
        controller.loadGradleProperties(includedBuildId, includedDir, false)

        then:
        rootProperties.find("prop") == "root value"
        includedProperties.find("prop") == "included value"

        and: "properties are separate instances"
        !rootProperties.is(includedProperties)
    }

    def "supports unloading build-scoped properties"() {
        given:
        def settingsDir = new File("settingsDir")
        def gradleUserHomePropertiesFile = propertiesFileFromDir(gradleUserHome)
        def settingsPropertiesFile = propertiesFileFromDir(settingsDir)

        1 * environment.propertiesFile(gradleUserHomePropertiesFile) >> ["prop": "user value"]
        1 * environment.propertiesFile(settingsPropertiesFile) >> ["prop": "settings value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, Mock(SystemPropertiesInstaller), projectPropertiesLoader)

        def properties = controller.getGradleProperties(rootBuildId)

        when: "properties are loaded"
        controller.loadGradleProperties(rootBuildId, settingsDir, false)

        then: "properties can be accessed"
        properties.find("prop") == "user value"

        when: "properties are unloaded"
        controller.unloadGradleProperties(rootBuildId)

        and: "accessing properties fails"
        properties.find("prop")

        then:
        thrown(IllegalStateException)
    }

    def "project-scoped properties inherit from build-scoped properties"() {
        given:
        def projectDir = new File("project")
        def buildDir = new File("build")
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        1 * gradlePropertiesLoader.loadGradleProperties(buildDir) >> new DefaultGradleProperties([a: "build", b: "build"], [:])
        1 * projectPropertiesLoader.loadProjectProperties() >> [:]

        1 * environment.propertiesFile(propertiesFileFromDir(projectDir)) >> [b: 'proj']

        when:
        controller.loadGradleProperties(rootBuildId, buildDir, false)
        controller.loadGradleProperties(rootProjectId, projectDir)
        def properties = controller.getGradleProperties(rootProjectId)

        then:
        properties.find("a") == "build"
        properties.find("b") == "proj"
        properties.getProperties() == [a: "build", b: "proj"]
    }

    private static File propertiesFileFromDir(File dir) {
        new File(dir, Project.GRADLE_PROPERTIES)
    }
}
