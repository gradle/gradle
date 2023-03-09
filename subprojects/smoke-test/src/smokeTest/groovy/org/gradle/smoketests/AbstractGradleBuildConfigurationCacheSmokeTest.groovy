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

package org.gradle.smoketests

import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheMaxProblemsOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions

/**
 * Smoke test building gradle/gradle with configuration cache enabled.
 *
 * gradle/gradle requires Java >=9 and <=11 to build, see {@link AbstractGradleceptionSmokeTest.GradleBuildJvmSpec}.
 */
@Requires([
    UnitTestPreconditions.Jdk9OrLater,
    IntegTestPreconditions.NotConfigCached,
    IntegTestPreconditions.GradleBuildJvmSpecAvailable
])
abstract class AbstractGradleBuildConfigurationCacheSmokeTest extends AbstractGradleceptionSmokeTest {
    def setup() {
        // Generate Kotlin DSL sources once so they are included as :kotlin-dsl:compileKotlin inputs.
        // TODO:configuration-cache handle generated sources better (see gradlebuild.kotlin-dsl-dependencies-embedded.gradle.kts:39)
        run([':kotlin-dsl:generateKotlinDependencyExtensions'])
    }

    @Override
    protected void assertConfigurationCacheStateStored() {
        assert result.output.count("Calculating task graph as no configuration cache is available") == 1
    }

    @Override
    protected void assertConfigurationCacheStateLoaded() {
        assert result.output.count("Reusing configuration cache") == 1
    }

    TestExecutionResult assertTestClassExecutedIn(String subProjectDir, String testClass) {
        new DefaultTestExecutionResult(file(subProjectDir), "build", "", "", "embeddedIntegTest")
            .assertTestClassesExecuted(testClass)
    }

    protected int maxConfigurationCacheProblems = 0

    void configurationCacheRun(List<String> tasks, int daemonId = 0) {
        def ccOptions = [
            // TODO: the version of KGP we use still accesses Task.project from a cacheIf predicate
            "-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true",
            "--stacktrace",
            "--${ConfigurationCacheOption.LONG_OPTION}".toString(),
        ]
        if (maxConfigurationCacheProblems > 0) {
            ccOptions += [
                "--${ConfigurationCacheProblemsOption.LONG_OPTION}=warn".toString(),
                "-D${ConfigurationCacheMaxProblemsOption.PROPERTY_NAME}=$maxConfigurationCacheProblems".toString(),
            ]
        }
        run(
            tasks + ccOptions,
            // use a unique testKitDir per daemonId other than 0 as 0 means default daemon.
            daemonId != 0 ? file("test-kit/$daemonId") : null
        )
    }
}
