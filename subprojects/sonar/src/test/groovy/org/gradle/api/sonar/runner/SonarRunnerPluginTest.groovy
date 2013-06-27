/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.sonar.runner

import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.HelperUtil
import org.gradle.util.SetSystemProperties
import org.junit.Rule

import spock.lang.Specification

import static spock.util.matcher.HamcrestSupport.*
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*

class SonarRunnerPluginTest extends Specification {
    @Rule SetSystemProperties systemProperties

    def rootProject = HelperUtil.builder().withName("root").build()
    def parentProject = HelperUtil.builder().withName("parent").withParent(rootProject).build()
    def childProject = HelperUtil.builder().withName("child").withParent(parentProject).build()
    def childProject2 = HelperUtil.builder().withName("child2").withParent(parentProject).build()
    def leafProject = HelperUtil.builder().withName("leaf").withParent(childProject).build()

    def setup() {
        parentProject.plugins.apply(SonarRunnerPlugin)
        rootProject.allprojects {
            group = "group"
            version = 1.3
            description = "description"
            buildDir = "buildDir"
        }
    }

    def "adds a sonarRunner extension to the target project (i.e. the project to which the plugin is applied) and its subprojects"() {
        expect:
        rootProject.extensions.findByName("sonarRunner") == null
        parentProject.extensions.findByName("sonarRunner") instanceof SonarRunnerExtension
        childProject.extensions.findByName("sonarRunner") instanceof SonarRunnerExtension
    }

    def "adds a sonarRunner task to the target project"() {
        expect:
        parentProject.tasks.findByName("sonarRunner") instanceof SonarRunner
        parentProject.tasks.sonarRunner.description == "Analyzes project ':parent' and its subprojects with Sonar Runner."

        childProject.tasks.findByName("sonarRunner") == null
    }

    def "makes sonarRunner task depend on test tasks of the target project and its subprojects"() {
        when:
        rootProject.plugins.apply(JavaPlugin)
        parentProject.plugins.apply(JavaPlugin)
        childProject.plugins.apply(JavaPlugin)

        then:
        expect(parentProject.tasks.sonarRunner, dependsOnPaths(containsInAnyOrder(":parent:test", ":parent:child:test")))
    }

    def "doesn't make sonarRunner task depend on test task of skipped projects"() {
        when:
        rootProject.plugins.apply(JavaPlugin)
        parentProject.plugins.apply(JavaPlugin)
        childProject.plugins.apply(JavaPlugin)
        childProject.sonarRunner.skipProject = true

        then:
        expect(parentProject.tasks.sonarRunner, dependsOnPaths(contains(":parent:test")))
    }

    def "adds default properties for target project and its subprojects"() {
        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.sources"] == ""
        properties["sonar.projectName"] == "parent"
        properties["sonar.projectDescription"] == "description"
        properties["sonar.projectVersion"] == "1.3"
        properties["sonar.projectBaseDir"] == parentProject.projectDir as String
        properties["sonar.working.directory"] == new File(parentProject.buildDir, "sonar") as String
        properties["sonar.dynamicAnalysis"] == "reuseReports"

        and:
        properties["child.sonar.sources"] == ""
        properties["child.sonar.projectName"] == "child"
        properties["child.sonar.projectDescription"] == "description"
        properties["child.sonar.projectVersion"] == "1.3"
        properties["child.sonar.projectBaseDir"] == childProject.projectDir as String
        properties["child.sonar.dynamicAnalysis"] == "reuseReports"
    }

    def "adds additional default properties for target project"() {
        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.projectKey"] == "group:parent"
        properties["sonar.environment.information.key"] == "Gradle"
        properties["sonar.environment.information.version"] == parentProject.gradle.gradleVersion
        properties["sonar.working.directory"] == new File(parentProject.buildDir, "sonar") as String

        and:
        !properties.containsKey("child.sonar.projectKey") // default left to Sonar
        !properties.containsKey("child.sonar.environment.information.key")
        !properties.containsKey("child.sonar.environment.information.version")
        !properties.containsKey('child.sonar.working.directory')
    }

    def "defaults projectKey to project.name if project.group isn't set"() {
        parentProject.group = "" // or null, but only rootProject.group can effectively be set to null

        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.projectKey"] == "parent"
    }

    def "adds additional default properties for 'java-base' projects"() {
        parentProject.plugins.apply(JavaBasePlugin)
        childProject.plugins.apply(JavaBasePlugin)
        parentProject.sourceCompatibility = 1.5
        parentProject.targetCompatibility = 1.6
        childProject.sourceCompatibility = 1.6
        childProject.targetCompatibility = 1.7

        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.java.source"] == "1.5"
        properties["sonar.java.target"] == "1.6"
        properties["child.sonar.java.source"] == "1.6"
        properties["child.sonar.java.target"] == "1.7"
    }

