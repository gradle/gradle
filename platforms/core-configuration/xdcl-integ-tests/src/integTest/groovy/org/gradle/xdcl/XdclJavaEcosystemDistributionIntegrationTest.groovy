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

/**
 * The built-in XDCL JVM ecosystem, end-to-end against a real distribution (forking). Applying
 * {@code java-ecosystem} by id ships no jar onto the build's classpath; the provider PULLS the
 * ecosystem's schema from the distribution via ModuleRegistry, so {@code javaLibrary { }} resolves
 * and JavaLibraryReaction configures a real Java build.
 */
class XdclJavaEcosystemDistributionIntegrationTest extends AbstractIntegrationSpec {

    def "the built-in java-ecosystem plugin contributes its schema and configures a real Java library"() {
        given: 'a build that opts into the built-in JVM ecosystem — no included build, no dependency resolution'
        file('settings.gradle.xdcl') << '''
            settings {
              plugins [
                { id "java-ecosystem" }
              ]
              rootProject { name "demo" }
            }
        '''

        and: 'an empty javaLibrary — main/test source sets come from the shipped ecosystem default'
        file('build.gradle.xdcl') << '''
            javaLibrary {
            }
        '''

        when:
        succeeds("tasks", "--all")

        then: 'the schema reached the frozen registry and the reaction registered the per-source-set Java tasks'
        outputContains("compileMainJava")
        outputContains("compileTestJava")
        outputContains("processMainResources")
        outputContains("processTestResources")

        and: 'plus a jar built from the main classes'
        outputContains("jar")
    }

    def "compiles the main source set into a jar via the distribution-shipped java-ecosystem"() {
        given:
        file('settings.gradle.xdcl') << '''
            settings {
              plugins [
                { id "java-ecosystem" }
              ]
              rootProject { name "demo" }
            }
        '''
        file('build.gradle.xdcl') << '''
            javaLibrary {
            }
        '''
        file('src/main/java/com/example/Greeter.java') << '''
            package com.example;
            public class Greeter {
                public String greeting() { return "hello"; }
                public String loudGreeting() { return greeting().toUpperCase(); }
            }
        '''

        expect: 'the reaction-registered compile + jar tasks build the real source end-to-end'
        succeeds("jar")
    }

    def "composes three built-in ecosystems: checkstyle and instrumentation build on the JVM one"() {
        given: 'a build applying the JVM ecosystem plus two that DEPEND on it (their schemas import it)'
        file('settings.gradle.xdcl') << '''
            settings {
              plugins [
                { id "java-ecosystem" }
                { id "checkstyle-ecosystem" }
                { id "instrumentation-ecosystem" }
              ]
              rootProject { name "demo" }
            }
        '''

        and: 'a javaLibrary augmented with the dependent ecosystems’ extensions bound to it and its source set'
        file('build.gradle.xdcl') << '''
            javaLibrary {
              checkstyle {
              }
              sources [
                {
                  name "main"
                  checkstyle {
                  }
                  instrument {
                  }
                },
                {
                  name "test"
                },
              ]
            }
        '''

        when: 'configuration only — enough to prove all three ecosystems’ schemas resolved and their reactions ran'
        succeeds("tasks", "--all")

        then: 'the JVM ecosystem registered the per-source Java tasks'
        outputContains("compileMainJava")
        outputContains("compileTestJava")

        and: 'the checkstyle ecosystem (bound to the JVM source set) registered its task'
        outputContains("checkMainCheckstyle")

        and: 'the instrumentation ecosystem (bound to the JVM source set) registered its task'
        outputContains("instrumentMainClasses")
    }

    def "a template from an unapplied ecosystem stays out of the registry"() {
        given: 'only the JVM ecosystem is applied — the Groovy ecosystem ships in the distribution but is not applied here'
        enableProblemsApiCheck()
        file('settings.gradle.xdcl') << '''
            settings {
              plugins [
                { id "java-ecosystem" }
              ]
              rootProject { name "demo" }
            }
        '''

        and: 'the build script names groovyLibrary { }, a top-level template owned by the unapplied Groovy ecosystem'
        file('build.gradle.xdcl') << '''
            groovyLibrary {
            }
        '''

        when: 'the unapplied ecosystem contributed no schema, so its template never reached the frozen registry'
        fails("help")

        then: 'evaluation fails on the unknown template — the completion-scoping property, proven negatively'
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:xdcl:xdcl-evaluation-error'
            contextualLabel.contains("groovyLibrary")
        }
    }
}
