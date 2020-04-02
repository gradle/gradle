/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.scala

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.language.scala.internal.toolchain.DefaultScalaToolProvider
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.not

class ScalaPluginIntegrationTest extends AbstractIntegrationSpec {
    @Issue("https://issues.gradle.org/browse/GRADLE-3094")
    def "can apply scala plugin"() {
        file("build.gradle") << """
apply plugin: "scala"

task someTask
"""

        expect:
        succeeds("someTask")
    }

    @Issue("https://github.com/gradle/gradle/issues/6558")
    def "can build in parallel with lazy tasks"() {
        settingsFile << """
            include 'a', 'b', 'c', 'd'
        """
        buildFile << """
            allprojects {
                tasks.withType(AbstractScalaCompile) {
                    options.fork = true
                }
                repositories {
                    ${jcenterRepository()}
                }
                plugins.withId("scala") {
                    dependencies {
                        implementation("org.scala-lang:scala-library:2.12.6")
                    }
                }
            }
        """
        ['a', 'b', 'c', 'd'].each { project ->
            file("${project}/build.gradle") << """
                plugins {
                    id 'scala'
                }
            """
            file("${project}/src/main/scala/${project}/${project.toUpperCase()}.scala") << """
                package ${project}
                trait ${project.toUpperCase()}
            """
        }
        file("a/build.gradle") << """
            dependencies {
              implementation(project(":b"))
              implementation(project(":c"))
              implementation(project(":d"))
            }
        """

        expect:
        succeeds(":a:classes", "--parallel")
        true
    }

    @Issue("https://github.com/gradle/gradle/issues/6735")
    def "can depend on the source set of another Java project"() {
        settingsFile << """
            include 'java', 'scala'
        """
        buildFile << """
            allprojects {
                repositories {
                    ${jcenterRepository()}
                }
            }
            project(":java") {
                apply plugin: 'java'
            }
            project(":scala") {
                apply plugin: 'scala'
                dependencies {
                    implementation("org.scala-lang:scala-library:2.12.6")
                    implementation(project(":java").sourceSets.main.output)
                }
            }
        """
        file("java/src/main/java/Bar.java") << """
            public class Bar {}
        """
        file("scala/src/test/scala/Foo.scala") << """
            trait Foo {
                val bar: Bar
            }
        """
        expect:
        succeeds(":scala:testClasses")
    }

    @Issue("https://github.com/gradle/gradle/issues/6750")
    def "can depend on Scala project from other project"() {
        settingsFile << """
            include 'other', 'scala'
        """
        buildFile << """
            allprojects {
                repositories {
                    ${jcenterRepository()}
                }
            }
            project(":other") {
                apply plugin: 'base'
                configurations {
                    conf
                }
                dependencies {
                    conf(project(":scala"))
                }
                task resolve {
                    dependsOn configurations.conf
                    doLast {
                        println configurations.conf.files
                    }
                }
            }
            project(":scala") {
                apply plugin: 'scala'

                dependencies {
                    implementation("org.scala-lang:scala-library:2.12.6")
                }
            }
        """
        file("scala/src/main/scala/Bar.scala") << """
            class Bar {
            }
        """
        expect:
        succeeds(":other:resolve")
    }

    @Issue("https://github.com/gradle/gradle/issues/7014")
    def "can use Scala with war and ear plugins"() {
        settingsFile << """
            include 'war', 'ear'
        """
        buildFile << """
            allprojects {
                repositories {
                    ${jcenterRepository()}
                }
                apply plugin: 'scala'

                dependencies {
                    implementation("org.scala-lang:scala-library:2.12.6")
                }
            }
            project(":war") {
                apply plugin: 'war'
            }
            project(":ear") {
                apply plugin: 'ear'
                dependencies {
                    deploy project(path: ':war', configuration: 'archives')
                }
            }
        """
        file("war/src/main/scala/Bar.scala") << """
            class Bar {
            }
        """
        expect:
        succeeds(":ear:assemble")
        // The Scala incremental compilation mapping should not be exposed to anything else
        file("ear/build/tmp/ear/application.xml").assertContents(not(containsString("implementationScala.mapping")))
    }

    @Issue("https://github.com/gradle/gradle/issues/6849")
    @ToBeFixedForInstantExecution
    def "can publish test-only projects"() {
        using m2
        settingsFile << """
            rootProject.name = "scala"
        """
        buildFile << """
            apply plugin: 'scala'
            apply plugin: 'maven'

            repositories {
                ${jcenterRepository()}
            }
            dependencies {
                implementation("org.scala-lang:scala-library:2.12.6")
            }
        """
        file("src/test/scala/Foo.scala") << """
            class Foo
        """
        expect:
        executer.expectDeprecationWarning()
        succeeds("install")
    }

    def "forcing an incompatible version of Scala fails with a clear error message"() {
        settingsFile << """
            rootProject.name = "scala"
        """
        buildFile << """
            apply plugin: 'scala'

            repositories {
                ${jcenterRepository()}
            }
            dependencies {
                implementation("org.scala-lang:scala-library")
            }
            configurations.all {
                resolutionStrategy.force "org.scala-lang:scala-library:2.10.7"
            }
        """
        file("src/main/scala/Foo.scala") << """
            class Foo
        """
        when:
        fails("assemble")

        then:
        failureHasCause("The version of 'scala-library' was changed while using the default Zinc version." +
            " Version 2.10.7 is not compatible with org.scala-sbt:zinc_2.12:" + DefaultScalaToolProvider.DEFAULT_ZINC_VERSION)
    }

    def "trying to use an old version of Zinc switches to Gradle-supported version"() {
        settingsFile << """
            rootProject.name = "scala"
        """
        buildFile << """
            apply plugin: 'scala'

            repositories {
                ${jcenterRepository()}
            }
            dependencies {
                zinc("com.typesafe.zinc:zinc:0.3.6")
                implementation("org.scala-lang:scala-library:2.12.6")
            }
        """
        file("src/main/scala/Foo.scala") << """
            class Foo
        """
        expect:
        succeeds("assemble")
        succeeds("dependencyInsight", "--configuration", "zinc", "--dependency", "zinc")
    }
}
