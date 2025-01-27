/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems.deprecation;


import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DeprecationReporterIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()

        settingsFile("""
            includeBuild("deprecation-plugin")
        """)
        buildFile("""
            plugins {
                id 'org.gradle.integtest.deprecation-test-plugin'
            }

            tasks.register('test') {
                doLast {
                    println 'Hello, World!'
                }
            }
        """)
        buildFile("deprecation-plugin/build.gradle", """
            plugins {
                id 'java-gradle-plugin'
            }

            dependencies {
                implementation gradleApi()
            }

            gradlePlugin {
                plugins {
                    deprecation {
                        id = 'org.gradle.integtest.deprecation-test-plugin'
                        implementationClass = 'DeprecationPlugin'
                    }
                }
            }
        """)
    }

    def "can report generic deprecation"() {
        given:
        javaFile("deprecation-plugin/src/main/java/DeprecationPlugin.java", """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.problems.Problems;

            import javax.inject.Inject;

            public abstract class DeprecationPlugin implements Plugin<Project> {
                @Inject
                public abstract Problems getProblems();

                @Override
                public void apply(Project project) {
                    // Report the plugin as deprecated
                    getProblems()
                        .getDeprecationReporter()
                        .deprecate("Generic deprecation message", feature -> feature
                            .because("Reasoning of removal")
                            .removedInVersion("2.0.0")
                            .replacedBy("newMethod(String, String)")
                        );
                }
            }
            """)

        when:
        succeeds("help")

        then:
        def deprecation = receivedProblem
        deprecation.definition.id.fqid == "deprecation:generic"
        deprecation.definition.id.displayName == "Generic deprecation"
        deprecation.contextualLabel == "Generic deprecation message"
        verifyAll(deprecation.additionalData.asMap) {
            it["because"] == "Reasoning of removal"
            it["replacedBy"] == "newMethod(String, String)"
            it["removedIn"]["version"] == "2.0.0"
        }
    }

    def "can report method deprecation"() {
        given:
        javaFile("deprecation-plugin/src/main/java/DeprecationPlugin.java", """
            import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.problems.Problems;

import javax.inject.Inject;

            public abstract class DeprecationPlugin implements Plugin<Project> {
                @Inject
                public abstract Problems getProblems();

                @Override
                public void apply(Project project) {
                    // Report the plugin as deprecated
                    getProblems().getDeprecationReporter().deprecateMethod(
                        this.getClass(), "oldMethod(String, String)", feature -> feature
                            .because("Reasoning of removal")
                            .removedInVersion("2.0.0")
                            .replacedBy("newMethod(String, String)")
                    );
                }
            }
            """)

        when:
        succeeds("help")

        then:
        def deprecation = receivedProblem
        deprecation.definition.id.fqid == "deprecation:method"
        deprecation.contextualLabel == "Method 'DeprecationPlugin_Decorated#oldMethod(String, String)' is deprecated"
        verifyAll(deprecation.additionalData.asMap) {
            it["because"] == "Reasoning of removal"
            it["replacedBy"] == "newMethod(String, String)"
            it["removedIn"]["version"] == "2.0.0"
        }
    }

    def "can report plugin deprecation"() {
        given:
        javaFile("deprecation-plugin/src/main/java/DeprecationPlugin.java", """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.problems.Problems;

            import javax.inject.Inject;

            public abstract class DeprecationPlugin implements Plugin<Project> {
                @Inject
                public abstract Problems getProblems();

                @Override
                public void apply(Project project) {
                    // Report the plugin as deprecated
                    getProblems()
                        .getDeprecationReporter()
                        .deprecatePlugin("this-plugin-id", feature -> feature
                            .because("Reasoning of removal")
                            .removedInVersion("2.0.0")
                            .replacedBy("plugin-other")
                    );
                }
            }
            """)

        when:
        succeeds("help")

        then:
        def deprecation = receivedProblem
        deprecation.definition.id.fqid == "deprecation:plugin"
        deprecation.contextualLabel == "Plugin 'this-plugin-id' is deprecated"
        verifyAll(deprecation.additionalData.asMap) {
            it["because"] == "Reasoning of removal"
            it["replacedBy"] == "plugin-other"
            it["removedIn"]["version"] == "2.0.0"
        }
    }

}
