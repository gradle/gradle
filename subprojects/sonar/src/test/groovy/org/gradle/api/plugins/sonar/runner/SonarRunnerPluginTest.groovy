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
package org.gradle.api.plugins.sonar.runner

import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.SetSystemProperties
import org.junit.Rule

import spock.lang.Specification

class SonarRunnerPluginTest extends Specification {
    @Rule SetSystemProperties systemProperties

    def project = ProjectBuilder.builder().withName("parent").build()
    def childProject = ProjectBuilder.builder().withName("child").withParent(project).build()
    def childProject2 = ProjectBuilder.builder().withName("child2").withParent(project).build()
    def leafProject = ProjectBuilder.builder().withName("leaf").withParent(childProject).build()

    def setup() {
        project.plugins.apply(SonarRunnerPlugin)
        project.allprojects {
            group = "group"
            version = 1.3
            description = "description"
            buildDir = "buildDir"
        }
    }

    def "adds a sonarRunner extension for the project and its subprojects"() {
        expect:
        project.extensions.findByName("sonarRunner") instanceof SonarRunnerExtension
        childProject.extensions.findByName("sonarRunner") instanceof SonarRunnerExtension
    }

    def "adds a sonarRunner task to the root project"() {
        expect:
        project.tasks.findByName("sonarRunner") instanceof SonarRunner
        childProject.tasks.findByName("sonarRunner") == null
    }

    def "makes sonarRunner task depend on test task of all Java projects"() {
        when:
        project.plugins.apply(JavaPlugin)
        childProject2.plugins.apply(JavaPlugin)

        then:
        project.tasks.sonarRunner.dependsOn.contains(project.tasks.test)
        project.tasks.sonarRunner.dependsOn.contains(childProject2.tasks.test)
    }

    def "adds default properties for all projects"() {
        when:
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.sources"] == ""
        properties["sonar.projectKey"] == "group%3Aparent"
        properties["sonar.projectName"] == "parent"
        properties["sonar.projectDescription"] == "description"
        properties["sonar.projectVersion"] == "1.3"
        properties["sonar.projectBaseDir"] == project.projectDir as String
        properties["sonar.working.directory"] == new File(project.buildDir, "sonar") as String
        properties["sonar.dynamicAnalysis"] == "reuseReports"

        and:
        properties["child.sonar.sources"] == ""
        properties["child.sonar.projectKey"] == "group%3Aparent%3Achild"
        properties["child.sonar.projectName"] == "child"
        properties["child.sonar.projectDescription"] == "description"
        properties["child.sonar.projectVersion"] == "1.3"
        properties["child.sonar.projectBaseDir"] == childProject.projectDir as String
        properties["child.sonar.dynamicAnalysis"] == "reuseReports"
    }

    def "adds additional default properties for the root project"() {
        when:
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.environment.information.key"] == "Gradle"
        properties["sonar.environment.information.version"] == project.gradle.gradleVersion
        properties["sonar.working.directory"] == new File(project.buildDir, "sonar") as String

        and:
        !properties.containsKey("child.sonar.environment.information.key")
        !properties.containsKey("child.sonar.environment.information.version")
        !properties.containsKey('child.sonar.working.directory')
    }

    def "adds additional default properties for all 'java-base' projects"() {
        project.plugins.apply(JavaBasePlugin)
        childProject.plugins.apply(JavaBasePlugin)
        project.sourceCompatibility = 1.5
        project.targetCompatibility = 1.6
        childProject.sourceCompatibility = 1.6
        childProject.targetCompatibility = 1.7

        when:
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.java.source"] == "1.5"
        properties["sonar.java.target"] == "1.6"
        properties["child.sonar.java.source"] == "1.6"
        properties["child.sonar.java.target"] == "1.7"
    }

    def "adds additional default properties for all 'java' projects"() {
        project.plugins.apply(JavaPlugin)

        project.sourceSets.main.java.srcDirs = ["src"]
        project.sourceSets.test.java.srcDirs = ["test"]
        project.sourceSets.main.output.classesDir = "$project.buildDir/out"
        project.sourceSets.main.output.resourcesDir = "$project.buildDir/out"
        project.sourceSets.main.runtimeClasspath += project.files("lib/SomeLib.jar")

        new TestFile(project.projectDir).create {
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
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.sources"] == new File(project.projectDir, "src") as String
        properties["sonar.tests"] == new File(project.projectDir, "test") as String
        properties["sonar.binaries"].contains(new File(project.buildDir, "out") as String)
        properties["sonar.libraries"].contains(new File(project.projectDir, "lib/SomeLib.jar") as String)
        properties["sonar.surefire.reportsPath"] == new File(project.buildDir, "test-results") as String
    }

    def "only adds existing directories"() {
        project.plugins.apply(JavaPlugin)

        when:
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        !properties.containsKey("sonar.sources")
        !properties.containsKey("sonar.tests")
        !properties.containsKey("sonar.binaries")
        properties.containsKey("sonar.libraries") == (Jvm.current().getRuntimeJar() != null)
        !properties.containsKey("sonar.surefire.reportsPath")
    }

    def "allows to configure Sonar properties via 'sonarRunner' extension"() {
        project.sonarRunner.sonarProperties {
            property "sonar.some.key", "some value"
        }

        when:
        def properties = project.tasks.sonarRunner.sonarProperties

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
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        properties["child.sonar.some.key"] == "other value"
        properties["child.leaf.sonar.some.key"] == "other value"
    }

    def "adds 'modules' properties to declare subprojects (and their prefixes)"() {
        when:
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.modules"] in ["child,child2", "child2,child"]
        properties["child.sonar.modules"] == "leaf"
        !properties.containsKey("child2.sonar.modules")
        !properties.containsKey("child.leaf.sonar.modules")
    }

    def "evaluates 'sonarRunner' block lazily"() {
        project.version = "1.0"
        project.sonarRunner.sonarProperties {
            property "sonar.projectVersion", project.version
        }
        project.version = "1.2.3"

        when:
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.projectVersion"] == "1.2.3"
    }

    def "converts Sonar property values to strings"() {
        def object = new Object() {
            String toString() {
                "object"
            }
        }

        project.sonarRunner.sonarProperties {
            property "some.object", object
            property "some.list", [1, object, 2]
        }

        when:
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        properties["some.object"] == "object"
        properties["some.list"] == "1,object,2"
    }

    def "removes Sonar properties with null values"() {
        project.sonarRunner.sonarProperties {
            property "some.key", null
        }

        when:
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        !properties.containsKey("some.key")
    }

    def "allows to set Sonar properties for root project via 'sonar.xyz' system properties"() {
        System.setProperty("sonar.some.key", "some value")
        System.setProperty("sonar.projectVersion", "3.2")

        when:
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.some.key"] == "some value"
        properties["sonar.projectVersion"] == "3.2"

        and:
        !properties.containsKey("child.sonar.some.key")
        properties["child.sonar.projectVersion"] == "1.3"
    }

    def "system properties win over values set in build script"() {
        System.setProperty("sonar.some.key", "win")
        project.sonarRunner.sonarProperties {
            property "sonar.some.key", "lose"
        }

        when:
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.some.key"] == "win"
    }

    def "doesn't add Sonar properties for skipped projects"() {
        childProject.sonarRunner.skipProject = true

        when:
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        !properties.any { key, value -> key.startsWith("child.sonar.") }
    }
}
