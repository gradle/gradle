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
import org.gradle.internal.code.Locality
import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * The lineage above a validation problem's leaf (UC2): the task, the plugin that registered it, the plugin that applied that one, and the script.
 */
class OriginTraceLineageIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
    }

    def "a validation problem leads from the property through the task to the plugins and the script that brought it in"() {
        given: "a third-party plugin from a repository that registers a task of a type with an unannotated property"
        def pluginBuilder = new PluginBuilder(file("acme-plugin"))
        pluginBuilder.packageName = "com.acme"
        pluginBuilder.addPluginSource("com.acme.foo", "AcmePlugin", """
            package com.acme

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class AcmePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register("acme", AcmeTask)
                }
            }
        """)
        pluginBuilder.groovy("AcmeTask.groovy") << """
            package com.acme

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class AcmeTask extends DefaultTask {
                String bar

                @TaskAction
                void run() {}
            }
        """
        pluginBuilder.publishAs("com.acme", "acme-plugin", "1.0", mavenRepo, createExecuter())

        and: "a local convention plugin that applies it"
        file("buildSrc/build.gradle") << """
            plugins {
                id "groovy-gradle-plugin"
            }
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            dependencies {
                implementation "com.acme:acme-plugin:1.0"
            }
            gradlePlugin {
                plugins {
                    convention {
                        id = "com.acme.convention"
                        implementationClass = "com.acme.convention.ConventionPlugin"
                    }
                }
            }
        """
        file("buildSrc/src/main/groovy/com/acme/convention/ConventionPlugin.groovy") << """
            package com.acme.convention

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class ConventionPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.pluginManager.apply("com.acme.foo")
                }
            }
        """
        settingsFile """
            pluginManagement {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
            }
            include "app"
        """
        file("app/build.gradle") << """
            plugins {
                id "com.acme.convention"
            }
        """

        when:
        fails(":app:acme")

        then:
        def problem = findReceivedProblem { it.fqid == "Gradle:Plugin Validation:Missing annotation" }
        def frames = problem.originTraces[0].frames
        frames*.displayName == [
            "property bar",
            "class com.acme.AcmeTask",
            "task :app:acme",
            "plugin com.acme.foo",
            "plugin com.acme.convention",
            file("app/build.gradle").absolutePath,
        ]
        frames*.locality == [
            Locality.UNKNOWN,
            Locality.UNKNOWN,
            Locality.EXTERNAL,
            Locality.EXTERNAL,
            Locality.LOCAL,
            Locality.LOCAL
        ]
    }

    def "a task type that is a nested class gets its lineage"() {
        given:
        file("buildSrc/src/main/java/com/acme/Tasks.java") << """
            package com.acme;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.TaskAction;

            public class Tasks {
                public static class AcmeTask extends DefaultTask {
                    private String bar;

                    public String getBar() { return bar; }
                    public void setBar(String bar) { this.bar = bar; }

                    @TaskAction
                    public void run() {}
                }
            }
        """
        buildFile """
            tasks.register("acme", com.acme.Tasks.AcmeTask)
        """

        when:
        fails("acme")

        then:
        def problem = findReceivedProblem { it.fqid == "Gradle:Plugin Validation:Missing annotation" }
        problem.originTraces[0].frames*.displayName == [
            "property bar",
            "class com.acme.Tasks.AcmeTask",
            "task :acme",
            buildFile.absolutePath
        ]
    }

    def "a property of a nested bean gets the lineage of its task"() {
        given:
        file("buildSrc/src/main/java/com/acme/Options.java") << """
            package com.acme;

            public class Options {
                private String bar;

                public String getBar() { return bar; }
                public void setBar(String bar) { this.bar = bar; }
            }
        """
        file("buildSrc/src/main/java/com/acme/AcmeTask.java") << """
            package com.acme;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.Nested;
            import org.gradle.api.tasks.TaskAction;

            public class AcmeTask extends DefaultTask {
                private final Options options = new Options();

                @Nested
                public Options getOptions() { return options; }

                @TaskAction
                public void run() {}
            }
        """
        buildFile """
            tasks.register("acme", com.acme.AcmeTask)
        """

        when:
        fails("acme")

        then:
        def problem = findReceivedProblem { it.fqid == "Gradle:Plugin Validation:Missing annotation" }
        problem.originTraces[0].frames*.displayName == [
            "property options.bar",
            "class com.acme.AcmeTask",
            "task :acme",
            buildFile.absolutePath
        ]
    }

    def "the lineage follows who registered the task, not which plugin ships its type"() {
        given: "an applied third-party plugin that ships a task type and registers one task of it"
        publishAcmePlugin()
        buildSrcWithAcmePluginAnd("")

        and: "a build script that registers a second task of that type itself"
        buildFile """
            plugins {
                id "com.acme.foo"
            }

            tasks.register("mine", com.acme.AcmeTask)
        """

        when: "the task the plugin registered runs"
        fails("acme")

        then: "the plugin is part of the lineage"
        def fromPlugin = findReceivedProblem { it.fqid == "Gradle:Plugin Validation:Missing annotation" }.originTraces[0].frames
        fromPlugin*.displayName == [
            "property bar",
            "class com.acme.AcmeTask",
            "task :acme",
            "plugin com.acme.foo",
            buildFile.absolutePath
        ]
        fromPlugin*.locality == [
            Locality.UNKNOWN,
            Locality.UNKNOWN,
            Locality.EXTERNAL,
            Locality.EXTERNAL,
            Locality.LOCAL
        ]

        when: "the task the build script registered runs"
        fails("mine")

        then: "the same type leads to the script alone, though the plugin's jar holds the class"
        def fromScript = findReceivedProblem { it.fqid == "Gradle:Plugin Validation:Missing annotation" }.originTraces[0].frames
        fromScript*.displayName == [
            "property bar",
            "class com.acme.AcmeTask",
            "task :mine",
            buildFile.absolutePath
        ]
        fromScript*.locality == [
            Locality.UNKNOWN,
            Locality.UNKNOWN,
            Locality.LOCAL,
            Locality.LOCAL
        ]
    }

    def "the lineage names the plugin that registered the task when the plugin that ships its type is never applied"() {
        given: "a third-party jar whose plugin nobody applies"
        publishAcmePlugin()

        and: "a local plugin that registers a task of a type from that jar"
        buildSrcWithAcmePluginAnd("""
            gradlePlugin {
                plugins {
                    convention {
                        id = "com.acme.convention"
                        implementationClass = "com.acme.convention.ConventionPlugin"
                    }
                }
            }
        """)
        file("buildSrc/src/main/groovy/com/acme/convention/ConventionPlugin.groovy") << """
            package com.acme.convention

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class ConventionPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register("conventional", com.acme.AcmeTask)
                }
            }
        """
        buildFile """
            plugins {
                id "com.acme.convention"
            }
        """

        when:
        fails("conventional")

        then: "the class comes from the third-party jar, the lineage from the local plugin"
        def frames = findReceivedProblem { it.fqid == "Gradle:Plugin Validation:Missing annotation" }.originTraces[0].frames
        frames*.displayName == [
            "property bar",
            "class com.acme.AcmeTask",
            "task :conventional",
            "plugin com.acme.convention",
            buildFile.absolutePath
        ]
        frames*.locality == [
            Locality.UNKNOWN,
            Locality.UNKNOWN,
            Locality.LOCAL,
            Locality.LOCAL,
            Locality.LOCAL
        ]
    }

    private void publishAcmePlugin() {
        def pluginBuilder = new PluginBuilder(file("acme-plugin"))
        pluginBuilder.packageName = "com.acme"
        pluginBuilder.addPluginSource("com.acme.foo", "AcmePlugin", """
            package com.acme

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class AcmePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register("acme", AcmeTask)
                }
            }
        """)
        pluginBuilder.groovy("AcmeTask.groovy") << """
            package com.acme

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class AcmeTask extends DefaultTask {
                String bar

                @TaskAction
                void run() {}
            }
        """
        pluginBuilder.publishAs("com.acme", "acme-plugin", "1.0", mavenRepo, createExecuter())
    }

    private void buildSrcWithAcmePluginAnd(String more) {
        file("buildSrc/build.gradle") << """
            plugins {
                id "groovy-gradle-plugin"
            }
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
            dependencies {
                implementation "com.acme:acme-plugin:1.0"
            }
            $more
        """
    }
}
