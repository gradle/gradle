/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.tasks.diagnostics

import org.gradle.api.tasks.diagnostics.internal.ReportRenderer
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.internal.logging.text.StyledTextOutput

/**
 * Test to ensure backward compatibility for how {@link AbstractReportTask} is used in the Android plugin.
 * See <a href="https://cs.android.com/android/platform/superproject/+/master:tools/base/build-system/gradle-core/src/main/groovy/com/android/build/gradle/internal/tasks/SourceSetsTask.java;l=40;drc=4e74351da07ffd8a4ab114f5b660be3682b50d07">SourceSetsTask</a>
 */
@SuppressWarnings('GrDeprecatedAPIUsage')
@UnsupportedWithConfigurationCache(
    because = "AbstractReportTask has been deprecated in favor of ProjectBasedReportTask with supports the configuration cache."
)
class AbstractReportTaskIntegrationTest extends AbstractIntegrationSpec {

    def "can extend AbstractReportTask"() {
        given:
        buildFile """
            import ${AbstractReportTask.name}
            import ${TextReportRenderer.name}
            import ${ReportRenderer.name}
            import ${StyledTextOutput.name}

            plugins {
                id 'java-library'
            }

            class SourceSetTasks extends AbstractReportTask {
                private final TextReportRenderer mRenderer = new TextReportRenderer()

                @Override
                protected ReportRenderer getRenderer() {
                    return mRenderer
                }

                @Override
                protected void generate(Project project) throws IOException {
                    for (sourceSet in project.sourceSets) {
                        mRenderer.builder.subheading sourceSet.name
                        mRenderer.textOutput
                            .withStyle(StyledTextOutput.Style.Identifier)
                            .text("Compile configuration name: ")
                        mRenderer.textOutput
                            .withStyle(StyledTextOutput.Style.Info)
                            .text(sourceSet.implementationConfigurationName)
                        mRenderer.textOutput.println()
                    }
                }
            }

            tasks.register("sourceSets", SourceSetTasks) {}
        """

        when:
        run 'sourceSets'

        then:
        outputContains 'Compile configuration name: implementation'
    }
}
