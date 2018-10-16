/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.build.BuildTestFile
import spock.lang.Issue

/**
 * Tests for plugin development scenarios within a composite build.
 */
class CompositeBuildPluginDevelopmentIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile pluginBuild
    BuildTestFile pluginDependencyA

    def setup() {
        pluginDependencyA = singleProjectBuild("pluginDependencyA") {
            buildFile << """
                apply plugin: 'java'
                version "2.0"
            """
        }

        pluginBuild = pluginProjectBuild("pluginBuild")
    }

    def "can co-develop plugin and consumer with plugin as included build"() {
        given:
        applyPlugin(buildA)
        addLifecycleTasks(buildA)

        includeBuild pluginBuild

        when:
        execute(buildA, "taskFromPluginBuild")

        then:
        executed ":pluginBuild:jar", ":taskFromPluginBuild"

        when:
        execute(buildA, "assemble")

        then:
        executed ":pluginBuild:jar", ":pluginBuild:assemble", ":assemble"
    }

    def "can co-develop plugin and consumer with both plugin and consumer as included builds"() {
        given:
        applyPlugin(pluginDependencyA)

        buildA.buildFile << """
            dependencies {
                compile "org.test:pluginDependencyA:1.0"
            }
        """
        pluginDependencyA.buildFile << """
            tasks.jar.dependsOn(tasks.taskFromPluginBuild)
        """

        includeBuild pluginBuild
        includeBuild pluginDependencyA

        when:
        execute(buildA, "assemble")

        then:
        executed ":pluginBuild:jar", ":pluginDependencyA:taskFromPluginBuild", ":pluginDependencyA:jar", ":jar"
    }

    @Issue("https://github.com/gradle/gradle/issues/5234")
    def "can co-develop plugin and multiple consumers as included builds with transitive plugin library dependency"() {
        given:
        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                apply plugin: 'java'
                version "2.0"
            """
        }
        applyPlugin(buildA)
        applyPlugin(buildB)
        includeBuild pluginBuild
        includeBuild pluginDependencyA
        includeBuild buildB
        dependency(buildA, "org.test:buildB:2.0")
        dependency(pluginBuild, "org.test:pluginDependencyA:1.0")

        when:
        execute(buildA, "assemble")

        then:
        executed ":pluginBuild:jar", ":pluginDependencyA:jar", ":buildB:jar", ":jar"
    }

    def "can co-develop plugin and consumer where plugin uses previous version of itself to build"() {
        given:
        // Ensure that 'plugin' is published with older version
        mavenRepo.module("org.test", "pluginBuild", "0.1").publish()

        pluginBuild.buildFile << """
            buildscript {
                repositories {
                    repositories {
                        maven { url "${mavenRepo.uri}" }
                    }
                }
                dependencies {
                    classpath 'org.test:pluginBuild:0.1'
                }
            }
        """

        applyPlugin(buildA)

        includeBuild pluginBuild

        when:
        execute(buildA, "taskFromPluginBuild")

        then:
        executed ":pluginBuild:jar", ":taskFromPluginBuild"
    }

    def "can develop a transitive plugin dependency as included build"() {
        given:
        applyPlugin(buildA)
        dependency(pluginBuild, "org.test:pluginDependencyA:1.0")

        includeBuild pluginBuild
        includeBuild pluginDependencyA

        when:
        execute(buildA, "taskFromPluginBuild")

        then:
        executed ":pluginDependencyA:jar", ":pluginBuild:jar", ":taskFromPluginBuild"
    }

    def "can develop a buildscript dependency that is also used by main build"() {
        given:
        buildA.buildFile << """
            buildscript {
                dependencies {
                    classpath 'org.test:pluginDependencyA:1.0'
                }
            }
        """

        dependency("org.test:pluginDependencyA:1.0")
        includeBuild pluginDependencyA

        when:
        execute(buildA, "jar")

        then:
        executed ":pluginDependencyA:jar", ":jar"
    }

    def "can develop a buildscript dependency that is used by multiple projects of main build"() {
        given:
        buildA.settingsFile << """
            include 'a1'
            include 'a2'
        """
        buildA.file("a1/build.gradle") << """
            buildscript {
                dependencies {
                    classpath 'org.test:pluginDependencyA:1.0'
                }
            }
        """
        buildA.file("a2/build.gradle") << """
            buildscript {
                dependencies {
                    classpath 'org.test:pluginDependencyA:1.0'
                }
            }
        """

        includeBuild pluginDependencyA

        when:
        execute(buildA, "help")

        then:
        executed ":pluginDependencyA:jar"
    }

    def "can use an included build that provides both a buildscript dependency and a compile dependency"() {
        given:
        def buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
            """
        }
        includedBuilds << buildB

        buildA.buildFile << """
            buildscript {
                dependencies {
                    classpath 'org.test:b1:1.0'
                }
            }
        """

        dependency("org.test:b2:1.0")

        when:
        execute(buildA, "jar")

        then:
        executed ":buildB:b1:jar", ":buildB:b2:jar", ":jar"
   }

    def "can develop a transitive plugin dependency as included build when plugin itself is not included"() {
        given:
        publishPluginWithDependency()

        buildA.buildFile << """
            buildscript {
                repositories {
                    repositories {
                        maven { url "${mavenRepo.uri}" }
                    }
                }
            }
        """
        applyPlugin(buildA)

        when:
        includeBuild pluginDependencyA
        execute(buildA, "taskFromPluginBuild")

        then:
        executed ":pluginDependencyA:jar", ":taskFromPluginBuild"
        notExecuted ":pluginBuild:jar"
    }

    private void publishPluginWithDependency() {
        dependency pluginBuild, 'org.test:pluginDependencyA:1.0'
        pluginBuild.buildFile << """
            apply plugin: 'maven-publish'
            publishing {
                repositories {
                    maven {
                        url '${mavenRepo.uri}'
                    }
                }
            }
        """
        executer.inDirectory(pluginBuild).withArguments('--include-build', pluginDependencyA.absolutePath).withTasks('publish').run()
    }

    private void publishPlugin() {
        pluginBuild.buildFile << """
            apply plugin: 'maven-publish'
            publishing {
                repositories {
                    maven {
                        url '${mavenRepo.uri}'
                    }
                }
            }
        """
        executer.inDirectory(pluginBuild).withTasks('publish').run()
    }

    def "detects dependency cycle between included builds required for buildscript classpath"() {
        given:
        def pluginDependencyB = singleProjectBuild("pluginDependencyB") {
            buildFile << """
                apply plugin: 'java'
                version "2.0"
            """
        }

        dependency pluginBuild, "org.test:pluginDependencyA:1.0"
        dependency pluginDependencyA, "org.test:pluginDependencyB:1.0"
        dependency pluginDependencyB, "org.test:pluginDependencyA:1.0"

        applyPlugin(buildA)

        includeBuild pluginBuild
        includeBuild pluginDependencyA
        includeBuild pluginDependencyB

        when:
        fails(buildA, "tasks")

        then:
        failure.assertHasDescription("Included build dependency cycle: build 'pluginDependencyA' -> build 'pluginDependencyB' -> build 'pluginDependencyA'")
    }

    def "can co-develop unpublished plugin applied via plugins block"() {
        given:
        addPluginsBlock(buildA, """
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'pluginBuild') {
                    useModule('org.test:pluginBuild:1.0')
                }
            }
        """)


        when:
        execute(buildA, "tasks", ["--include-build", "../pluginBuild"])

        then:
        executed ":pluginBuild:jar"
        outputContains("taskFromPluginBuild")

        when:
        includeBuild pluginBuild
        execute(buildA, "tasks")

        then:
        executed ":pluginBuild:jar"
        outputContains("taskFromPluginBuild")
    }

    def "can co-develop published plugin applied via plugins block"() {
        given:
        publishPlugin()
        addPluginsBlock(buildA)

        when:
        execute(buildA, "tasks", ["--include-build", "../pluginBuild"])

        then:
        executed ":pluginBuild:jar"
        outputContains("taskFromPluginBuild")

        when:
        includeBuild pluginBuild
        execute(buildA, "tasks")

        then:
        executed ":pluginBuild:jar"
        outputContains("taskFromPluginBuild")
    }

    def addLifecycleTasks(BuildTestFile build) {
        build.buildFile << """
            tasks.maybeCreate("assemble")
            tasks.assemble.dependsOn gradle.includedBuilds*.task(':assemble')
        """
    }

    def addPluginsBlock(BuildTestFile build, String resolutionStrategy = "") {
        build.settingsFile.text = """
            pluginManagement {
                $resolutionStrategy
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
            }
        """ + build.settingsFile.text

        build.buildFile.text = """
            plugins {
                id 'org.test.plugin.pluginBuild' version '1.0'
            }
        """ + build.buildFile.text
    }

}
