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
    }

    def "adds a sonarRunner extension for the project and its subprojects"() {
        expect:
        project.extensions.findByName("sonarRunner") instanceof SonarRunnerExtension
        childProject.extensions.findByName("sonarRunner") instanceof SonarRunnerExtension
    }

    def "sets default for root project's sonarRunner.bootstrapDir property"() {
        expect:
        project.sonarRunner.bootstrapDir == new File(project.buildDir, "sonar/bootstrap")
        childProject.sonarRunner.bootstrapDir == null
    }

    def "adds a sonarRunner task to the root project"() {
        expect:
        project.tasks.findByName("sonarRunner") instanceof SonarRunner
        childProject.tasks.findByName("sonarRunner") == null
    }

    def "provides extensive defaults for 'sonarRunner' task properties"() {
        //TODO
        with(project.tasks.sonarRunner) {
        }
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
        properties["leaf.child.sonar.some.key"] == "other value"
    }

    def "adds 'modules' properties to declare subprojects (and their prefixes)"() {
        when:
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        properties["sonar.modules"] == "child,child2"
        properties["child.sonar.modules"] == "leaf"
        !properties.containsKey("child2.sonar.modules")
        !properties.containsKey("leaf.child.sonar.modules")
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
        properties["child.sonar.projectVersion"] == "unspecified"
    }

    def "doesn't add Sonar properties for skipped projects"() {
        childProject.sonarRunner.skipProject = true

        when:
        def properties = project.tasks.sonarRunner.sonarProperties

        then:
        !properties.any { key, value -> key.startsWith("child.sonar.") }
    }
}
