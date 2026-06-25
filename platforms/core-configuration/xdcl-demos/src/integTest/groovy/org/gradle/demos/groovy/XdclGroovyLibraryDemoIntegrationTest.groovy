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

package org.gradle.demos.groovy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

@Requires(TestExecutionPreconditions.IsEmbeddedExecutor)
class XdclGroovyLibraryDemoIntegrationTest extends AbstractIntegrationSpec {

    private void applyEcosystem(String rootName = null) {
        file('settings.gradle.xdcl') << """
            settings {
              plugins [
                { id "groovy-ecosystem" }
              ]
              ${rootName ? "rootProject { name \"${rootName}\" }" : ""}
            }
        """
    }

    def "an empty groovyLibrary gets main and test source sets from the shipped default, the shipped groovyVersion, and registers their tasks"() {
        given:
        applyEcosystem()
        file('build.gradle.xdcl') << '''
            groovyLibrary {
            }
        '''

        when:
        succeeds("tasks", "--all")

        then: 'main and test came from the plugin-shipped whole-list default, with no sources declared'
        outputContains("sources=[main, test]")

        and: 'groovyVersion came from the plugin-shipped scalar default (not declared in the build)'
        outputContains("groovyVersion=4.0.3")

        and: 'the per-source compile and process tasks exist'
        outputContains("compileMainGroovy")
        outputContains("compileTestGroovy")
        outputContains("processMainResources")
        outputContains("processTestResources")

        and: 'plus a jar from the main classes'
        outputContains("jar")
    }

    def "wires top-level and per-source-set dependencies, with source configurations extending the shared ones"() {
        given:
        applyEcosystem()
        file('build.gradle.xdcl') << '''
            groovyLibrary {
              dependencies {
                implementation [ "com.example:shared-impl:1.0" ]
              }
              sources [
                {
                  name "main"
                  dependencies { implementation [ "com.example:main-impl:2.0" ] }
                },
                {
                  name "test"
                  dependencies { api [ "com.example:test-api:3.0" ] }
                },
              ]
              repositories [ :mavenCentral, "https://repo.example.invalid/maven" ]
            }
        '''

        when: 'the main source set implementation configuration is inspected'
        succeeds("dependencies", "--configuration", "mainImplementation")

        then: 'it carries the per-source dependency declared on the main source'
        outputContains("com.example:main-impl:2.0")

        when: 'the shared top-level implementation configuration is inspected'
        succeeds("dependencies", "--configuration", "implementation")

        then: 'it carries the project-wide dependency (mainImplementation extends this one in the reaction)'
        outputContains("com.example:shared-impl:1.0")

        when: 'the test source set api configuration is inspected'
        succeeds("dependencies", "--configuration", "testApi")

        then: 'it carries the per-source dependency declared on the test source'
        outputContains("com.example:test-api:3.0")
    }

    def "compiles the main source set into a jar and runs a JUnit test that loads the compiled Groovy class"() {
        given:
        applyEcosystem("demo")
        file('src/main/groovy/com/example/Greeter.groovy') << '''
            package com.example
            class Greeter {
                String greeting() { "hello" }
                String loudGreeting() { greeting().toUpperCase() }
            }
        '''
        file('src/test/groovy/com/example/GreeterTest.groovy') << '''
            package com.example
            import org.junit.Test
            import static org.junit.Assert.assertEquals
            class GreeterTest {
                @Test void greetsLoudly() {
                    assertEquals("HELLO", new Greeter().loudGreeting())
                }
            }
        '''
        file('build.gradle.xdcl') << """
            groovyLibrary {
              sources [
                { name "main" },
                {
                  name "test"
                  dependencies { implementation [ "junit:junit:4.13.2" ] }
                },
              ]
              repositories [ "${RepoScriptBlockUtil.mavenCentralMirrorUrl}" ]
            }
        """

        when: 'the jar task runs (compiling the main Groovy source against the resolved groovy classpath)'
        succeeds("jar")

        then: 'the compiled main class is packaged in the jar'
        new JarTestFixture(file("build/libs/demo.jar")).assertContainsFile("com/example/Greeter.class")

        when: 'the test task runs (compiling the Groovy test and executing JUnit against the compiled Greeter)'
        succeeds("test")

        then: 'the test compiled against the main source set and executed and passed (groovy runtime on the test classpath)'
        executedAndNotSkipped(":test")

        and: 'the JUnit result the reaction wired into the model reports the executed, passing test'
        def junitXml = file("build/test-results/test/TEST-com.example.GreeterTest.xml")
        junitXml.assertExists()
        junitXml.text.contains('name="greetsLoudly"')
        junitXml.text.contains('tests="1"')
        junitXml.text.contains('failures="0"')
    }

    def "adding a source set registers a compile task that compiles the new source set's classes"() {
        given:
        applyEcosystem()
        file('src/feature/groovy/com/example/feature/Feature.groovy') << '''
            package com.example.feature
            class Feature {
                int answer() { 42 }
            }
        '''
        file('build.gradle.xdcl') << """
            groovyLibrary {
              sources [
                { name "main" },
                { name "test" },
                { name "feature" },
              ]
              repositories [ "${RepoScriptBlockUtil.mavenCentralMirrorUrl}" ]
            }
        """

        when: 'the compile task for the added source set runs (resolving the groovy classpath)'
        succeeds("compileFeatureGroovy")

        then: 'it is a real compile task that compiled the new source set class to its own output dir'
        executedAndNotSkipped(":compileFeatureGroovy")
        file("build/classes/groovy/feature/com/example/feature/Feature.class").assertExists()
    }
}
