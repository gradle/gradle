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

package org.gradle.performance.regression.android

import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.internal.service.scopes.VirtualFileSystemServices
import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.categories.PerformanceExperiment
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.GradleBuildExperimentSpec
import org.gradle.performance.regression.java.JavaInstantExecutionPerformanceTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.performance.regression.android.IncrementalAndroidTestProject.SANTA_TRACKER_JAVA
import static org.gradle.performance.regression.android.IncrementalAndroidTestProject.SANTA_TRACKER_KOTLIN

@Category(PerformanceExperiment)
class FasterIncrementalAndroidBuildsPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll
    def "faster incremental build on #testProject (build comparison)"() {
        given:
        runner.testGroup = "incremental android changes"
        def defaultArgs = ["-Dorg.gradle.workers.max=8", "--no-build-cache", "--no-scan"]

        // Kotlin is not supported for instant execution
        def optimizations = testProject == SANTA_TRACKER_KOTLIN
            ? [
                "no optimizations": EnumSet.noneOf(Optimization),
                "VFS retention": EnumSet.of(Optimization.VFS_RETENTION)
            ]
            : [
                "no optimizations": EnumSet.noneOf(Optimization),
                "VFS retention": EnumSet.of(Optimization.VFS_RETENTION),
                "instant execution": EnumSet.of(Optimization.INSTANT_EXECUTION),
                "all optimizations": EnumSet.allOf(Optimization)
            ]

        optimizations.each { name, Set<Optimization> enabledOptimizations ->
            runner.buildSpec {
                testProject.configureForNonAbiChange(it)
                passChangedFile(it, testProject)
                invocation.args(*defaultArgs, *enabledOptimizations*.argument)
                displayName("non abi change (${name})")
            }
            runner.buildSpec {
                testProject.configureForAbiChange(it)
                passChangedFile(it, testProject)
                invocation.args(*defaultArgs, *enabledOptimizations*.argument)
                displayName("abi change (${name})")
            }
        }

        when:
        def results = runner.run()
        then:
        results

        where:
        testProject << [SANTA_TRACKER_KOTLIN, SANTA_TRACKER_JAVA]
    }

    @Override
    protected void defaultSpec(BuildExperimentSpec.Builder builder) {
        if (builder instanceof GradleBuildExperimentSpec.GradleBuilder) {
            builder.invocation.args(AndroidGradlePluginVersions.OVERRIDE_VERSION_CHECK)
        }
    }

    static void passChangedFile(GradleBuildExperimentSpec.GradleBuilder builder, IncrementalAndroidTestProject testProject) {
        builder.invocation.args("-D${VirtualFileSystemServices.VFS_CHANGES_SINCE_LAST_BUILD_PROPERTY}=${testProject.pathToChange}")
    }

    enum Optimization {
        INSTANT_EXECUTION(JavaInstantExecutionPerformanceTest.INSTANT_EXECUTION_ENABLED_PROPERTY),
        VFS_RETENTION(VirtualFileSystemServices.VFS_RETENTION_ENABLED_PROPERTY)

        Optimization(String systemProperty) {
            this.argument = "-D${systemProperty}=true"
        }

        final String argument
    }
}
