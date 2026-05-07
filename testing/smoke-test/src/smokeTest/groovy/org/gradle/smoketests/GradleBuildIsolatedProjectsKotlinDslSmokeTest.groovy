/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.kotlin.dsl.tooling.fixtures.FetchKotlinDslScriptsModelForAllBuilds
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import static org.gradle.kotlin.dsl.tooling.fixtures.KotlinDslModelChecker.checkBuildTreeScriptsModels

class GradleBuildIsolatedProjectsKotlinDslSmokeTest extends AbstractGradleceptionSmokeTest {

    def "KotlinDslScriptsModel for all builds for IP and non-IP mode are structurally equal"() {
        when:
        def runner = runner()
        def originalModel = fetchBuildTreeScriptsModels(runner)

        then:
        originalModel != null

        when:
        def isolatedModel = fetchBuildTreeScriptsModels(runner, '-Dorg.gradle.unsafe.isolated-projects=true')

        then:
        isolatedModel != null
        checkBuildTreeScriptsModels(isolatedModel, originalModel)
    }

    private Map<String, KotlinDslScriptsModel> fetchBuildTreeScriptsModels(SmokeTestGradleRunner runner, String... extraArgs) {
        try (ProjectConnection connection = GradleConnector.newConnector()
            .useGradleUserHomeDir(IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir)
            .useInstallation(IntegrationTestBuildContext.INSTANCE.gradleHomeDir)
            .forProjectDirectory(testProjectDir)
            .connect()) {
            def actionBuilder = connection.action(new FetchKotlinDslScriptsModelForAllBuilds())
                .addArguments(runner.arguments)
                .addJvmArguments(runner.jvmArguments)
                .setStandardOutput(System.out)
                .setStandardError(System.err)
            if (extraArgs) {
                actionBuilder.addArguments(extraArgs as List)
            }
            return actionBuilder.run()
        }
    }
}
