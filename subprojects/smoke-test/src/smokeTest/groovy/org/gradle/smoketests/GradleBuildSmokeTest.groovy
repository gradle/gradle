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

package org.gradle.smoketests

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir

class GradleBuildSmokeTest extends AbstractGradleceptionSmokeTest {

    @TempDir
    File targetDir

    def "can build Gradle distribution"() {
        def runner = runner(':distributions-full:binDistributionZip', ':distributions-full:binInstallation', '--stacktrace')
//            .expectDeprecationWarning("The AbstractCompile.destinationDir property has been deprecated. " +
//                "This is scheduled to be removed in Gradle 8.0. " +
//                "Please use the destinationDirectory property instead. " +
//                "Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_7.html#compile_task_wiring",
//                "https://youtrack.jetbrains.com/issue/KT-46019")
            .ignoreDeprecationWarnings("https://github.com/gradle/gradle-private/issues/3405")

        runner.withJvmArguments(runner.jvmArguments + [
            // TODO: the version of KGP we use still accesses Task.project from a cacheIf predicate
            "-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true",
        ])

        when:
        result = runner.build()

        then:
        result.task(":distributions-full:binDistributionZip").outcome == TaskOutcome.SUCCESS
        result.task(":distributions-full:binInstallation").outcome == TaskOutcome.SUCCESS
    }

    def "can install Gradle distribution over itself"() {
        def runner = runner('install', "-Pgradle_installPath=$targetDir", '--stacktrace')
            .ignoreDeprecationWarnings("https://github.com/gradle/gradle-private/issues/3405")

        when:
        result = runner.build()
        result = runner.build()

        then:
        result.task(":distributions-full:install").outcome == TaskOutcome.SUCCESS
        new File(targetDir, "bin/gradle").exists()
        new File(targetDir, "LICENSE").exists()
    }
}
