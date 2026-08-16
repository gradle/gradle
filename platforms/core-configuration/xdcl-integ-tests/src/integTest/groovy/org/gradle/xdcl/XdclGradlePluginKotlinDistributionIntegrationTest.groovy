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

package org.gradle.xdcl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.test.fixtures.archive.JarTestFixture

/**
 * The Kotlin face of the built-in plugin-development ecosystem, end-to-end against a real
 * distribution (forking). {@code xdclGradlePluginKotlin { }} needs a Kotlin toolchain the
 * distribution does not ship, so the settings put one on the build classpath — either through the
 * {@code embedded-kotlin} front-end alias (id and version desugared by the provider) or the
 * official {@code org.jetbrains.kotlin.jvm} plugin at an explicit version — and the reaction
 * applies whichever is there.
 *
 * <p>These tests resolve the kotlin-dsl plugins bundle from the (mirrored) plugin portal and the
 * Kotlin artifacts from the (mirrored) Maven Central — the same repositories any build applying a
 * Kotlin plugin uses.
 */
class XdclGradlePluginKotlinDistributionIntegrationTest extends AbstractIntegrationSpec {

    private static String settingsWithToolchain(String toolchainRequest) {
        """
            settings {
              plugins [
                { id "plugin-development-ecosystem" }
                $toolchainRequest
              ]
              rootProject { name "demo-plugin" }
            }
        """
    }

    private void kotlinPluginProject() {
        file('build.gradle.xdcl') << """
            xdclGradlePluginKotlin {
              repositories ["${RepoScriptBlockUtil.mavenCentralMirrorUrl}"]
            }
        """
        file('src/main/xdcl/demo.xdsl') << '''
            package com.example.demo

            template Demo {
              demo {
                name: String
              }
            }
        '''.stripIndent()
        file('src/main/xdcl/com.example.demo.xdcl') << '''
            plugin {
              reactions [ :com.example.demo.DemoReaction ]
            }
        '''.stripIndent()
        file('src/main/kotlin/com/example/demo/DemoReaction.kt') << '''
            package com.example.demo

            import org.gradle.api.Project
            import org.gradle.api.xdcl.Reaction
            import org.gradle.api.xdcl.ReactionScope

            class DemoReaction : Reaction<Demo, Project> {
                override fun on(data: Demo, project: Project, scope: ReactionScope) {
                    println("demo-reaction-name=" + data.name().get() + " in " + project.name)
                }
            }
        '''.stripIndent()
    }

    private void assertPluginJar() {
        def jar = new JarTestFixture(file('build/libs/demo-plugin.jar'))
        jar.assertContainsFile('META-INF/xdcl/demo.xdsl')
        jar.assertContainsFile('com/example/demo/Demo.class')
        jar.assertContainsFile('com/example/demo/DemoReaction.class')
        jar.assertContainsFile('xdcl/generated/plugins/ComExampleDemoPlugin.class')
        jar.assertContainsFile('META-INF/gradle-plugins/com.example.demo.properties')
    }

    def "builds a Kotlin XDCL plugin jar with the embedded toolchain supplied by the settings alias"() {
        given: 'the alias carries no version — the front end fills in the one the distribution expects'
        file('settings.gradle.xdcl') << settingsWithToolchain('{ id "embedded-kotlin", apply false }')
        kotlinPluginProject()

        when:
        succeeds("jar")

        then: 'codegen generated the facade (javac) and the Kotlin reaction compiled (kotlinc)'
        executedAndNotSkipped(":xdclCodegen", ":compileKotlin", ":jar")
        assertPluginJar()
    }

    def "builds a Kotlin XDCL plugin jar with the official Kotlin plugin at an explicit version"() {
        given:
        def kotlinVersion = new KotlinGradlePluginVersions().latestStable
        file('settings.gradle.xdcl') << settingsWithToolchain(
            "{ id \"org.jetbrains.kotlin.jvm\", version \"$kotlinVersion\", apply false }")
        kotlinPluginProject()

        when:
        succeeds("jar")

        then: 'the reaction adapted to the toolchain that was on the classpath'
        executedAndNotSkipped(":xdclCodegen", ":compileKotlin", ":jar")
        assertPluginJar()
    }

    def "an included build-logic build authored with xdclGradlePluginKotlin supplies a plugin consumed by a declarative root build"() {
        given: 'an included build-logic build that is itself declarative, with a Kotlin reaction'
        file('build-logic/settings.gradle.xdcl') << '''
            settings {
              plugins [
                { id "plugin-development-ecosystem" }
                { id "embedded-kotlin", apply false }
              ]
            }
        '''
        file('build-logic/build.gradle.xdcl') << """
            xdclGradlePluginKotlin {
              repositories ["${RepoScriptBlockUtil.mavenCentralMirrorUrl}"]
            }
        """
        file('build-logic/src/main/xdcl/demo.xdsl') << '''
            package com.example.demo

            template Demo {
              demo {
                name: String
              }
            }
        '''.stripIndent()
        file('build-logic/src/main/xdcl/com.example.demo.xdcl') << '''
            plugin {
              reactions [ :com.example.demo.DemoReaction ]
            }
        '''.stripIndent()
        file('build-logic/src/main/kotlin/com/example/demo/DemoReaction.kt') << '''
            package com.example.demo

            import org.gradle.api.Project
            import org.gradle.api.xdcl.Reaction
            import org.gradle.api.xdcl.ReactionScope

            class DemoReaction : Reaction<Demo, Project> {
                override fun on(data: Demo, project: Project, scope: ReactionScope) {
                    println("demo-reaction-name=" + data.name().get() + " in " + project.name)
                }
            }
        '''.stripIndent()

        and: 'a declarative root build consuming the plugin from the included build'
        file('settings.gradle.xdcl') << '''
            settings {
              pluginManagement {
                includedBuilds ["build-logic"]
              }
              plugins [
                { id "com.example.demo" }
              ]
              rootProject { name "consumer" }
            }
        '''
        file('build.gradle.xdcl') << '''
            demo {
              name "hello-from-kotlin"
            }
        '''

        when:
        succeeds("help")

        then: 'the Kotlin-built plugin resolved the template and its reaction fired'
        outputContains("demo-reaction-name=hello-from-kotlin in consumer")
    }

    def "an explicit version on the embedded-kotlin alias wins over the distribution default"() {
        given: 'a version that exists nowhere, so resolution fails naming exactly what was requested'
        file('settings.gradle.xdcl') << settingsWithToolchain(
            '{ id "embedded-kotlin", version "999.0.0", apply false }')
        file('build.gradle.xdcl') << 'xdclGradlePluginKotlin {\n}'

        when:
        fails("help")

        then: 'the alias rewrote the id but honored the declared version'
        failureDescriptionContains("org.gradle.kotlin.embedded-kotlin")
        failureDescriptionContains("999.0.0")
    }

    def "a build that declares no Kotlin toolchain gets a teaching error naming both options"() {
        given:
        file('settings.gradle.xdcl') << '''
            settings {
              plugins [
                { id "plugin-development-ecosystem" }
              ]
              rootProject { name "demo-plugin" }
            }
        '''
        file('build.gradle.xdcl') << 'xdclGradlePluginKotlin {\n}'

        when:
        fails("help")

        then:
        failureCauseContains('{ id "embedded-kotlin", apply false }')
        failureCauseContains('{ id "org.jetbrains.kotlin.jvm", version "<kotlinVersion>", apply false }')
    }
}
