/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.antlr

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.test.fixtures.archive.JarTestFixture
import spock.lang.Issue

import static org.gradle.test.fixtures.dsl.GradleDsl.GROOVY
import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN

class AntlrPluginIntegrationTest extends WellBehavedPluginTest {
    @Override
    String getMainTask() {
        return "build"
    }

    def "can handle grammar in nested folders"() {
        given:
        buildFile << """
            apply plugin: "java"
            apply plugin: "antlr"

            ${mavenCentralRepository()}
        """
        and:

        file("src/main/antlr/org/acme/TestGrammar.g") << testGrammarSource

        when:
        succeeds("generateGrammarSource")

        then:
        file("build/generated-src/antlr/main/TestGrammar.java").exists()
        file("build/generated-src/antlr/main/TestGrammar.smap").exists()
        file("build/generated-src/antlr/main/TestGrammarTokenTypes.java").exists()
        file("build/generated-src/antlr/main/TestGrammarTokenTypes.txt").exists()

    }

    def "can configure antlr source set extension in Groovy scripts"() {
        given:
        buildFile << """
            apply plugin: "java"
            apply plugin: "antlr"

            ${mavenCentralRepository(GROOVY)}

            sourceSets.main {
                antlr {}
            }
            sourceSets.main {
                antlr({} as Action)
            }
        """

        expect:
        succeeds 'help'
    }

    def "can configure antlr source set extension in Kotlin scripts"() {
        given:
        buildKotlinFile << """
            plugins {
                java
                antlr
            }

            ${mavenCentralRepository(KOTLIN)}

            sourceSets.main {
                antlr {}
            }
            sourceSets.main {
                antlr(Action {})
            }
        """

        expect:
        succeeds 'help'
    }

    @Issue('https://github.com/gradle/gradle/issues/19555')
    def "creates proper dependency wiring between generated source set and source generation task"() {
        given:
        settingsFile << """
            rootProject.name = 'antlr'
        """
        buildFile << """
            apply plugin: "java"
            apply plugin: "antlr"

            ${mavenCentralRepository()}

            java {
                withSourcesJar()
            }
        """

        and:
        file("src/main/antlr/TestGrammar.g") << testGrammarSource

        when:
        succeeds('sourcesJar')

        then:
        executed(':generateGrammarSource')

        and:
        def jar = new JarTestFixture(file("build/libs/antlr-sources.jar"))
        jar.assertContainsFile("TestGrammar.java")
        jar.assertContainsFile("TestGrammar.g")
        jar.assertContainsFile("TestGrammar.smap")
        jar.assertContainsFile("TestGrammarTokenTypes.txt")
        jar.assertContainsFile("TestGrammarTokenTypes.java")
    }

    private static String getTestGrammarSource() {
        return """ class TestGrammar extends Parser;
        options {
            buildAST = true;
        }

        expr:   mexpr (PLUS^ mexpr)* SEMI!
        ;

        mexpr
        :   atom (STAR^ atom)*
        ;

        atom:   INT
        ;"""
    }
}
