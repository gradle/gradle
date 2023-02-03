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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
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
                apply plugin: 'java-library'
                version "2.0"
            """
        }

        pluginBuild = pluginProjectBuild("pluginBuild")
    }

    def "can co-develop plugin and consumer with plugin as included build withVersion: #withVersion"() {
        given:
        applyPlugin(buildA, true, withVersion)
        addLifecycleTasks(buildA)

        includePluginBuild pluginBuild

        when:
        execute(buildA, "taskFromPluginBuild")

        then:
        executed ":pluginBuild:jar", ":taskFromPluginBuild"

        when:
        execute(buildA, "assemble")

        then:
        executed ":pluginBuild:jar", ":pluginBuild:assemble", ":assemble"

        where:
        withVersion << [true, false]
    }

    def "can co-develop plugin and consumer with plugin as included library build using 'apply plugin', withVersion: #withVersion"() {
        given:
        applyPlugin(buildA, false, withVersion)
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
        withVersion << [true, false]
    }

    def "does not expose Gradle runtime dependencies without shading"() {
        given:
        applyPlugin(buildA, true, false)
        addLifecycleTasks(buildA)

        includePluginBuild pluginBuild

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
        applyPlugin(pluginDependencyA, true)

        buildA.buildFile << """
            dependencies {
                implementation "org.test:pluginDependencyA:1.0"
            }
        """
        pluginDependencyA.buildFile << """
            tasks.compileJava.dependsOn(tasks.taskFromPluginBuild)
        """
        pluginDependencyA.settingsFile.text = """
            pluginManagement {
                includeBuild("${pluginBuild.toURI()}")
            }
            ${pluginDependencyA.settingsFile.text}
        """

        includeBuild pluginDependencyA

        when:
        execute(buildA, "assemble")

        then:
        executed ":pluginBuild:jar", ":pluginDependencyA:taskFromPluginBuild", ":pluginDependencyA:compileJava", ":jar"
    }

    def "can co-develop plugin and consumer with both plugin and consumer as included builds using 'apply plugin' and library included build"() {
        given:
        applyPlugin(pluginDependencyA, false)

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
        buildA.settingsFile.text = """
            pluginManagement {
                includeBuild("${pluginBuild.toURI()}")
            }
            ${buildA.settingsFile.text}
        """
        applyPlugin(buildA, true)
        buildB.settingsFile.text = """
            pluginManagement {
                includeBuild("${pluginBuild.toURI()}")
            }
            includeBuild("${pluginDependencyA.toURI()}")
            ${buildB.settingsFile.text}
        """
        applyPlugin(buildB, true)
        includeBuild buildB
        dependency(buildA, "org.test:buildB:2.0")
        dependency(pluginBuild, "org.test:pluginDependencyA:1.0")

        when:
        execute(buildA, "assemble")

        then:
        executed ":pluginBuild:jar", ":pluginDependencyA:jar", ":buildB:jar", ":jar"
    }

    @IntegrationTestTimeout(value = 30, onlyIf = { GradleContextualExecuter.embedded })
    def "can co-develop plugin and multiple consumers as included builds with transitive plugin library dependency using library included build and 'apply plugin'"() {
        given:
        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                apply plugin: 'java'
                version "2.0"
            """
        }
        applyPlugin(buildA, false)
        applyPlugin(buildB, false)
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

        applyPlugin(buildA, true)

        includePluginBuild pluginBuild

        when:
        execute(buildA, "taskFromPluginBuild")

        then:
        executed ":pluginBuild:jar", ":taskFromPluginBuild"
    }

    def "can co-develop plugin and consumer where plugin uses previous version of itself to build using library included build and 'apply plugin'"() {
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

        applyPlugin(buildA, false)

        includeBuild pluginBuild

        when:
        execute(buildA, "taskFromPluginBuild")

        then:
        executed ":pluginBuild:jar", ":taskFromPluginBuild"
    }

    def "can develop a transitive plugin dependency as included build"() {
        given:
        applyPlugin(buildA, true)
        dependency(pluginBuild, "org.test:pluginDependencyA:1.0")

        includePluginBuild pluginBuild
        includeBuild pluginDependencyA

        when:
        execute(buildA, "taskFromPluginBuild")

        then:
        executed ":pluginDependencyA:jar", ":pluginBuild:jar", ":taskFromPluginBuild"
    }

    def "can develop a transitive plugin dependency as included library build and 'apply plugin'"() {
        given:
        applyPlugin(buildA, false)
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
        failure.assertHasCause("""
Circular dependency between the following tasks:
:pluginDependencyA:compileJava
\\--- :pluginDependencyB:jar
     +--- :pluginDependencyB:classes
     |    \\--- :pluginDependencyB:compileJava
     |         \\--- :pluginDependencyA:compileJava (*)
     \\--- :pluginDependencyB:compileJava (*)

(*) - details omitted (listed previously)
""".trim())
    }

    def "can co-develop plugin applied via plugins block with resolution strategy applied"() {
        given:
        applyPluginFromRepo(buildA, """
            includeBuild('${pluginBuild.toURI()}')
            resolutionStrategy.eachPlugin {
                if(requested.id.name == 'pluginBuild') {
                    useModule('org.test:pluginBuild:1.0')
                }
            }
        """)
        when:
        execute(buildA, "tasks")

        then:
        executed ":pluginBuild:jar"
        outputContains("taskFromPluginBuild")
    }

    def "can co-develop plugin applied via plugins block with resolution strategy applied with build included from command line"() {
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
    }

    def "can co-develop published plugin applied via plugins block"() {
        given:
        publishPlugin()
        applyPluginFromRepo(buildA, "includeBuild('${pluginBuild.toURI()}')")

        when:
        execute(buildA, "tasks")

        then:
        executed ":pluginBuild:jar"
        outputContains("taskFromPluginBuild")
    }

    def "can co-develop published plugin applied via plugins block with build included from command line"() {
        given:
        publishPlugin()
        applyPluginFromRepo(buildA)

        when:
        execute(buildA, "tasks", ["--include-build", "../pluginBuild"])

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
    def "can co-develop plugin with nested consumers using configure-on-demand"() {
        given:
        buildA = multiProjectBuild("cod", ["foo", "foo:bar"])
        includePluginBuild pluginBuild

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

    def "can develop a plugin with multiple consumers when those consumers are accessed via undeclared dependency resolution and using configure-on-demand"() {
        given:
        buildA = multiProjectBuild("cod", ["a", "b", "c", "d"])
        includePluginBuild pluginBuild

        buildA.file("a/build.gradle") << """
plugins {
    id 'java-library'
    id 'org.test.plugin.pluginBuild'
}
"""
        buildA.file('b/build.gradle') << """
plugins {
    id 'java-library'
    id 'org.test.plugin.pluginBuild'
}
"""
        buildA.file("c/build.gradle") << """
plugins {
    id 'java-library'
}
dependencies {
    implementation project(':a')
}
task resolve {
    def compileClasspath = configurations.compileClasspath
    doLast {
        compileClasspath.files
    }
}
"""
        buildA.file("d/build.gradle") << """
plugins {
    id 'java-library'
}
dependencies {
    implementation project(':b')
}
task resolve {
    def compileClasspath = configurations.compileClasspath
    doLast {
        compileClasspath.files
    }
}
"""

        when:
        args "--configure-on-demand", "--parallel"
        execute(buildA, ":c:resolve", ":d:resolve")

        then:
        noExceptionThrown()

        where:
        iterations << (0..20).collect()
    }

    @Issue("https://github.com/gradle/gradle/issues/15068")
    def "can develop plugin whose build requires dependency resolution using configure-on-demand"() {
        given:
        buildA = multiProjectBuild("cod", ["consumer"]) {
            project("consumer").buildFile.text = """
                plugins {
                    id 'java-library'
                    id 'org.test.plugin.plugin'
                }
            """
        }
        def pluginBuild = multiProjectBuild("build-logic", ["plugin"]) {
            buildFile.text = """
                plugins {
                    id("java-library")
                }
                allprojects {
                    group = "org.test.plugin"
                }
            """
            def subproject = project("plugin")
            pluginProjectBuild(subproject)
            subproject.buildFile << """
                dependencies {
                    implementation project(":")
                }
            """
        }

        includePluginBuild pluginBuild

        when:
        args "--configure-on-demand"
        execute(buildA, "classes")

        then:
        executed ":build-logic:jar", ":build-logic:plugin:jar", ":consumer:classes"
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
                id 'org.test.plugin.pluginBuild' version '1.1'
            }
        """ + build.buildFile.text
    }

}
