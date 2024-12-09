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


import org.gradle.integtests.fixtures.AbstractIntegrationSpec;

class DeprecationReporterIntegrationTest extends AbstractIntegrationSpec {

    def "can report simple deprecation"() {
        given:
        // settings.gradle.kts
        settingsFile("""
            includeBuild("deprecation-plugin")
        """)
        buildFile("""
            plugins {
                id 'deprecation-reporting-plugin'
            }

            tasks.register('test') {
                doLast {
                    println 'Hello, World!'
                }
            }
        """)
        // build.gradle.kts
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
                        id = 'deprecation-reporting-plugin'
                        implementationClass = 'DeprecationPlugin'
                    }
                }
            }
        """)
        // Plugin class for deprecation
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
                    getProblems().getDeprecationReporter().deprecate("This plugin is deprecated", feature -> feature
                            .because("We decided to remove it")
                            .inVersion("2.0.0")
                            .replacedBy("plugin-other")
                    );
                }
            }
            """)

        when:
        enableProblemsApiCheck()
        succeeds("help")

        then:
        def deprecation = receivedProblem
        deprecation.definition.id.fqid == "deprecation:generic"
        deprecation.contextualLabel == "This plugin is deprecated"
        verifyAll(deprecation.additionalData.asMap) {
            it["because"] == "We decided to remove it"
            it["replacedBy"] == "plugin-other"
            it["removedIn"]["opaqueVersion"] == "2.0.0"
        }
    }

}
