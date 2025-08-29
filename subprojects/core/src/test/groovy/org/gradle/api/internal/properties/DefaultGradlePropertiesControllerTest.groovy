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

package org.gradle.api.internal.properties

import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.initialization.properties.GradlePropertiesLoader
import org.gradle.initialization.properties.SystemPropertiesInstaller
import org.gradle.util.Path
import spock.lang.Specification

class DefaultGradlePropertiesControllerTest extends Specification {

    private final rootBuildId = DefaultBuildIdentifier.ROOT
    private final rootProjectId = ProjectIdentity.forRootProject(Path.ROOT, "root")
    private final buildRootDir = new File("buildRootDir")

    private final GradlePropertiesLoader gradlePropertiesLoader = Mock(GradlePropertiesLoader)
    private final SystemPropertiesInstaller systemPropertiesInstaller = Mock(SystemPropertiesInstaller)
    private final GradlePropertiesListener listener = Mock(GradlePropertiesListener)

    def "attached GradleProperties #method fails before loading build properties"() {
        given:
        def controller = newDefaultGradlePropertiesController()
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
        def controller = newDefaultGradlePropertiesController()
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
        def controller = newDefaultGradlePropertiesController()
        def properties = controller.getGradleProperties(rootBuildId)
        1 * gradlePropertiesLoader.loadFromGradleHome() >> [:]
        1 * gradlePropertiesLoader.loadFrom(buildRootDir) >> [:]
        1 * gradlePropertiesLoader.loadFromGradleUserHome() >> [:]
        1 * gradlePropertiesLoader.loadFromEnvironmentVariables() >> [:]
        1 * gradlePropertiesLoader.loadFromSystemProperties() >> [:]
        1 * gradlePropertiesLoader.loadFromStartParameterProjectProperties() >> [property: '42']

        when:
        controller.loadGradleProperties(rootBuildId, buildRootDir, false)

        then:
        properties.find("property") == '42'
        properties.getProperties() == [property: '42']
    }

    def "loading build-scoped properties from the same location is idempotent"() {
        given:
        // use a different File instance for each call to ensure it is compared by value
        def currentDir = { new File('.') }
        def controller = newDefaultGradlePropertiesController()

        when: "calling the method multiple times with the same value"
        controller.loadGradleProperties(rootBuildId, currentDir(), false)
        controller.loadGradleProperties(rootBuildId, currentDir(), false)

        then:
        1 * gradlePropertiesLoader.loadFromGradleHome() >> [:]
        1 * gradlePropertiesLoader.loadFrom(currentDir()) >> [:]
        1 * gradlePropertiesLoader.loadFromGradleUserHome() >> [:]
        1 * gradlePropertiesLoader.loadFromEnvironmentVariables() >> [:]
        1 * gradlePropertiesLoader.loadFromSystemProperties() >> [:]
        1 * gradlePropertiesLoader.loadFromStartParameterProjectProperties() >> [:]
    }

    def "loading build-scoped properties second time from another location fails"() {
        given:
        def settingsDir = new File('a')
        def controller = newDefaultGradlePropertiesController()
        1 * gradlePropertiesLoader.loadFromGradleHome() >> [:]
        1 * gradlePropertiesLoader.loadFrom(settingsDir) >> [:]
        1 * gradlePropertiesLoader.loadFromGradleUserHome() >> [:]
        1 * gradlePropertiesLoader.loadFromEnvironmentVariables() >> [:]
        1 * gradlePropertiesLoader.loadFromSystemProperties() >> [:]
        1 * gradlePropertiesLoader.loadFromStartParameterProjectProperties() >> [:]

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, false)
        controller.loadGradleProperties(rootBuildId, new File('b'), false)

