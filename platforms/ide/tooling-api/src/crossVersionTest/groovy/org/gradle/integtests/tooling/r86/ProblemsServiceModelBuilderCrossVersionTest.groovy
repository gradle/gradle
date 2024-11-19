/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.r86

import org.gradle.api.problems.Problems
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r85.CustomModel
import org.gradle.integtests.tooling.r85.ProblemProgressEventCrossVersionTest.ProblemProgressListener
import org.gradle.util.GradleVersion
import org.junit.Assume

import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk17
import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk21
import static org.gradle.integtests.fixtures.AvailableJavaHomes.getJdk8

@ToolingApiVersion(">=8.6")
class ProblemsServiceModelBuilderCrossVersionTest extends ToolingApiSpecification {

    static String getBuildScriptSampleContent(boolean pre86api, boolean includeAdditionalMetadata, GradleVersion targetVersion, Integer threshold = 1) {
        def isNewerOrEqual811 = targetVersion >= GradleVersion.version("8.11")
        def isOlderThan89 = targetVersion < GradleVersion.version("8.9")
        def additionalDataCall = includeAdditionalMetadata ? isOlderThan89 ? '.additionalData("keyToString", "value")"' : ".additionalData(org.gradle.api.problems.${targetVersion >= GradleVersion.version("8.12") ? '' : 'internal.'}GeneralData) { it.put(\"keyToString\", \"value\") }" : ""
        def isOlderThan88 = targetVersion < GradleVersion.version("8.8")
        def label = isOlderThan88 ? 'label("label").category("testcategory")' : 'id("testcategory", "label")'
        """
            import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
            import org.gradle.tooling.provider.model.ToolingModelBuilder
            import ${Problems.name}
            import javax.inject.Inject

            allprojects {
                apply plugin: CustomPlugin
            }

            class CustomModel implements Serializable {
                CustomModel() {
                }
            }

            class CustomBuilder implements ToolingModelBuilder {
                private final Problems problemsService

                CustomBuilder(Problems problemsService) {
                    this.problemsService = problemsService
                }

                boolean canBuild(String modelName) {
                    return modelName == '${CustomModel.name}'
                }
                Object buildAll(String modelName, Project project) {
                    ($threshold).times{
                        problemsService.${isNewerOrEqual811 ? 'getReporter().reporting' : pre86api ? "create" : "forNamespace(\"org.example.plugin\").reporting"} {
                            it.${label}
                                .withException(new RuntimeException("test"))
                                ${pre86api ? ".undocumented()" : ""}
                                ${additionalDataCall}
                        }${pre86api ? ".report()" : ""}
                    }
                    return new CustomModel()
                }
            }

            class CustomPlugin implements Plugin<Project> {
                @Inject
                CustomPlugin(ToolingModelBuilderRegistry registry, Problems problemsService) {
                    registry.register(new CustomBuilder(problemsService))
                }

                public void apply(Project project) {
                }
            }
        """
    }

    @TargetGradleVersion("=8.6")
    def "Can use problems service in model builder and get problem"() {
        given:
        Assume.assumeTrue(jdk != null)
        buildFile getBuildScriptSampleContent(false, false, targetVersion)
        def listener = new ProblemProgressListener()

        when:
        withConnection {
            it.model(CustomModel)
                .setJavaHome(jdk.javaHome)
                .addProgressListener(listener)
                .get()
        }
        def problems = listener.problems

        then:
        problems.size() == 1

        where:
        jdk << [
            jdk8,
            jdk17,
            jdk21
        ]
    }
}
