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
import org.gradle.test.fixtures.archive.JarTestFixture

/**
 * The built-in XDCL plugin-development ecosystem, end-to-end against a real distribution (forking).
 * Applying {@code plugin-development-ecosystem} PULLS its schema from the distribution via
 * ModuleRegistry, so {@code xdclGradlePlugin { }} resolves and XdclGradlePluginReaction wires the
 * REAL plugin-development machinery: java-library + java-gradle-plugin + the bundled
 * xdcl-gradle-plugin. The project's own {@code src/main/xdcl/} single-sources everything else — the
 * schema generates facades, and the {@code <plugin-id>.xdcl} plugin block feeds the {@code
 * gradlePlugin} registration (role 1), from which java-gradle-plugin generates the plugin descriptor.
 */
class XdclPluginDevelopmentEcosystemDistributionIntegrationTest extends AbstractIntegrationSpec {

    def "the built-in plugin-development-ecosystem builds a real XDCL plugin jar from a declarative project"() {
        given: 'a build that opts into the built-in plugin-development ecosystem'
        file('settings.gradle.xdcl') << '''
            settings {
              plugins [
                { id "plugin-development-ecosystem" }
              ]
              rootProject { name "demo-plugin" }
            }
        '''

        and: 'an empty xdclGradlePlugin project type — everything else is single-sourced from src/main/xdcl'
        file('build.gradle.xdcl') << '''
            xdclGradlePlugin {
            }
        '''

        and: 'the plugin schema and its plugin declaration (file name = plugin id; carrier is generated)'
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
            }
        '''.stripIndent()

        when:
        succeeds("jar")

        then: 'codegen ran and the real java-library compiled its output'
        executedAndNotSkipped(":xdclCodegen", ":compileJava", ":jar")

        and: 'the jar is a complete XDCL plugin: schema, generated facade, generated carrier, descriptor'
        def jar = new JarTestFixture(file('build/libs/demo-plugin.jar'))
        jar.assertContainsFile('META-INF/xdcl/demo.xdsl')
        jar.assertContainsFile('com/example/demo/Demo.class')
        jar.assertContainsFile('xdcl/generated/plugins/ComExampleDemoPlugin.class')
        jar.assertContainsFile('META-INF/gradle-plugins/com.example.demo.properties')
    }

    def "declared repositories and dependencies map onto the real java-library configurations"() {
        given: 'a hermetic maven repository with one module per dependency scope'
        mavenRepo.module("org.test", "api-dep", "1.0").publish()
        mavenRepo.module("org.test", "impl-dep", "1.0").publish()
        mavenRepo.module("org.test", "compile-only-dep", "1.0").publish()
        mavenRepo.module("org.test", "runtime-only-dep", "1.0").publish()

        and: 'a declarative plugin project declaring the repository and all four dependency scopes'
        file('settings.gradle.xdcl') << '''
            settings {
              plugins [
                { id "plugin-development-ecosystem" }
              ]
              rootProject { name "demo-plugin" }
            }
        '''
        file('build.gradle.xdcl') << """
            xdclGradlePlugin {
              repositories ["${mavenRepo.uri}"]
              dependencies {
                api ["org.test:api-dep:1.0"]
                implementation ["org.test:impl-dep:1.0"]
                compileOnly ["org.test:compile-only-dep:1.0"]
                runtimeOnly ["org.test:runtime-only-dep:1.0"]
              }
            }
        """

        when:
        succeeds("dependencies", "--configuration", "compileClasspath")

        then: 'the compile classpath sees api, implementation, and compileOnly — not runtimeOnly'
        outputContains("org.test:api-dep:1.0")
        outputContains("org.test:impl-dep:1.0")
        outputContains("org.test:compile-only-dep:1.0")
        outputDoesNotContain("org.test:runtime-only-dep")

        when:
        succeeds("dependencies", "--configuration", "runtimeClasspath")

        then: 'the runtime classpath sees api, implementation, and runtimeOnly — not compileOnly'
        outputContains("org.test:api-dep:1.0")
        outputContains("org.test:impl-dep:1.0")
        outputContains("org.test:runtime-only-dep:1.0")
        outputDoesNotContain("org.test:compile-only-dep")
    }

    def "an included build authored with xdclGradlePlugin supplies a plugin consumed by a declarative root build"() {
        given: 'an included build-logic build that is itself declarative: an xdclGradlePlugin project'
        file('build-logic/settings.gradle.xdcl') << '''
            settings {
              plugins [
                { id "plugin-development-ecosystem" }
              ]
            }
        '''
        file('build-logic/build.gradle.xdcl') << '''
            xdclGradlePlugin {
            }
        '''

        and: 'its plugin: a schema, a reaction bound through the plugin config, and nothing imperative'
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
        file('build-logic/src/main/java/com/example/demo/DemoReaction.java') << '''
            package com.example.demo;

            import org.gradle.api.Project;
            import org.gradle.api.xdcl.Reaction;
            import org.gradle.api.xdcl.ReactionScope;

            public class DemoReaction implements Reaction<Demo, Project> {
                @Override
                public void on(Demo data, Project project, ReactionScope scope) {
                    System.out.println("demo-reaction-name=" + data.name().get() + " in " + project.getName());
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
              name "hello-from-xdcl"
            }
        '''

        when:
        succeeds("help")

        then: 'the plugin was built declaratively, its schema resolved the template, and its reaction fired'
        outputContains("demo-reaction-name=hello-from-xdcl in consumer")
    }

    def "xdclGradlePlugin is unavailable when the plugin-development ecosystem is not applied"() {
        given: 'a build that opts into no ecosystem'
        enableProblemsApiCheck()
        file('settings.gradle.xdcl') << '''
            settings {
              rootProject { name "demo-plugin" }
            }
        '''
        file('build.gradle.xdcl') << '''
            xdclGradlePlugin {
            }
        '''

        when: 'the unapplied ecosystem contributed no schema, so its template never reached the frozen registry'
        fails("help")

        then:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:xdcl:xdcl-evaluation-error'
            contextualLabel.contains("xdclGradlePlugin")
        }
    }
}
