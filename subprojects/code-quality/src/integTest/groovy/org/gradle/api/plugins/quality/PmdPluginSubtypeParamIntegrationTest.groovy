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
package org.gradle.api.plugins.quality

import org.gradle.util.VersionNumber

import static org.junit.Assume.assumeTrue

class PmdPluginSubtypeParamIntegrationTest extends AbstractPmdPluginVersionIntegrationTest {

    static boolean supportsAuxclasspath() {
        return VersionNumber.parse("5.2.0") < versionNumber
    }

    def setup() {
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'pmd'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile "${calculateDefaultDependencyNotation()}"
                compile 'ch.qos.logback.contrib:logback-json-core:0.1.4'
            }

            pmd {
                ruleSets = ["java-unusedcode"]
            }
        """

        file("src/main/java/org/gradle/ruleusing/UnderAnalysis.java") << underAnalysisCode()
        file("src/main/java/org/gradle/ruleusing/IAccessEvent.java") << iAccessEventCode()
        file("src/main/java/org/gradle/ruleusing/IRequestEvent.java") << iRequestEventCode()
        file("src/main/java/org/gradle/ruleusing/JsonHttpLayout.java") << jsonHttpLayoutCode()
    }

    def "unused code rule not triggered when passing subtype parameter"() {
        assumeTrue(supportsAuxclasspath())

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
