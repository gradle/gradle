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

package org.gradle.integtests.tooling.r85

import org.gradle.api.problems.Problems
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.BaseProblemDescriptor
import org.gradle.tooling.events.problems.ProblemDescriptor
import org.gradle.tooling.events.problems.ProblemEvent

import static org.gradle.integtests.fixtures.AvailableJavaHomes.jdk17

@ToolingApiVersion(">=8.5")
@TargetGradleVersion(">=8.5")
class ProblemsServiceModelBuilderCrossVersionTest extends ToolingApiSpecification {

    def withSampleProject(boolean includeAdditionalMetadata = false, boolean pre86api = true) {
        buildFile getBuildScriptSampleContent(pre86api, includeAdditionalMetadata)
    }

    static String getBuildScriptSampleContent(boolean pre86api, boolean includeAdditionalMetadata) {
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
                    problemsService.${pre86api ? "create" : "forNamespace(\"org.example.plugin\").reporting"} {
                        it.label("label")
                            .category("testcategory")
                            .withException(new RuntimeException("test"))
                            ${pre86api ? ".undocumented()" : ""}
                            ${includeAdditionalMetadata ? ".additionalData(\"keyToString\", \"value\")" : ""}
                    }${pre86api ? ".report()" : ""}
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

    @TargetGradleVersion("<8.6")
    def "Can use problems service in model builder before 8.6"() {
        given:
        withSampleProject()
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
        withConnection {
            it.model(CustomModel)
                .setJavaHome(jdk17.javaHome)
                .addProgressListener(listener)
                .get()
        }
        then:
        listener.problems.size() == 1
    }

    @TargetGradleVersion(">=8.6")
    @ToolingApiVersion(">=8.6")
    def "getFailure always null in older version"() {
        buildFile """
            tasks.register("foo) {
        """

        given:
        ProblemProgressListener listener = new ProblemProgressListener()

        when:
        withConnection {
            it.model(CustomModel)
                .setJavaHome(jdk17.javaHome)
                .addProgressListener(listener)
                .get()
        }

        then:
        thrown(BuildException)
        def problems = listener.problems.collect { it as ProblemDescriptor }
        problems.size() == 1
        problems[0].label.label == "Could not compile build file '$buildFile.absolutePath'."
        problems[0].category.category == 'compilation'
    }

    class ProblemProgressListener implements ProgressListener {

        List<BaseProblemDescriptor> problems = []

        @Override
        void statusChanged(ProgressEvent event) {
            if (event instanceof ProblemEvent) {
                this.problems.add(event.getDescriptor())
            }
        }
    }
}
