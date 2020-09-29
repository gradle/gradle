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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.build.BuildTestFile
import spock.lang.Issue
import spock.lang.Unroll

/**
 * Tests for plugin development scenarios within a composite build.
 */
class CompositeBuildPluginDevelopmentIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile pluginBuild
    BuildTestFile pluginDependencyA

    def setup() {
        pluginDependencyA = singleProjectBuild("pluginDependencyA") {
            buildFile << """
                apply plugin: 'java-library'
                version "2.0"
            """
        }

        pluginBuild = pluginProjectBuild("pluginBuild")
    }

    @Unroll
    @ToBeFixedForConfigurationCache
    def "can co-develop plugin and consumer with plugin as included build #pluginsBlock, #withVersion"() {
        given:
        applyPlugin(buildA, pluginsBlock, withVersion)
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

        where:
        pluginsBlock | withVersion
        true         | true
        true         | false
        false        | true
        false        | false
    }

    def "does not expose Gradle runtime dependencies without shading"() {
        given:
        applyPlugin(buildA, true, false)
        addLifecycleTasks(buildA)

        includeBuild pluginBuild

        buildA.buildFile << """
            import ${com.google.common.collect.ImmutableList.name}
        """

        when:
        fails(buildA, "taskFromPluginBuild")

        then:
        failure.assertHasDescription("Could not compile build file '$buildA.buildFile.canonicalPath'.")
    }

    def "can co-develop plugin and consumer with both plugin and consumer as included builds"() {
        given:
        applyPlugin(pluginDependencyA, pluginsBlock)

        buildA.buildFile << """
            dependencies {
                implementation "org.test:pluginDependencyA:1.0"
            }
        """
        pluginDependencyA.buildFile << """
            tasks.compileJava.dependsOn(tasks.taskFromPluginBuild)
        """

        includeBuild pluginBuild
        includeBuild pluginDependencyA

        when:
        execute(buildA, "assemble")

        then:
        executed ":pluginBuild:jar", ":pluginDependencyA:taskFromPluginBuild", ":pluginDependencyA:compileJava", ":jar"

        where:
        pluginsBlock << [true, false]
    }

    @Issue("https://github.com/gradle/gradle/issues/5234")
    @ToBeFixedForConfigurationCache
    def "can co-develop plugin and multiple consumers as included builds with transitive plugin library dependency"() {
        given:
        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                apply plugin: 'java'
                version "2.0"
            """
        }
        applyPlugin(buildA, pluginsBlock)
        applyPlugin(buildB, pluginsBlock)
        includeBuild pluginBuild
        includeBuild pluginDependencyA
        includeBuild buildB
        dependency(buildA, "org.test:buildB:2.0")
        dependency(pluginBuild, "org.test:pluginDependencyA:1.0")

        when:
        execute(buildA, "assemble")

        then:
        executed ":pluginBuild:jar", ":pluginDependencyA:jar", ":buildB:jar", ":jar"

        where:
        pluginsBlock << [true, false]
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

        applyPlugin(buildA, pluginsBlock)

        includeBuild pluginBuild

        when:
        execute(buildA, "taskFromPluginBuild")

        then:
        executed ":pluginBuild:jar", ":taskFromPluginBuild"

        where:
        pluginsBlock << [true, false]
    }

    @ToBeFixedForConfigurationCache
    def "can develop a transitive plugin dependency as included build"() {
        given:
        applyPlugin(buildA, pluginsBlock)
        dependency(pluginBuild, "org.test:pluginDependencyA:1.0")

        includeBuild pluginBuild
        includeBuild pluginDependencyA

        when:
        execute(buildA, "taskFromPluginBuild")

        then:
        executed ":pluginDependencyA:jar", ":pluginBuild:jar", ":taskFromPluginBuild"

        where:
        pluginsBlock << [true, false]
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

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
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

    @ToBeFixedForConfigurationCache
    def "can co-develop plugin applied via plugins block with resolution strategy applied"() {
        given:
        applyPluginFromRepo(buildA, """
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

    @ToBeFixedForConfigurationCache
    def "can co-develop published plugin applied via plugins block"() {
        given:
        publishPlugin()
        applyPluginFromRepo(buildA)

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

    def "does not substitute plugin from same build into root build"() {
        buildA.settingsFile << """
            include "a", "b"
        """
        buildA.file("a/build.gradle") << """
            plugins { id("java-gradle-plugin") }
            gradlePlugin {
                plugins {
                    broken {
                        id = "a-plugin"
                        implementationClass = "org.test.Broken"
                    }
                }
            }
        """
        buildA.file("b/build.gradle") << """
            plugins {
                id("a-plugin")
            }
        """

        when:
        fails(buildA, "help")

        then:
        failure.assertHasDescription("Plugin [id: 'a-plugin'] was not found in any of the following sources:")
    }

    def "does not substitute plugin from root build into included build"() {
        buildA.settingsFile << """
            include "a"
        """
        buildA.file("a/build.gradle") << """
            plugins { id("java-gradle-plugin") }
            gradlePlugin {
                plugins {
                    broken {
                        id = "a-plugin"
                        implementationClass = "org.test.Broken"
                    }
                }
            }
        """
        pluginBuild.settingsFile << """
            include "b"
        """
        pluginBuild.file("b/build.gradle") << """
            plugins {
                id("a-plugin")
            }
        """

        includeBuild pluginBuild

        when:
        fails(buildA, "help")

        then:
        failure.assertHasDescription("Plugin [id: 'a-plugin'] was not found in any of the following sources:")
    }

    def "does not substitute plugin from same build into included build"() {
        pluginBuild.settingsFile << """
            include "a"
        """
        pluginBuild.file("a/build.gradle") << """
            plugins {
                id("org.test.plugin.pluginBuild")
            }
        """
        includeBuild pluginBuild

        when:
        fails(buildA, "help")

        then:
        failure.assertHasDescription("Plugin [id: 'org.test.plugin.pluginBuild'] was not found in any of the following sources:")
    }

    @Issue("https://github.com/gradle/gradle/issues/14552")
    @ToBeFixedForConfigurationCache
    def "can co-develop plugin with nested consumers using configure-on-demand"() {
        given:
        buildA = multiProjectBuild("cod", ["foo", "foo:bar"])
        includeBuild pluginBuild

        buildA.file("foo/build.gradle") << """
plugins {
    id 'java-library'
    id 'org.test.plugin.pluginBuild'
}
"""
        buildA.file('foo/bar/build.gradle') << """
plugins {
    id 'java-library'
    id 'org.test.plugin.pluginBuild'
}
"""

        when:
        args "--configure-on-demand"
        execute(buildA, ":foo:bar:classes", ":foo:classes")

        then:
        executed ":pluginBuild:jar", ":foo:classes", ":foo:bar:classes"
    }

    def addLifecycleTasks(BuildTestFile build) {
        build.buildFile << """
            tasks.maybeCreate("assemble")
            tasks.assemble.dependsOn gradle.includedBuilds*.task(':assemble')
        """
    }

    def applyPlugin(BuildTestFile build, boolean pluginsBlock = false, boolean withVersion = true) {
        if (pluginsBlock && withVersion) {
            build.buildFile.text = """
                plugins {
                    id 'org.test.plugin.pluginBuild' version '1.0'
                }
            """ + build.buildFile.text
        } else if (pluginsBlock) {
            build.buildFile.text = """
                plugins {
                    id 'org.test.plugin.pluginBuild'
                }
            """ + build.buildFile.text
        } else if (withVersion) {
            build.buildFile << """
                buildscript {
                    dependencies {
                        classpath 'org.test:pluginBuild:1.0'
                    }
                }
                apply plugin: 'org.test.plugin.pluginBuild'
            """
        } else {
            build.buildFile << """
                buildscript {
                    dependencies {
                        classpath 'org.test:pluginBuild:'
                    }
                }
                apply plugin: 'org.test.plugin.pluginBuild'
            """
        }
    }

    def applyPluginFromRepo(BuildTestFile build, String resolutionStrategy = "") {
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