        then:
        thrown(IllegalStateException)
    }

    def "gradle properties are composed from multiple sources"() {
        given:
        def projectDir = new File("projectDir")
        def controller = newDefaultGradlePropertiesController()

        1 * gradlePropertiesLoader.loadFromGradleHome() >> ["gradleHomeProp": "gradleHomeValue", "commonProp": "gradleHomeValue"]
        1 * gradlePropertiesLoader.loadFrom(buildRootDir) >> ["buildRootProp": "buildRootValue", "commonProp": "buildRootValue"]
        1 * gradlePropertiesLoader.loadFromGradleUserHome() >> ["userHomeProp": "userHomeValue", "commonProp": "userHomeValue"]
        1 * gradlePropertiesLoader.loadFromEnvironmentVariables() >> ["envProp": "envValue", "commonProp": "envValue"]
        1 * gradlePropertiesLoader.loadFromSystemProperties() >> ["sysProp": "sysValue", "commonProp": "sysValue"]
        1 * gradlePropertiesLoader.loadFromStartParameterProjectProperties() >> ["paramProp": "paramValue", "commonProp": "paramValue"]

        when:
        controller.loadGradleProperties(rootBuildId, buildRootDir, false)
        def buildScopedProperties = controller.getGradleProperties(rootBuildId)
        then:
        buildScopedProperties.properties == [
            gradleHomeProp: "gradleHomeValue",
            buildRootProp: "buildRootValue",
            userHomeProp: "userHomeValue",
            envProp: "envValue",
            sysProp: "sysValue",
            paramProp: "paramValue",

            commonProp: "paramValue" // start-parameter project-properties have the highest precedence
        ]

        when:
        1 * gradlePropertiesLoader.loadFrom(projectDir) >> ["projectProp": "projectValue", "commonProp": "projectValue"]
        controller.loadGradleProperties(rootProjectId, projectDir)
        def projectScopedProperties = controller.getGradleProperties(rootProjectId)
        then:
        projectScopedProperties.properties == [
            gradleHomeProp: "gradleHomeValue",
            buildRootProp: "buildRootValue",
            userHomeProp: "userHomeValue",
            envProp: "envValue",
            sysProp: "sysValue",
            paramProp: "paramValue",

            projectProp: "projectValue",
            commonProp: "paramValue" // start-parameter project-properties have the highest precedence
        ]
    }

    def "build-scoped properties from #observedSource take precedence over #shadowedSource properties"() {
        given:
        def controller = newDefaultGradlePropertiesController()

        def propOrEmpty = { value -> value ? [prop: value] : [:] }

        1 * gradlePropertiesLoader.loadFromGradleHome() >> propOrEmpty(gradleHome)
        1 * gradlePropertiesLoader.loadFrom(buildRootDir) >> propOrEmpty(buildRoot)
        1 * gradlePropertiesLoader.loadFromGradleUserHome() >> propOrEmpty(userHome)
        1 * gradlePropertiesLoader.loadFromEnvironmentVariables() >> propOrEmpty(envVars)
        1 * gradlePropertiesLoader.loadFromSystemProperties() >> propOrEmpty(sysProps)
        1 * gradlePropertiesLoader.loadFromStartParameterProjectProperties() >> propOrEmpty(paramProps)

        when:
        controller.loadGradleProperties(rootBuildId, buildRootDir, false)
        def buildScopedProperties = controller.getGradleProperties(rootBuildId)

        then:
        buildScopedProperties.properties == [prop: expected]

        where:
        observedSource    | shadowedSource  | gradleHome | buildRoot | userHome | envVars | sysProps | paramProps | expected
        "build-root"      | "gradle home"   | "home"     | "build"   | null     | null    | null     | null       | "build"
        "user home"       | "build-root"    | "home"     | "build"   | "user"   | null    | null     | null       | "user"
        "env variables"   | "user home"     | "home"     | "build"   | "user"   | "env"   | null     | null       | "env"
        "system props"    | "env variables" | "home"     | "build"   | "user"   | "env"   | "sys"    | null       | "sys"
        "start parameter" | "system props"  | "home"     | "build"   | "user"   | "env"   | "sys"    | "param"    | "param"
    }

    def "project-scoped properties from #observedSource take precedence over #shadowedSource properties"() {
        given:
        def projectDir = new File("projectDir")
        def controller = newDefaultGradlePropertiesController()

        def propOrEmpty = { value -> value ? [prop: value] : [:] }

        1 * gradlePropertiesLoader.loadFromGradleHome() >> propOrEmpty(gradleHome)
        1 * gradlePropertiesLoader.loadFrom(buildRootDir) >> propOrEmpty(buildRoot)
        1 * gradlePropertiesLoader.loadFrom(projectDir) >> propOrEmpty(projDir)
        1 * gradlePropertiesLoader.loadFromGradleUserHome() >> propOrEmpty(userHome)
        1 * gradlePropertiesLoader.loadFromEnvironmentVariables() >> propOrEmpty(envVars)
        1 * gradlePropertiesLoader.loadFromSystemProperties() >> propOrEmpty(sysProps)
        1 * gradlePropertiesLoader.loadFromStartParameterProjectProperties() >> propOrEmpty(paramProps)

        when:
        controller.loadGradleProperties(rootBuildId, buildRootDir, false)
        controller.loadGradleProperties(rootProjectId, projectDir)
        def projectScopedProperties = controller.getGradleProperties(rootProjectId)

        then:
        projectScopedProperties.properties == [prop: expected]

        where:
        observedSource    | shadowedSource  | gradleHome | buildRoot | projDir | userHome | envVars | sysProps | paramProps | expected
        "build-root"      | "gradle home"   | "home"     | "build"   | null    | null     | null    | null     | null       | "build"
        "project dir"     | "build-root"    | "home"     | "build"   | "proj"  | null     | null    | null     | null       | "proj"
        "user home"       | "project dir"   | "home"     | "build"   | "proj"  | "user"   | null    | null     | null       | "user"
        "env variables"   | "user home"     | "home"     | "build"   | "proj"  | "user"   | "env"   | null     | null       | "env"
        "system props"    | "env variables" | "home"     | "build"   | "proj"  | "user"   | "env"   | "sys"    | null       | "sys"
        "start parameter" | "system props"  | "home"     | "build"   | "proj"  | "user"   | "env"   | "sys"    | "param"    | "param"
    }

    def "system properties are installed from multiple sources as part of loading build-scoped properties"() {
        given:
        def controller = newDefaultGradlePropertiesController()

        1 * gradlePropertiesLoader.loadFromGradleHome() >> ["gradleHomeProp": "gradleHomeValue", "commonProp": "gradleHomeValue"]
        1 * gradlePropertiesLoader.loadFrom(buildRootDir) >> ["buildRootProp": "buildRootValue", "commonProp": "buildRootValue"]
        1 * gradlePropertiesLoader.loadFromGradleUserHome() >> ["userHomeProp": "userHomeValue", "commonProp": "userHomeValue"]
        1 * gradlePropertiesLoader.loadFromEnvironmentVariables() >> ["envProp": "envValue", "commonProp": "envValue"]
        1 * gradlePropertiesLoader.loadFromSystemProperties() >> ["sysProp": "sysValue", "commonProp": "sysValue"]
        1 * gradlePropertiesLoader.loadFromStartParameterProjectProperties() >> ["paramProp": "paramValue", "commonProp": "paramValue"]

        when:
        controller.loadGradleProperties(rootBuildId, buildRootDir, true)

        then:
        1 * systemPropertiesInstaller.setSystemPropertiesFrom(_) >> { GradleProperties props ->
            assert props.properties == [
                gradleHomeProp: "gradleHomeValue",
                buildRootProp: "buildRootValue",
                userHomeProp: "userHomeValue",

                commonProp: "userHomeValue" // user home properties have the highest precedence

                // system properties are not installed from environment variables, (other) system properties or start-parameter properties
            ]
        }
    }

    def "system properties installed from #observedSource take precedence over #shadowedSource properties"() {
        given:
        def controller = newDefaultGradlePropertiesController()

        def propOrEmpty = { value -> value ? [prop: value] : [:] }

        1 * gradlePropertiesLoader.loadFromGradleHome() >> propOrEmpty(gradleHome)
        1 * gradlePropertiesLoader.loadFrom(buildRootDir) >> propOrEmpty(buildRoot)
        1 * gradlePropertiesLoader.loadFromGradleUserHome() >> propOrEmpty(userHome)
        // these sources are not used for system properties installation, see the previous test
        1 * gradlePropertiesLoader.loadFromEnvironmentVariables() >> [:]
        1 * gradlePropertiesLoader.loadFromSystemProperties() >> [:]
        1 * gradlePropertiesLoader.loadFromStartParameterProjectProperties() >> [:]

        when:
        controller.loadGradleProperties(rootBuildId, buildRootDir, true)

        then:
        1 * systemPropertiesInstaller.setSystemPropertiesFrom(_) >> { GradleProperties props ->
            assert props.properties == [prop: expected]
        }

        where:
        observedSource | shadowedSource | gradleHome | buildRoot | userHome | expected
        "build-root"   | "gradle home"  | "home"     | "build"   | null     | "build"
        "user home"    | "build-root"   | "home"     | "build"   | "user"   | "user"
    }

    def "each build loads own gradle properties"() {
        given:
        def includedDir = new File(buildRootDir, "included")
        def controller = newDefaultGradlePropertiesController()

        def rootBuildId = this.rootBuildId
        def includedBuildId = new DefaultBuildIdentifier(Path.path(":included"))

        def rootProperties = controller.getGradleProperties(rootBuildId)
        def includedProperties = controller.getGradleProperties(includedBuildId)

        2 * gradlePropertiesLoader.loadFromGradleHome() >> [:]
        1 * gradlePropertiesLoader.loadFrom(buildRootDir) >> ["prop": "root value"]
        1 * gradlePropertiesLoader.loadFrom(includedDir) >> ["prop": "included value"]
        2 * gradlePropertiesLoader.loadFromGradleUserHome() >> [:]
        2 * gradlePropertiesLoader.loadFromEnvironmentVariables() >> [:]
        2 * gradlePropertiesLoader.loadFromSystemProperties() >> [:]
        2 * gradlePropertiesLoader.loadFromStartParameterProjectProperties() >> [:]

        when:
        controller.loadGradleProperties(rootBuildId, buildRootDir, false)
        controller.loadGradleProperties(includedBuildId, includedDir, false)

        then:
        rootProperties.find("prop") == "root value"
        includedProperties.find("prop") == "included value"

        and: "properties are separate instances"
        !rootProperties.is(includedProperties)
    }

    def "supports unloading build-scoped properties"() {
        given:
        def controller = newDefaultGradlePropertiesController()

        1 * gradlePropertiesLoader.loadFromGradleHome() >> [:]
        1 * gradlePropertiesLoader.loadFrom(buildRootDir) >> [prop: "value"]
        1 * gradlePropertiesLoader.loadFromGradleUserHome() >> [:]
        1 * gradlePropertiesLoader.loadFromEnvironmentVariables() >> [:]
        1 * gradlePropertiesLoader.loadFromSystemProperties() >> [:]
        1 * gradlePropertiesLoader.loadFromStartParameterProjectProperties() >> [:]

        def properties = controller.getGradleProperties(rootBuildId)

        when: "properties are loaded"
        controller.loadGradleProperties(rootBuildId, buildRootDir, false)

        then: "properties can be accessed"
        properties.find("prop") == "value"

        when: "properties are unloaded"
        controller.unloadGradleProperties(rootBuildId)

        and: "accessing properties fails"
        properties.find("prop")

        then:
        thrown(IllegalStateException)
    }

    def "unloading build-scoped properties fails if project properties have been loaded"() {
        given:
        def projectDir = new File("projectDir")
        def controller = newDefaultGradlePropertiesController()

        1 * gradlePropertiesLoader.loadFromGradleHome() >> [:]
        1 * gradlePropertiesLoader.loadFrom(buildRootDir) >> [:]
        1 * gradlePropertiesLoader.loadFromGradleUserHome() >> [:]
        1 * gradlePropertiesLoader.loadFromEnvironmentVariables() >> [:]
        1 * gradlePropertiesLoader.loadFromSystemProperties() >> [:]
        1 * gradlePropertiesLoader.loadFromStartParameterProjectProperties() >> [:]

        1 * gradlePropertiesLoader.loadFrom(projectDir) >> [:]

        when: "build and project properties are loaded"
        controller.loadGradleProperties(rootBuildId, buildRootDir, false)
        controller.loadGradleProperties(rootProjectId, projectDir)

        and: "attempting to unload build properties"
        controller.unloadGradleProperties(rootBuildId)

        then:
        thrown(IllegalStateException)
    }

    private DefaultGradlePropertiesController newDefaultGradlePropertiesController() {
        new DefaultGradlePropertiesController(gradlePropertiesLoader, systemPropertiesInstaller, listener)
    }
}
