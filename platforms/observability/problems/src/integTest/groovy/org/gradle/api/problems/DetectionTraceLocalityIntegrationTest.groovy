/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.integtests.fixtures.problems.ReceivedProblem
import org.gradle.internal.code.Locality
import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * Who owns each frame of a detection trace: the locality of plugin applications and of tasks.
 */
class DetectionTraceLocalityIntegrationTest extends AbstractIntegrationSpec {

    private static final String PROBLEM_ID = "problem from spawned thread"

    def setup() {
        enableProblemsApiCheck()
    }

    def "a plugin from buildSrc and the task it registers are local"() {
        given:
        pluginInBuildSrc("com.acme.foo", "com.acme.AcmePlugin")
        buildFile """
            plugins {
                id "com.acme.foo"
            }
        """

        when:
        run("acme")

        then:
        localitiesOf(atApplication()) == [
            "plugin application com.acme.foo": Locality.LOCAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
        localitiesOf(inTask()) == [
            "task :acme": Locality.LOCAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
    }

    def "a plugin applied by its class is named by the class and keeps its locality"() {
        given:
        pluginInBuildSrc("com.acme.foo", "com.acme.AcmePlugin")
        buildFile """
            apply plugin: com.acme.AcmePlugin
        """

        when:
        run("acme")

        then:
        localitiesOf(atApplication()) == [
            "plugin application class com.acme.AcmePlugin": Locality.LOCAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
        localitiesOf(inTask()) == [
            "task :acme": Locality.LOCAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
    }

    def "only the innermost plugin application is part of the trace"() {
        given:
        pluginInBuildSrc("com.acme.outer", "com.acme.OuterPlugin")
        file("buildSrc/src/main/groovy/com/acme/OuterPlugin.groovy").text = """
            package com.acme

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class OuterPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.pluginManager.apply(AcmePlugin)
                }
            }
        """
        file("buildSrc/src/main/groovy/com/acme/AcmePlugin.groovy") << pluginSource("com.acme", "AcmePlugin")
        buildFile """
            plugins {
                id "com.acme.outer"
            }
        """

        when:
        run("acme")

        then:
        localitiesOf(atApplication()) == [
            "plugin application class com.acme.AcmePlugin": Locality.LOCAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
        localitiesOf(inTask()) == [
            "task :acme": Locality.LOCAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
    }

    def "a plugin from a repository and the task it registers are external"() {
        given:
        def pluginBuilder = new PluginBuilder(file("acme-plugin"))
        pluginBuilder.packageName = "com.acme"
        pluginBuilder.addPluginSource("com.acme.foo", "AcmePlugin", pluginSource("com.acme", "AcmePlugin"))
        pluginBuilder.publishAs("com.acme", "acme-plugin", "1.0", mavenRepo, createExecuter())
        settingsFile """
            pluginManagement {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
            }
        """
        buildFile """
            plugins {
                id "com.acme.foo" version "1.0"
            }
        """

        when:
        run("acme")

        then:
        localitiesOf(atApplication()) == [
            "plugin application com.acme.foo": Locality.EXTERNAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
        localitiesOf(inTask()) == [
            "task :acme": Locality.EXTERNAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
    }

    def "a task registered by a core plugin is core"() {
        given:
        buildFile """
            plugins {
                id "java"
            }
        """
        file("src/main/java/Foo.java") << """
            public class Foo {
                void broken() {
                    int x = ;
                }
            }
        """

        when:
        fails("compileJava")

        then:
        def problem = findReceivedProblem { it.fqid.startsWith("Compilation:Java:") }
        localitiesOf(problem) == [
            "task :compileJava": Locality.CORE,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
    }

    def "a task registered by a build script is local"() {
        given:
        buildFile """
            $reportingTask
            tasks.register("acme", ProblemReportingTask)
        """

        when:
        run("acme")

        then:
        localitiesOf(inTask()) == [
            "task :acme": Locality.LOCAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
    }

    def "a task registered by an init script from the Gradle user home belongs to the environment"() {
        given:
        executer.requireOwnGradleUserHomeDir("init scripts are read from it")
        executer.gradleUserHomeDir.file("init.d/acme.gradle") << initScript

        when:
        run("acme")

        then:
        localitiesOf(inTask()) == [
            "task :acme": Locality.ENVIRONMENT,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
    }

    def "a task registered by an init script passed on the command line is unknown"() {
        given:
        file("acme.gradle") << initScript

        when:
        run("acme", "--init-script", "acme.gradle")

        then:
        localitiesOf(inTask()) == [
            "task :acme": Locality.UNKNOWN,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
    }

    def "a task keeps the locality of its plugin on a configuration cache hit"() {
        given:
        pluginInBuildSrc("com.acme.foo", "com.acme.AcmePlugin")
        buildFile """
            plugins {
                id "com.acme.foo"
            }
        """
        def configurationCache = new ConfigurationCacheFixture(this)
        run("acme", "--configuration-cache")

        when:
        run("acme", "--configuration-cache")

        then:
        configurationCache.assertStateLoaded()
        localitiesOf(inTask()) == [
            "task :acme": Locality.LOCAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
    }

    def "a plugin class defined in a build script is local like the script, and so is the task it registers"() {
        given:
        buildFile """
            $reportingTask

            abstract class ScriptPlugin implements Plugin<Project> {
                @Inject
                abstract Problems getProblems()

                void apply(Project project) {
                    problems.reporter.report(problems.groups.others.group("Generic").problemId("$PROBLEM_ID")) { it.details("at application") }
                    project.tasks.register("acme", ProblemReportingTask)
                }
            }

            apply plugin: ScriptPlugin
        """

        when:
        run("acme")

        then:
        localitiesOf(atApplication()) == [
            "plugin application class ScriptPlugin": Locality.LOCAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
        localitiesOf(inTask()) == [
            "task :acme": Locality.LOCAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
    }

    def "a plugin class defined in an init script takes the locality of that script"() {
        given:
        executer.requireOwnGradleUserHomeDir("init scripts are read from it")
        executer.gradleUserHomeDir.file("init.d/acme.gradle") << """
            import org.gradle.api.problems.Problems
            import javax.inject.Inject

            abstract class InitScriptPlugin implements Plugin<Gradle> {
                @Inject
                abstract Problems getProblems()

                void apply(Gradle gradle) {
                    problems.reporter.report(problems.groups.others.group("Generic").problemId("$PROBLEM_ID")) { it.details("at application") }
                }
            }

            apply plugin: InitScriptPlugin
        """

        when:
        run("help")

        then: "an init script from the Gradle user home belongs to the environment, and so does the class it defines"
        localitiesOf(atApplication())["plugin application class InitScriptPlugin"] == Locality.ENVIRONMENT
    }

    def "a plugin from a jar added with files() and the task it registers are external"() {
        given:
        def pluginBuilder = new PluginBuilder(file("acme-plugin"))
        pluginBuilder.packageName = "com.acme"
        pluginBuilder.addPluginSource("com.acme.foo", "AcmePlugin", pluginSource("com.acme", "AcmePlugin"))
        pluginBuilder.publishTo(createExecuter(), file("libs/acme-plugin.jar"))
        buildFile """
            buildscript {
                dependencies {
                    classpath files("libs/acme-plugin.jar")
                }
            }
            apply plugin: "com.acme.foo"
        """

        when:
        run("acme")

        then:
        localitiesOf(atApplication()) == [
            "plugin application com.acme.foo": Locality.EXTERNAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
        localitiesOf(inTask()) == [
            "task :acme": Locality.EXTERNAL,
            "project :": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
    }

    def "a settings plugin applied to an included build is detected in that build"() {
        given:
        def pluginBuilder = new PluginBuilder(file("acme-settings-plugin"))
        pluginBuilder.packageName = "com.acme"
        pluginBuilder.addPluginSource("com.acme.settings", "AcmeSettingsPlugin", """
            package com.acme

            import org.gradle.api.Plugin
            import org.gradle.api.initialization.Settings
            import org.gradle.api.problems.Problems
            import javax.inject.Inject

            abstract class AcmeSettingsPlugin implements Plugin<Settings> {
                @Inject
                abstract Problems getProblems()

                void apply(Settings settings) {
                    problems.reporter.report(problems.groups.others.group("Generic").problemId("$PROBLEM_ID")) { it.details("at application") }
                }
            }
        """)
        pluginBuilder.publishAs("com.acme", "acme-settings-plugin", "1.0", mavenRepo, createExecuter())
        def pluginManagement = """
            pluginManagement {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
            }
        """
        settingsFile """
            $pluginManagement
            includeBuild "lib"
        """
        file("lib/settings.gradle") << """
            $pluginManagement
            plugins {
                id "com.acme.settings" version "1.0"
            }
            rootProject.name = "lib"
        """

        when:
        run("help")

        then:
        atApplication().detectionTrace.frames*.displayName == [
            "plugin application com.acme.settings",
            "build :lib",
            "build :"
        ]
    }

    def "a plugin applied by an init script is detected in each build the script runs for"() {
        given:
        settingsFile """
            includeBuild "lib"
        """
        file("lib/settings.gradle") << """
            rootProject.name = "lib"
        """
        def initScript = file("acme-init.gradle")
        initScript << """
            import org.gradle.api.problems.Problems
            import javax.inject.Inject

            abstract class InitScriptPlugin implements Plugin<Gradle> {
                @Inject
                abstract Problems getProblems()

                void apply(Gradle gradle) {
                    // The build's path makes the two reports distinct problems, so neither is dropped as a repeat
                    problems.reporter.report(problems.groups.others.group("Generic").problemId("$PROBLEM_ID")) { it.details("in build " + gradle.identityPath) }
                }
            }

            apply plugin: InitScriptPlugin
        """

        when: "the init script runs once for the root build and once for the included build"
        executer.usingInitScript(initScript)
        run("help")

        then:
        findReceivedProblem { it.details == "in build :" }.detectionTrace.frames*.displayName == [
            "plugin application class InitScriptPlugin",
            "build :"
        ]
        findReceivedProblem { it.details == "in build :lib" }.detectionTrace.frames*.displayName == [
            "plugin application class InitScriptPlugin",
            "build :lib",
            "build :"
        ]
    }

    def "a settings plugin from a repository is external"() {
        given:
        def pluginBuilder = new PluginBuilder(file("acme-settings-plugin"))
        pluginBuilder.packageName = "com.acme"
        pluginBuilder.addPluginSource("com.acme.settings", "AcmeSettingsPlugin", settingsPluginSource("com.acme", "AcmeSettingsPlugin"))
        pluginBuilder.publishAs("com.acme", "acme-settings-plugin", "1.0", mavenRepo, createExecuter())
        settingsFile """
            pluginManagement {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
            }
            plugins {
                id "com.acme.settings" version "1.0"
            }
        """

        when:
        run("help")

        then:
        localitiesOf(atApplication()) == [
            "plugin application com.acme.settings": Locality.EXTERNAL,
            "build :": Locality.LOCAL
        ]
    }

    def "a settings plugin from an included build is local"() {
        given:
        file("build-logic/settings.gradle") << """
            rootProject.name = "build-logic"
        """
        file("build-logic/build.gradle") << """
            plugins {
                id "groovy-gradle-plugin"
            }
            gradlePlugin {
                plugins {
                    acme {
                        id = "com.acme.settings"
                        implementationClass = "com.acme.AcmeSettingsPlugin"
                    }
                }
            }
        """
        file("build-logic/src/main/groovy/com/acme/AcmeSettingsPlugin.groovy") << settingsPluginSource("com.acme", "AcmeSettingsPlugin")
        settingsFile """
            pluginManagement {
                includeBuild "build-logic"
            }
            plugins {
                id "com.acme.settings"
            }
        """

        when:
        run("help")

        then:
        localitiesOf(atApplication()) == [
            "plugin application com.acme.settings": Locality.LOCAL,
            "build :": Locality.LOCAL
        ]
    }

    def "a plugin from a repository on the classpath of an init script is external"() {
        given:
        def pluginBuilder = new PluginBuilder(file("acme-init-plugin"))
        pluginBuilder.packageName = "com.acme"
        pluginBuilder.addPluginSource("com.acme.init", "AcmeInitPlugin", """
            package com.acme

            import org.gradle.api.Plugin
            import org.gradle.api.invocation.Gradle
            import org.gradle.api.problems.Problems
            import javax.inject.Inject

            abstract class AcmeInitPlugin implements Plugin<Gradle> {
                @Inject
                abstract Problems getProblems()

                void apply(Gradle gradle) {
                    problems.reporter.report(problems.groups.others.group("Generic").problemId("$PROBLEM_ID")) { it.details("at application") }
                }
            }
        """)
        pluginBuilder.publishAs("com.acme", "acme-init-plugin", "1.0", mavenRepo, createExecuter())
        def initScript = file("acme-init.gradle")
        initScript << """
            initscript {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                dependencies {
                    classpath "com.acme:acme-init-plugin:1.0"
                }
            }

            apply plugin: com.acme.AcmeInitPlugin
        """

        when:
        executer.usingInitScript(initScript)
        run("help")

        then: "the plugin is external whoever the init script belongs to: its jar came from a repository"
        localitiesOf(atApplication())["plugin application class com.acme.AcmeInitPlugin"] == Locality.EXTERNAL
    }

    private static String settingsPluginSource(String packageName, String className) {
        """
            package $packageName

            import org.gradle.api.Plugin
            import org.gradle.api.initialization.Settings
            import org.gradle.api.problems.Problems
            import javax.inject.Inject

            abstract class $className implements Plugin<Settings> {
                @Inject
                abstract Problems getProblems()

                void apply(Settings settings) {
                    problems.reporter.report(problems.groups.others.group("Generic").problemId("$PROBLEM_ID")) { it.details("at application") }
                }
            }
        """
    }

    /**
     * A plugin that reports a problem as it is applied and registers task {@code acme}, which reports one too.
     */
    private static String pluginSource(String packageName, String className) {
        """
            package $packageName

            import org.gradle.api.DefaultTask
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.problems.Problems
            import org.gradle.api.tasks.TaskAction
            import javax.inject.Inject

            abstract class $className implements Plugin<Project> {
                @Inject
                abstract Problems getProblems()

                void apply(Project project) {
                    problems.reporter.report(problems.groups.others.group("Generic").problemId("$PROBLEM_ID")) { it.details("at application") }
                    project.tasks.register("acme", AcmeTask)
                }
            }

            abstract class AcmeTask extends DefaultTask {
                @Inject
                abstract Problems getProblems()

                @TaskAction
                void run() {
                    problems.reporter.report(problems.groups.others.group("Generic").problemId("$PROBLEM_ID")) { it.details("in task") }
                }
            }
        """
    }

    private void pluginInBuildSrc(String id, String implementationClass) {
        def packageName = implementationClass.substring(0, implementationClass.lastIndexOf('.'))
        def className = implementationClass.substring(implementationClass.lastIndexOf('.') + 1)
        file("buildSrc/build.gradle") << """
            plugins {
                id "groovy-gradle-plugin"
            }
            gradlePlugin {
                plugins {
                    acme {
                        id = "$id"
                        implementationClass = "$implementationClass"
                    }
                }
            }
        """
        file("buildSrc/src/main/groovy/${packageName.replace('.', '/')}/${className}.groovy") << pluginSource(packageName, className)
    }

    private static String getReportingTask() {
        """
            import org.gradle.api.problems.Problems
            import javax.inject.Inject

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                abstract Problems getProblems()

                @TaskAction
                void run() {
                    problems.reporter.report(problems.groups.others.group("Generic").problemId("$PROBLEM_ID")) { it.details("in task") }
                }
            }
        """
    }

    private static String getInitScript() {
        """
            $reportingTask
            allprojects {
                tasks.register("acme", ProblemReportingTask)
            }
        """
    }

    private ReceivedProblem atApplication() {
        findReceivedProblem { it.fqid == "Others:Generic:$PROBLEM_ID" && it.details == "at application" }
    }

    private ReceivedProblem inTask() {
        findReceivedProblem { it.fqid == "Others:Generic:$PROBLEM_ID" && it.details == "in task" }
    }

    private static Map<String, Locality> localitiesOf(ReceivedProblem problem) {
        problem.detectionTrace.frames.collectEntries { [(it.displayName): it.locality] }
    }
}
