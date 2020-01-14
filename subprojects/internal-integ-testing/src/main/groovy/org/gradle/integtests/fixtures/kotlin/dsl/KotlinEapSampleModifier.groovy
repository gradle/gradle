/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.fixtures.kotlin.dsl

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.samples.model.Command
import org.gradle.samples.model.Sample
import org.gradle.samples.test.runner.SampleModifier
import org.gradle.test.fixtures.dsl.GradleDsl

import static org.gradle.integtests.fixtures.kotlin.dsl.KotlinEapRepoUtil.createKotlinEapInitScript


/**
 * Modify samples to use the kotlin-eap repository.
 *
 * This modifier is intended to be used when testing integration of Kotlin EAPs.
 */
class KotlinEapSampleModifier implements SampleModifier {

    private File initScript = createKotlinEapInitScript()

    @Override
    Sample modify(Sample sample) {
        if (sample.getId().contains("usePluginsInInitScripts")) {
            // usePluginsInInitScripts asserts using https://repo.gradle.org/gradle/repo
            return sample;
        }
        List<Command> commands = sample.commands
        List<Command> modifiedCommands = []
        List<File> buildSrcKotlinBuildScripts = []
        for (Command command : commands) {
            if (command.executable == "gradle") {
                List<String> args = new ArrayList<>(command.args)
                args.add("--init-script")
                args.add(initScript.absolutePath)
                modifiedCommands.add(command.toBuilder().setArgs(args).build())
                def execDir = command.executionSubdirectory
                    ? new File(sample.projectDir, command.executionSubdirectory)
                    : sample.projectDir
                def buildSrcKotlinBuildScript = new File(execDir, "buildSrc/build.gradle.kts")
                if (buildSrcKotlinBuildScript.file) {
                    buildSrcKotlinBuildScripts.add(buildSrcKotlinBuildScript)
                }
            } else {
                modifiedCommands.add(command)
            }
        }
        for (File buildSrcBuildScript : buildSrcKotlinBuildScripts.unique()) {
            buildSrcBuildScript << """
                    repositories {
                        ${RepoScriptBlockUtil.kotlinEapRepositoryDefinition(GradleDsl.KOTLIN)}
                    }
                """.stripIndent()
        }
        return new Sample(sample.id, sample.projectDir, modifiedCommands);
    }
}