    def "adds additional default properties for 'java' projects"() {
        parentProject.plugins.apply(JavaPlugin)

        parentProject.sourceSets.main.java.srcDirs = ["src"]
        parentProject.sourceSets.test.java.srcDirs = ["test"]
        parentProject.sourceSets.main.output.classesDir = "$parentProject.buildDir/out"
        parentProject.sourceSets.main.output.resourcesDir = "$parentProject.buildDir/out"
        parentProject.sourceSets.main.runtimeClasspath += parentProject.files("lib/SomeLib.jar")

        new TestFile(parentProject.projectDir).create {
            src {}
            test {}
            buildDir {
                out {}
                "test-results" {}
            }
            lib {
                file("SomeLib.jar")
            }
        }

        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.sources"] == new File(parentProject.projectDir, "src") as String
        properties["sonar.tests"] == new File(parentProject.projectDir, "test") as String
        properties["sonar.binaries"].contains(new File(parentProject.buildDir, "out") as String)
        properties["sonar.libraries"].contains(new File(parentProject.projectDir, "lib/SomeLib.jar") as String)
        properties["sonar.surefire.reportsPath"] == new File(parentProject.buildDir, "test-results") as String
    }

    def "only adds existing directories"() {
        parentProject.plugins.apply(JavaPlugin)

        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        !properties.containsKey("sonar.tests")
        !properties.containsKey("sonar.binaries")
        properties.containsKey("sonar.libraries") == (Jvm.current().getRuntimeJar() != null)
        !properties.containsKey("sonar.surefire.reportsPath")
    }

    def "adds empty 'sonar.sources' property if no sources exist (because Sonar Runner 2.0 always expects this property to be set)"() {
        childProject2.plugins.apply(JavaPlugin)

        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.sources"] == ""
        properties["child.sonar.sources"] == ""
        properties["child2.sonar.sources"] == ""
        properties["child.leaf.sonar.sources"] == ""
    }

    def "allows to configure Sonar properties via 'sonarRunner' extension"() {
        parentProject.sonarRunner.sonarProperties {
            property "sonar.some.key", "some value"
        }

        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.some.key"] == "some value"
    }

    def "prefixes property keys of subprojects"() {
        childProject.sonarRunner.sonarProperties {
            property "sonar.some.key", "other value"
        }
        leafProject.sonarRunner.sonarProperties {
            property "sonar.some.key", "other value"
        }

        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        properties["child.sonar.some.key"] == "other value"
        properties["child.leaf.sonar.some.key"] == "other value"
    }

    def "adds 'modules' properties declaring (prefixes of) subprojects"() {
        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.modules"] == "child,child2"
        properties["child.sonar.modules"] == "leaf"
        !properties.containsKey("child2.sonar.modules")
        !properties.containsKey("child.leaf.sonar.modules")
    }

    def "handles 'modules' properties correctly if plugin is applied to root project"() {
        def rootProject = HelperUtil.builder().withName("root").build()
        def project = HelperUtil.builder().withName("parent").withParent(rootProject).build()
        def project2 = HelperUtil.builder().withName("parent2").withParent(rootProject).build()
        def childProject = HelperUtil.builder().withName("child").withParent(project).build()

        rootProject.plugins.apply(SonarRunnerPlugin)

        when:
        def properties = rootProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.modules"] == "parent,parent2"
        properties["parent.sonar.modules"] == "child"
        !properties.containsKey("parent2.sonar.modules")
        !properties.containsKey("parent.child.sonar.modules")

    }

    def "evaluates 'sonarRunner' block lazily"() {
        parentProject.version = "1.0"
        parentProject.sonarRunner.sonarProperties {
            property "sonar.projectVersion", parentProject.version
        }
        parentProject.version = "1.2.3"

        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.projectVersion"] == "1.2.3"
    }

    def "converts Sonar property values to strings"() {
        def object = new Object() {
            String toString() {
                "object"
            }
        }

        parentProject.sonarRunner.sonarProperties {
            property "some.object", object
            property "some.list", [1, object, 2]
        }

        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        properties["some.object"] == "object"
        properties["some.list"] == "1,object,2"
    }

    def "removes Sonar properties with null values"() {
        parentProject.sonarRunner.sonarProperties {
            property "some.key", null
        }

        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        !properties.containsKey("some.key")
    }

    def "allows to set Sonar properties for target project via 'sonar.xyz' system properties"() {
        System.setProperty("sonar.some.key", "some value")
        System.setProperty("sonar.projectVersion", "3.2")

        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.some.key"] == "some value"
        properties["sonar.projectVersion"] == "3.2"

        and:
        !properties.containsKey("child.sonar.some.key")
        properties["child.sonar.projectVersion"] == "1.3"
    }

    def "handles system properties correctly if plugin is applied to root project"() {
        def rootProject = HelperUtil.builder().withName("root").build()
        def project = HelperUtil.builder().withName("parent").withParent(rootProject).build()

        rootProject.allprojects { version = 1.3 }
        rootProject.plugins.apply(SonarRunnerPlugin)
        System.setProperty("sonar.some.key", "some value")
        System.setProperty("sonar.projectVersion", "3.2")

        when:
        def properties = rootProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.some.key"] == "some value"
        properties["sonar.projectVersion"] == "3.2"

        and:
        !properties.containsKey("parent.sonar.some.key")
        properties["parent.sonar.projectVersion"] == "1.3"
    }

    def "system properties win over values set in build script"() {
        System.setProperty("sonar.some.key", "win")
        parentProject.sonarRunner.sonarProperties {
            property "sonar.some.key", "lose"
        }

        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.some.key"] == "win"
    }

    def "doesn't add Sonar properties for skipped projects"() {
        childProject.sonarRunner.skipProject = true

        when:
        def properties = parentProject.tasks.sonarRunner.sonarProperties

        then:
        !properties.any { key, value -> key.startsWith("child.sonar.") }
    }
}
