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
import org.gradle.performance.fixture.IncrementalAndroidTestProject
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.ScenarioContext
import org.gradle.profiler.mutations.AbstractCleanupMutator
import org.gradle.profiler.mutations.AbstractFileChangeMutator
import org.gradle.profiler.mutations.ClearArtifactTransformCacheMutator
import spock.lang.Issue

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.fixture.AndroidTestProject.LARGE_ANDROID_BUILD
import static org.gradle.performance.results.OperatingSystem.LINUX

class RealLifeAndroidBuildPerformanceTest extends AbstractCrossVersionPerformanceTest implements AndroidPerformanceTestFixture {

    String agpVersion
    String kgpVersion

    def setup() {
        runner.args = [AndroidGradlePluginVersions.OVERRIDE_VERSION_CHECK]
        agpVersion = AndroidTestProject.useAgpLatestStableOrRcVersion(runner)
        kgpVersion = AndroidTestProject.useKotlinLatestStableOrRcVersion(runner)
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = "largeAndroidBuild", iterationMatcher = "run help"),
        @Scenario(type = PER_COMMIT, operatingSystems = LINUX, testProjects = ["largeAndroidBuild", "santaTrackerAndroidBuild", "nowInAndroidBuild"], iterationMatcher = "run assembleDebug"),
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
        if (IncrementalAndroidTestProject.NOW_IN_ANDROID == testProject) {
            configureRunnerSpecificallyForNowInAndroid()
        }
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
        @Scenario(type = PER_DAY, operatingSystems = LINUX, testProjects = ["largeAndroidBuild", "santaTrackerAndroidBuild", "nowInAndroidBuild"], iterationMatcher = "clean assemble.*"),
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
        if (IncrementalAndroidTestProject.NOW_IN_ANDROID == testProject) {
            configureRunnerSpecificallyForNowInAndroid()
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
        runner.setMinimumBaseVersion('8.3')
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

    private void configureRunnerSpecificallyForNowInAndroid() {
        runner.gradleOpts.addAll([
            "--add-opens",
            "java.base/java.util=ALL-UNNAMED",
            "--add-opens",
            "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
            "--add-opens",
            "java.base/java.lang=ALL-UNNAMED",
            "--add-opens",
            "java.base/java.lang.invoke=ALL-UNNAMED",
            "--add-opens",
            "java.base/java.net=ALL-UNNAMED"
        ]) // needed when tests are being run with CC on, see https://github.com/gradle/gradle/issues/22765
        runner.addBuildMutator { is -> new PluginRemoveMutator(is) }
        runner.addBuildMutator {is -> new AgpAndKgpVersionMutator(is, agpVersion, kgpVersion) }
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

    private static class AgpAndKgpVersionMutator extends AbstractFileChangeMutator {

        private final def agpVersion
        private final def kgpVersion

        protected AgpAndKgpVersionMutator(InvocationSettings invocationSettings, String agpVersion, String kgpVersion) {
            super(new File(new File(invocationSettings.projectDir, "gradle"), "libs.versions.toml"))
            this.agpVersion = agpVersion
            this.kgpVersion = kgpVersion
        }

        @Override
        protected void applyChangeTo(BuildContext context, StringBuilder text) {
            replace(text, "androidGradlePlugin = \"8.1.1\"", "androidGradlePlugin = \"$agpVersion\"")
            replace(text, "kotlin = \"1.9.0\"", "kotlin = \"$kgpVersion\"")
            replace(text, "androidxComposeCompiler = \"1.5.0\"", "androidxComposeCompiler = \"1.5.3\"") // TODO: no good, hardcoding
            replace(text, "ksp = \"1.9.0-1.0.13\"", "ksp = \"1.9.10-1.0.13\"") // TODO: no good, hardcoding
        }
    }

    // TODO: temporary workaround for https://github.com/gradle/gradle/issues/26462
    private static class PluginRemoveMutator extends AbstractFileChangeMutator {
        protected PluginRemoveMutator(InvocationSettings invocationSettings) {
            super(new File(new File(invocationSettings.projectDir, "app"), "build.gradle.kts"))
        }

        @Override
        protected void applyChangeTo(BuildContext context, StringBuilder text) {
            replace(text, "id(\"com.google.android.gms.oss-licenses-plugin\")", "")
        }
    }

    private static void replace(StringBuilder text, String target, String replacement) {
        def searchString = target
        def start = text.indexOf(searchString)
        text.delete(start, start + searchString.length())
        text.insert(start, replacement)
    }
}
