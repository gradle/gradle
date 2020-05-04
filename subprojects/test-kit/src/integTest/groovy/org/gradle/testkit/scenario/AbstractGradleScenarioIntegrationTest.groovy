/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.testkit.scenario

import org.gradle.api.Action
import org.gradle.testkit.runner.BaseGradleRunnerIntegrationTest
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.fixtures.GradleRunnerScenario

import java.util.function.Supplier


@GradleRunnerScenario
class AbstractGradleScenarioIntegrationTest extends BaseGradleRunnerIntegrationTest {

    protected Supplier<GradleRunner> getUnderTestRunnerFactory() {
        return { runner().forwardOutput() }
    }

    protected File getUnderTestBaseDirectory() {
        return file("scenario-base")
    }

    protected static Action<File> getUnderTestWorkspace() {
        return { File root ->
            new File(root, 'settings.gradle') << 'rootProject.name = "test"'
            new File(root, 'build.gradle') << buildScriptUnderTest
            new File(root, underTestTaskInputFilePath) << 'ORIGINAL'
        }
    }

    protected static String getUnderTestTaskPath() {
        return ":$underTestTaskName"
    }

    protected static String getUnderTestTaskName() {
        return "underTest"
    }

    protected static String getBuildScriptUnderTest() {
        return """

            @CacheableTask
            abstract class UnderTestTask extends DefaultTask {

                @Input
                abstract Property<String> getHeader()

                @InputFile
                @PathSensitive(PathSensitivity.NONE)
                abstract RegularFileProperty getInputFile()

                @OutputFile
                abstract RegularFileProperty getOutputFile()

                @TaskAction
                void action() {
                    outputFile.get().asFile.text = header.get() + '\\n\\n' + inputFile.get().asFile.text
                }
            }

            println(providers.systemProperty("$underTestBuildLogicInputSystemPropertyName").getOrElse("UNSET_SYS_PROP"))
            println(providers.environmentVariable("$underTestBuildLogicInputEnvironmentVariableName").getOrElse("UNSET_ENV_VAR"))
            println(providers.fileContents(layout.projectDirectory.file("$underTestBuildLogicInputFileName")).asText.getOrElse("UNSET_FILE"))

            tasks.register("underTest", UnderTestTask) {
                header.set(providers.systemProperty("$underTestTaskInputSystemPropertyName").orElse("CONSTANT"))
                inputFile.set(layout.projectDirectory.file("$underTestTaskInputFilePath"))
                outputFile.set(layout.buildDirectory.file("output.txt"))
            }

        """.stripIndent()
    }

    protected static String getUnderTestTaskInputSystemPropertyName() {
        return "header"
    }

    protected static String getUnderTestTaskInputFilePath() {
        return "input.txt"
    }

    protected static String getUnderTestBuildLogicInputSystemPropertyName() {
        return "bliSysProp"
    }

    protected static String getUnderTestBuildLogicInputEnvironmentVariableName() {
        return "bliEnvVar"
    }

    protected static String getUnderTestBuildLogicInputFileName() {
        return "bliFile"
    }

    protected static Action<File> getEmptyBuildWorkspace() {
        return { File root ->
            new File(root, 'settings.gradle') << 'rootProject.name = "test"'
        }
    }
}
