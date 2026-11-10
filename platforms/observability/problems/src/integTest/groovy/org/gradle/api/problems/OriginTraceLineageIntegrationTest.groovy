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
        frames*.locality == [Locality.UNKNOWN, Locality.UNKNOWN, Locality.EXTERNAL, Locality.EXTERNAL, Locality.LOCAL, Locality.LOCAL]
    }
}
