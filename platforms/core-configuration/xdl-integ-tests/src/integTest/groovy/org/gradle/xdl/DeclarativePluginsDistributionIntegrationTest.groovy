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

package org.gradle.xdl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture

/**
 * The built-in XDL plugin-development ecosystem, end-to-end against a real distribution (forking).
 * Applying {@code declarative-plugins} PULLS its schema from the distribution via
 * ModuleRegistry, so {@code declarativePlugins { }} resolves and DeclarativePluginsReaction wires the
 * REAL plugin-development machinery: java-library + java-gradle-plugin + the bundled
 * xdl-gradle-plugin. The project's own {@code src/main/xdl/} single-sources everything else — the
 * schema generates facades, and the {@code <plugin-id>.xdl} plugin block feeds the {@code
 * gradlePlugin} registration (role 1), from which java-gradle-plugin generates the plugin descriptor.
 */
class DeclarativePluginsDistributionIntegrationTest extends AbstractIntegrationSpec {

    def "the built-in declarative-plugins builds a real XDL plugin jar from a declarative project"() {
        given: 'a build that opts into the built-in plugin-development ecosystem'
        file('settings.gradle.xdl') << '''
            settings {
              plugins [
                { id "declarative-plugins" }
              ]
              rootProject { name "demo-plugin" }
            }
        '''

        and: 'an empty declarativePlugins project type — everything else is single-sourced from src/main/xdl'
        file('build.gradle.xdl') << '''
            declarativePlugins {
            }
        '''

        and: 'the plugin schema and its plugin declaration (file name = plugin id; carrier is generated)'
        file('src/main/xdl/demo.xdls') << '''
            package com.example.demo

            root Demo {
              demo {
                name: String
              }
            }
        '''.stripIndent()
        file('src/main/xdl/com.example.demo.xdl') << '''
            plugin {
            }
        '''.stripIndent()

        when:
        succeeds("jar")

        then: 'codegen ran and the real java-library compiled its output'
        executedAndNotSkipped(":xdlCodegen", ":compileJava", ":jar")

        and: 'the jar is a complete XDL plugin: schema, generated facade, generated carrier, descriptor'
        def jar = new JarTestFixture(file('build/libs/demo-plugin.jar'))
        jar.assertContainsFile('META-INF/xdl/demo.xdls')
        jar.assertContainsFile('com/example/demo/Demo.class')
        jar.assertContainsFile('xdl/generated/plugins/ComExampleDemoPlugin.class')
        jar.assertContainsFile('META-INF/gradle-plugins/com.example.demo.properties')
    }

    def "declared repositories and dependencies map onto the real java-library configurations"() {
        given: 'a hermetic maven repository with one module per dependency scope'
        mavenRepo.module("org.test", "api-dep", "1.0").publish()
        mavenRepo.module("org.test", "impl-dep", "1.0").publish()
        mavenRepo.module("org.test", "compile-only-dep", "1.0").publish()
        mavenRepo.module("org.test", "runtime-only-dep", "1.0").publish()

        and: 'a declarative plugin project: the repository in settings, the four dependency scopes on the root'
        file('settings.gradle.xdl') << """
            settings {
              plugins [
                { id "declarative-plugins" }
              ]
              dependencyResolutionManagement {
                repositories ["${mavenRepo.uri}"]
              }
              rootProject { name "demo-plugin" }
            }
        """
        file('build.gradle.xdl') << '''
            declarativePlugins {
              dependencies {
                api ["org.test:api-dep:1.0"]
                implementation ["org.test:impl-dep:1.0"]
                compileOnly ["org.test:compile-only-dep:1.0"]
                runtimeOnly ["org.test:runtime-only-dep:1.0"]
              }
            }
        '''

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

    def "an included build authored with declarativePlugins supplies a plugin consumed by a declarative root build"() {
        given: 'an included build-logic build that is itself declarative: an declarativePlugins project'
        file('build-logic/settings.gradle.xdl') << '''
            settings {
              plugins [
                { id "declarative-plugins" }
              ]
            }
        '''
        file('build-logic/build.gradle.xdl') << '''
            declarativePlugins {
            }
        '''

        and: 'its plugin: a schema, a reaction bound through the plugin config, and nothing imperative'
        file('build-logic/src/main/xdl/demo.xdls') << '''
            package com.example.demo

            root Demo {
              demo {
                name: String
              }
            }
        '''.stripIndent()
        file('build-logic/src/main/xdl/com.example.demo.xdl') << '''
            plugin {
              reactions [ :com.example.demo.DemoReaction ]
            }
        '''.stripIndent()
        file('build-logic/src/main/java/com/example/demo/DemoReaction.java') << '''
            package com.example.demo;

            import org.gradle.api.Project;
            import org.gradle.api.xdl.Reaction;
            import org.gradle.api.xdl.ReactionScope;

            public class DemoReaction implements Reaction<Demo, Project> {
                @Override
                public void on(Demo data, Project project, ReactionScope scope) {
                    System.out.println("demo-reaction-name=" + data.name().get() + " in " + project.getName());
                }
            }
        '''.stripIndent()

        and: 'a declarative root build consuming the plugin from the included build'
        file('settings.gradle.xdl') << '''
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
        file('build.gradle.xdl') << '''
            demo {
              name "hello-from-xdl"
            }
        '''

        when:
        succeeds("help")

        then: 'the plugin was built declaratively, its schema resolved the root, and its reaction fired'
        outputContains("demo-reaction-name=hello-from-xdl in consumer")
    }

    def "declarativePlugins is unavailable when the plugin-development ecosystem is not applied"() {
        given: 'a build that opts into no ecosystem'
        enableProblemsApiCheck()
        file('settings.gradle.xdl') << '''
            settings {
              rootProject { name "demo-plugin" }
            }
        '''
        file('build.gradle.xdl') << '''
            declarativePlugins {
            }
        '''

        when: 'the unapplied ecosystem contributed no schema, so its root never reached the frozen registry'
        fails("help")

        then:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:xdl:xdl-evaluation-error'
            contextualLabel.contains("declarativePlugins")
        }
    }
}
