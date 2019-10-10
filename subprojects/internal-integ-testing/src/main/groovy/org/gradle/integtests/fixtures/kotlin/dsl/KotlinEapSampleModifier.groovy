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
import org.gradle.samples.model.Sample
import org.gradle.samples.test.runner.SampleModifier
import org.gradle.test.fixtures.dsl.GradleDsl


/**
 * Modify samples to use the kotlin-eap repository.
 *
 * This modifier is intended to be used when testing integration of Kotlin EAPs.
 */
class KotlinEapSampleModifier implements SampleModifier {

    @Override
    Sample modify(Sample sample) {
        def execDirs = sample.commands
            .findAll { it.executable == "gradle" }
            .collect { command ->
                command.executionSubdirectory
                    ? new File(sample.projectDir, command.executionSubdirectory)
                    : sample.projectDir
            }
            .unique()
        for (File execDir : execDirs) {
            def buildSrcBuildScript = new File(execDir, "buildSrc/build.gradle.kts")
            if (buildSrcBuildScript.file) {
                buildSrcBuildScript << """
                    repositories {
                        ${RepoScriptBlockUtil.kotlinEapRepositoryDefinition(GradleDsl.KOTLIN)}
                    }
                """.stripIndent()
            }
        }
        return sample
    }
}
