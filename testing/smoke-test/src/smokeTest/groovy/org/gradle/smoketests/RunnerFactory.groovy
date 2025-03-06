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

package org.gradle.smoketests

import groovy.transform.SelfType
import org.gradle.util.internal.VersionNumber

/**
 * Implementing this trait means that a class knows how to create runners for testing Android and Kotlin plugins.
 */
@SelfType(AbstractSmokeTest)
trait RunnerFactory {

    SmokeTestGradleRunner mixedRunner(boolean parallel, String agpVersion, VersionNumber kgpVersion, String... tasks) {
        def runner = kgpRunner(parallel, kgpVersion, tasks)
        return newAgpRunner(agpVersion, runner)
    }

    SmokeTestGradleRunner agpRunner(String agpVersion, String... tasks) {
        return newAgpRunner(agpVersion, runner(tasks))
    }

    SmokeTestGradleRunner kgpRunner(boolean parallel, VersionNumber kotlinVersion, String... tasks) {
        newKotlinRunner(parallel, kotlinVersion, tasks.toList())
    }

    private SmokeTestGradleRunner newAgpRunner(String agpVersion, SmokeTestGradleRunner runner) {
        def extraArgs = []
        if (AGP_VERSIONS.isAgpNightly(agpVersion)) {
            def init = AGP_VERSIONS.createAgpNightlyRepositoryInitScript()
            extraArgs += ["-I", init.canonicalPath]
        }
        if (VersionNumber.parse(agpVersion) < VersionNumber.parse("7.4.0")) {
            runner.ignoreStackTraces("AGP $agpVersion outputs debug stacktraces")
        }
        return runner.withArguments([runner.arguments, extraArgs].flatten())
            .ignoreDeprecationWarningsIf(AGP_VERSIONS.isOld(agpVersion), "Old AGP version")
    }

    private SmokeTestGradleRunner newKotlinRunner(boolean parallel, VersionNumber kotlinVersion, List<String> tasks) {
        List<String> args = []

        // Parallel workers in Kotlin is enabled by Gradle's --parallel flag. See https://youtrack.jetbrains.com/issue/KT-46401/
        // For context on why we test with parallel workers see https://github.com/gradle/gradle/pull/10404
        if (parallel) {
            args = ["--parallel"]
        }

        runner(*(tasks + args))
            .ignoreDeprecationWarningsIf(KOTLIN_VERSIONS.isOld(kotlinVersion), "Old KGP version")
    }
}
