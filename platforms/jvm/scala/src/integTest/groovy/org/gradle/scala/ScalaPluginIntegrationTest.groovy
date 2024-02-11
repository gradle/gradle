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

import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

import static org.gradle.scala.ScalaCompilationFixture.scalaDependency
import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.not

@TargetCoverage({ ScalaCoverage.LATEST_IN_MAJOR })
class ScalaPluginIntegrationTest extends MultiVersionIntegrationSpec {

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
        createDirs("a", "b", "c", "d")
        settingsFile << """
            include 'a', 'b', 'c', 'd'
        """
        buildFile << """
            allprojects {
                tasks.withType(AbstractScalaCompile) {
                    options.fork = true
                }
                ${mavenCentralRepository()}
                plugins.withId("scala") {
                    dependencies {
                        implementation("${scalaDependency(version.toString())}")
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
        executer.noDeprecationChecks()
        succeeds(":a:classes", "--parallel")
        true
    }

    @Issue("https://github.com/gradle/gradle/issues/6735")
    def "can depend on the source set of another Java project"() {
        createDirs("java", "scala")
        settingsFile << """
            include 'java', 'scala'
        """
        buildFile << """
            allprojects {
                ${mavenCentralRepository()}
            }
            project(":java") {
                apply plugin: 'java'
            }
            project(":scala") {
                apply plugin: 'scala'
                dependencies {
                    implementation("${scalaDependency(version.toString())}")
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
        createDirs("other", "scala")
        settingsFile << """
            include 'other', 'scala'
        """
        buildFile << """
            allprojects {
                ${mavenCentralRepository()}
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
                    implementation("${scalaDependency(version.toString())}")
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
        createDirs("war", "ear")
        settingsFile << """
            include 'war', 'ear'
        """
        buildFile << """
            allprojects {
                ${mavenCentralRepository()}
                apply plugin: 'scala'

                dependencies {
                    implementation("${scalaDependency(version.toString())}")
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

    def "forcing an incompatible version of Scala fails with a clear error message"() {
        def fixedScalaVersion = "2.10.7"
        settingsFile << """
            rootProject.name = "scala"
        """
        buildFile << """
            apply plugin: 'scala'

            ${mavenCentralRepository()}
            dependencies {
                implementation("org.scala-lang:scala-library")
            }
            configurations.all {
                resolutionStrategy.force "org.scala-lang:scala-library:${fixedScalaVersion}"
            }
        """
        file("src/main/scala/Foo.scala") << """
            class Foo
        """
        when:
        fails("assemble")

        then:
        def expectedMessage = "The version of 'scala-library' was changed while using the default Zinc version." +
            " Version ${fixedScalaVersion} is not compatible with org.scala-sbt:zinc_2.13:" + ScalaBasePlugin.DEFAULT_ZINC_VERSION
        failureHasCause(expectedMessage)
    }

    def "trying to use an old version of Zinc switches to Gradle-supported version"() {
        settingsFile << """
            rootProject.name = "scala"
        """
        buildFile << """
            apply plugin: 'scala'

            ${mavenCentralRepository()}

            dependencies {
                zinc("com.typesafe.zinc:zinc:0.3.6")
                implementation("${scalaDependency(version.toString())}")
            }
        """
        file("src/main/scala/Foo.scala") << """
            class Foo
        """
        expect:
        succeeds("assemble")
        succeeds("dependencyInsight", "--configuration", "zinc", "--dependency", "zinc")
    }

    @Issue("gradle/gradle#19300")
    def 'show that log4j-core, if present, is 2_17_1 at the minimum'() {
        given:
        file('build.gradle') << """
            apply plugin: 'scala'

            ${mavenCentralRepository()}
        """

        def versionPattern = ~/.*-> 2\.(\d+).*/
        expect:
        succeeds('dependencies', '--configuration', 'zinc')
        def log4jOutput = result.getOutputLineThatContains("log4j-core:{require 2.17.1; reject [2.0, 2.17.1)}")
        def matcher = log4jOutput =~ versionPattern
        matcher.find()
        Integer.valueOf(matcher.group(1)) >= 16
    }

    @Requires(IntegTestPreconditions.NotNoDaemonExecutor)
    def "Scala compiler daemon respects keepalive option"() {
        buildFile << """
            plugins {
                id 'scala'
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation("${scalaDependency(version.toString())}")
            }

            tasks.withType(AbstractScalaCompile) {
                scalaCompileOptions.keepAliveMode = KeepAliveMode.SESSION
            }
        """
        file('src/main/scala/Foo.scala') << '''
            class Foo {
            }
        '''
        expect:
        succeeds(':compileScala', '--info')
        postBuildOutputContains('Stopped 1 worker daemon')

        when:
        buildFile.text = buildFile.text.replace('SESSION', 'DAEMON')

        then:
        succeeds(':compileScala', '--info')
        postBuildOutputDoesNotContain('Stopped 1 worker daemon')
    }
}
