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

package org.gradle.api.problems.deprecation

import org.gradle.api.problems.internal.StackTraceLocation
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DeprecationReporterIntegrationTest extends AbstractIntegrationSpec {

    private static final String TEST_DETAILS = "reasoning of removal"
    private static final String TEST_REPLACEMENT = "replacement of feature"
    private static final String TEST_REMOVED_IN_VERSION = "x.y.z"
    private static final String TEST_PLUGIN_ID = "report.source.plugin.id"
    private static final String TEST_PLUGIN_REPORT_SOURCE_ID = "report.source.plugin.id"

    def setup() {
        enableProblemsApiCheck()

        settingsFile('''
            includeBuild("plugin")
        ''')
        buildFile('''
            plugins {
                id 'test.plugin'
            }
        ''')
        buildFile("plugin/build.gradle", '''
            plugins {
                id 'java-gradle-plugin'
            }

            dependencies {
                implementation gradleApi()
            }

            gradlePlugin {
                plugins {
                   testPlugin {
                        id = 'test.plugin'
                        implementationClass = 'TestPlugin'
                    }
                }
            }
        ''')
    }

    def "generic deprecation can be reported"() {
        javaFile("plugin/src/main/java/TestPlugin.java", """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.problems.Problems;
            import org.gradle.api.problems.deprecation.source.*;

            import javax.inject.Inject;

            public abstract class TestPlugin implements Plugin<Project> {

                @Inject
                public abstract Problems getProblems();

                @Override
                public void apply(Project project) {
                    getProblems().getDeprecationReporter().deprecate(
                        ${reportSource},
                        "generic deprecation",
                        spec -> spec
                           .details("${TEST_DETAILS}")
                           .willBeRemovedInVersion("${TEST_REMOVED_IN_VERSION}")
                           .shouldBeReplacedBy("${TEST_REPLACEMENT}")
                    );
                }
            }
        """)

        when:
        succeeds("help")

        then:
        verifyAll(receivedProblem) {
            it.fqid == "deprecation:generic"
            it.definition.id.displayName == "Generic deprecation"
            it.contextualLabel == "generic deprecation"
            it.details == TEST_DETAILS
            it.getSingleOriginLocation(StackTraceLocation)
            def additionalData = it.additionalData.asMap
            additionalData.willBeRemovedInVersion == TEST_REMOVED_IN_VERSION
            additionalData.shouldBeReplacedBy == TEST_REPLACEMENT
        }

        where:
        reportSource << [
            "ReportSource.gradle()",
            "ReportSource.plugin(\"${TEST_PLUGIN_REPORT_SOURCE_ID}\")"
        ]
    }

    def "plugin deprecation can be reported"() {
        javaFile("plugin/src/main/java/TestPlugin.java", """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.problems.Problems;
            import org.gradle.api.problems.deprecation.source.*;

            import javax.inject.Inject;

            public abstract class TestPlugin implements Plugin<Project> {

                @Inject
                public abstract Problems getProblems();

                @Override
                public void apply(Project project) {
                    getProblems().getDeprecationReporter().deprecatePlugin(
                        ${reportSource},
                        "${TEST_PLUGIN_ID}",
                        spec -> spec
                           .details("${TEST_DETAILS}")
                           .willBeRemovedInVersion("${TEST_REMOVED_IN_VERSION}")
                           .shouldBeReplacedBy("${TEST_REPLACEMENT}")
                    );
                }
            }
        """)

        when:
        succeeds("help")

        then:
        verifyAll(receivedProblem) {
            it.fqid == "deprecation:plugin"
            it.definition.id.displayName == "Plugin deprecation"
            it.contextualLabel == "Plugin '${TEST_PLUGIN_ID}' is deprecated"
            it.details == TEST_DETAILS
            it.getSingleOriginLocation(StackTraceLocation)
            def additionalData = it.additionalData.asMap
            additionalData.willBeRemovedInVersion == TEST_REMOVED_IN_VERSION
            additionalData.shouldBeReplacedBy == TEST_REPLACEMENT
        }

        where:
        reportSource << [
            "ReportSource.gradle()",
            "ReportSource.plugin(\"${TEST_PLUGIN_REPORT_SOURCE_ID}\")"
        ]
    }

    def "method deprecation can be reported"() {
        javaFile("plugin/src/main/java/TestPlugin.java", """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.problems.Problems;

            import javax.inject.Inject;

            public abstract class TestPlugin implements Plugin<Project> {

                @Inject
                public abstract Problems getProblems();

                @Override
                public void apply(Project project) {
                    getProblems().getDeprecationReporter().deprecateMethod(
                        ${reportSource},
                        TestPlugin.class,
                        "apply(Project project)",
                        spec -> spec
                           .details("${TEST_DETAILS}")
                           .willBeRemovedInVersion("${TEST_REMOVED_IN_VERSION}")
                           .shouldBeReplacedBy("${TEST_REPLACEMENT}")
                    );
                }
            }
        """)

        when:
        succeeds("help")

        then:
        verifyAll(receivedProblem) {
            it.fqid == "deprecation:method"
            it.definition.id.displayName == "Method deprecation"
            it.contextualLabel == "Method 'TestPlugin.apply(Project project)' is deprecated"
            it.details == TEST_DETAILS
            it.getSingleOriginLocation(StackTraceLocation)
            def additionalData = it.additionalData.asMap
            additionalData.willBeRemovedInVersion == TEST_REMOVED_IN_VERSION
            additionalData.shouldBeReplacedBy == TEST_REPLACEMENT
        }

        where:
        reportSource << [
            "org.gradle.api.problems.deprecation.source.ReportSource.gradle()",
            "org.gradle.api.problems.deprecation.source.ReportSource.plugin(\"${TEST_PLUGIN_REPORT_SOURCE_ID}\")"
        ]
    }

}
