/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.quality.pmd

class PmdCustomRulesIntegrationTest extends AbstractPmdPluginVersionIntegrationTest {

    def "overrides default rules if custom rules are configured"() {
        given:
        buildFile << """
            plugins {
                id "pmd"
                id "java-library"
            }

            ${requiredSourceCompatibility()}

            ${mavenCentralRepository()}

            pmd {
                $customRuleSetConfig
                toolVersion = '$version'
                incrementalAnalysis = ${supportIncrementalAnalysis()}
            }
        """

        file("src/main/java/org/gradle/ruleusing/Class1.java") << breakingDefaultRulesCode()
        file("rules.xml") << customRuleSet()

        expect:
        succeeds(":pmdMain")

        where:
        customRuleSetConfig << [
            'ruleSetConfig = resources.text.fromFile("rules.xml")',
            'ruleSetFiles = files("rules.xml")'
        ]
    }

    def "use default rules if custom rules are not configured"() {
        given:
        buildFile << """
            plugins {
                id "pmd"
                id "java-library"
            }
            pmd {
                consoleOutput = true
                toolVersion = '$version'
                incrementalAnalysis = ${supportIncrementalAnalysis()}
            }

            ${requiredSourceCompatibility()}

            ${mavenCentralRepository()}
        """

        file("src/main/java/org/gradle/ruleusing/Class1.java") << breakingDefaultRulesCode()

        expect:
        fails(":pmdMain")
        errorOutput.contains("PMD rule violations were found")
        output.contains("AvoidCatchingThrowable")
    }

    def "overrides default rules if custom rules are configured in pmdMain only"() {
        given:
        buildFile << """
            plugins {
                id "pmd"
                id "java-library"
            }

            ${requiredSourceCompatibility()}

            ${mavenCentralRepository()}

            pmd {
                toolVersion = '$version'
                incrementalAnalysis = ${supportIncrementalAnalysis()}
            }

            pmdMain {
                $customRuleSetConfig
            }
        """

        file("src/main/java/org/gradle/ruleusing/Class1.java") << breakingDefaultRulesCode()
        file("rules.xml") << customRuleSet()

        expect:
        succeeds(":pmdMain")

        where:
        customRuleSetConfig << [
            'ruleSetConfig = resources.text.fromFile("rules.xml")',
            'ruleSetFiles = files("rules.xml")'
        ]
    }

    private static breakingDefaultRulesCode() {
        """
            package org.gradle.ruleusing;

            public final class Class1 {

                void foo() {
                   try {
                   } catch(Throwable t) {
                       // AvoidCatchingThrowable: https://docs.pmd-code.org/latest/pmd_rules_java_errorprone.html#avoidcatchingthrowable
                       System.out.println("Rule from category/java/errorprone.xml: Should not catch throwable");
                   }
                }
            }
        """
    }
}
