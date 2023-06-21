/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.AndroidTestProject
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.ScenarioContext
import org.gradle.profiler.mutations.AbstractCleanupMutator
import org.gradle.profiler.mutations.ClearArtifactTransformCacheMutator
import spock.lang.Issue

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.fixture.AndroidTestProject.LARGE_ANDROID_BUILD
import static org.gradle.performance.results.OperatingSystem.LINUX

class RealLifeAndroidBuildPerformanceTest extends AbstractCrossVersionPerformanceTest implements AndroidPerformanceTestFixture {

    def setup() {
        runner.args = [AndroidGradlePluginVersions.OVERRIDE_VERSION_CHECK]
        AndroidTestProject.useAgpLatestStableOrRcVersion(runner)
        AndroidTestProject.useKotlinLatestStableOrRcVersion(runner)
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = "largeAndroidBuild", iterationMatcher = "run help"),
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = ["largeAndroidBuild", "santaTrackerAndroidBuild"], iterationMatcher = "run assembleDebug"),
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = "largeAndroidBuild", iterationMatcher = ".*phthalic.*"),
        // @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = "largeAndroidBuild2", iterationMatcher = ".*module21.*"),
    ])
    def "run #tasks"() {
        given:
        AndroidTestProject testProject = androidTestProject
        testProject.configure(runner)
        runner.tasksToRun = tasks.split(' ')
        runner.args.add('-Dorg.gradle.parallel=true')
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        applyEnterprisePlugin()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        tasks                                        | warmUpRuns | runs
        'help'                                       | null       | null
        'assembleDebug'                              | null       | null
        'clean phthalic:assembleDebug'               | 2          | 8
        ':module21:module02:assembleDebug --dry-run' | 8          | 20
    }

    @RunFor([
        @Scenario(type = PER_DAY, operatingSystems = LINUX, testProjects = ["largeAndroidBuild", "santaTrackerAndroidBuild"], iterationMatcher = "clean assemble.*"),
        @Scenario(type = PER_DAY, operatingSystems = LINUX, testProjects = "largeAndroidBuild", iterationMatcher = "clean phthalic.*")
    ])
    def "clean #tasks with clean transforms cache"() {
        given:
        def testProject = androidTestProject
        boolean isLargeProject = androidTestProject == LARGE_ANDROID_BUILD
        if (isLargeProject) {
            runner.warmUpRuns = 2
            runner.runs = 8
        }

        testProject.configure(runner)
        runner.tasksToRun = tasks.split(' ')
        runner.args.add('-Dorg.gradle.parallel=true')
        runner.cleanTasks = ["clean"]
        runner.useDaemon = false
        runner.addBuildMutator { invocationSettings ->
            new ClearArtifactTransformCacheMutator(invocationSettings.getGradleUserHome(), AbstractCleanupMutator.CleanupSchedule.BUILD)
        }
        applyEnterprisePlugin()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        tasks << ['assembleDebug', 'phthalic:assembleDebug']
    }

    @Issue("https://github.com/gradle/gradle/issues/25361")
    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = "largeAndroidBuild"),
    ])
    def "calculate task graph with test finalizer"() {
        given:
        AndroidTestProject testProject = androidTestProject
        testProject.configure(runner)
        runner.addBuildMutator {invocation -> new TestFinalizerMutator(invocation) }
        runner.tasksToRun = [':phthalic:test', '--dry-run']
        runner.args.add('-Dorg.gradle.parallel=true')
        runner.warmUpRuns = 2
        runner.runs = 8
        applyEnterprisePlugin()

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    private class TestFinalizerMutator implements BuildMutator {
        private final InvocationSettings invocation

        TestFinalizerMutator(InvocationSettings invocation) {
            this.invocation = invocation
        }

        @Override
        void beforeScenario(ScenarioContext context) {
            def buildFile = new File(invocation.projectDir, "build.gradle")
            buildFile << """
                def finalizerTask = tasks.register("testFinalizer")
                subprojects {
                    tasks.withType(com.android.build.gradle.tasks.factory.AndroidUnitTest) { testTask ->
                        testTask.finalizedBy(finalizerTask)
                        finalizerTask.configure {
                            dependsOn testTask
                        }
                    }
                }
            """.stripIndent()
        }
    }
}
