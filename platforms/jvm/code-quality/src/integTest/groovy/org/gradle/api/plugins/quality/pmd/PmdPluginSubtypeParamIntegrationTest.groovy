/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.util.internal.VersionNumber

import static org.junit.Assume.assumeTrue

class PmdPluginSubtypeParamIntegrationTest extends AbstractPmdPluginVersionIntegrationTest {

    static boolean supportsAuxclasspath() {
        return VersionNumber.parse("5.2.0") < versionNumber
    }

    def setup() {
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'pmd'

            ${mavenCentralRepository()}

            dependencies {
                pmd "${calculateDefaultDependencyNotation()}"
                implementation 'ch.qos.logback.contrib:logback-json-core:0.1.4'
            }
        """

        if (versionNumber < VersionNumber.version(6)) {
            buildFile << """
                pmd {
                    ruleSets = ["java-unusedcode"]
                    incrementalAnalysis = false
                }
            """
        } else {
            buildFile << """
                pmd {
                    ruleSetConfig = resources.text.fromString('''<?xml version="1.0"?>
                        <ruleset name="Unused Code">
                            <description>Copy of https://github.com/pmd/pmd/blob/master/pmd-java/src/main/resources/rulesets/java/unusedcode.xml without deprecations.</description>
                            <rule ref="category/java/bestpractices.xml/UnusedFormalParameter" />
                            <rule ref="category/java/bestpractices.xml/UnusedLocalVariable" />
                            <rule ref="category/java/bestpractices.xml/UnusedPrivateField" />
                            <rule ref="category/java/bestpractices.xml/UnusedPrivateMethod" />
                        </ruleset>
                    ''')
                }
            """
        }

        file("src/main/java/org/gradle/ruleusing/UnderAnalysis.java") << underAnalysisCode()
        file("src/main/java/org/gradle/ruleusing/IAccessEvent.java") << iAccessEventCode()
        file("src/main/java/org/gradle/ruleusing/IRequestEvent.java") << iRequestEventCode()
        file("src/main/java/org/gradle/ruleusing/JsonHttpLayout.java") << jsonHttpLayoutCode()
    }

    def "unused code rule not triggered when passing subtype parameter"() {
        assumeTrue(supportsAuxclasspath() && fileLockingIssuesSolved())

        expect:
        succeeds "pmdMain"
    }

    private underAnalysisCode() {
        """
            package org.gradle.ruleusing;

            import ch.qos.logback.contrib.json.JsonLayoutBase;
            import ch.qos.logback.core.ConsoleAppender;

            public class UnderAnalysis {
               private final ConsoleAppender<IAccessEvent> appender = null;

               private final JsonLayoutBase<IAccessEvent> baseLayout = null;
               private final JsonHttpLayout<IAccessEvent> httpLayout = null;

               public UnderAnalysis() {
                  expectingBaseLayout(appender, baseLayout);
                  alsoExpectingBaseLayout(appender, httpLayout);
               }

               private <E> void expectingBaseLayout(ConsoleAppender<E> appender, JsonLayoutBase<E> layout) {
                  System.out.println(appender);
                  System.out.println(layout);
               }

               private <E> void alsoExpectingBaseLayout(ConsoleAppender<E> appender, JsonLayoutBase<E> layout) {
                  System.out.println(appender);
                  System.out.println(layout);
               }
            }
        """
    }

    private iAccessEventCode() {
        """
            package org.gradle.ruleusing;
            interface IAccessEvent extends IRequestEvent { }
        """
    }

    private iRequestEventCode() {
        """
            package org.gradle.ruleusing;
            interface IRequestEvent { }
        """
    }

    private jsonHttpLayoutCode() {
        """
            package org.gradle.ruleusing;
            import ch.qos.logback.contrib.json.JsonLayoutBase;

            abstract class JsonHttpLayout<E extends IRequestEvent> extends JsonLayoutBase<E> {}
        """
    }
}
