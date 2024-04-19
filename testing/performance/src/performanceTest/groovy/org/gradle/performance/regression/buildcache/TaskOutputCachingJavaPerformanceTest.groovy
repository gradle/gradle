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

package org.gradle.performance.regression.buildcache

import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.CrossVersionPerformanceTestRunner
import org.gradle.performance.fixture.JavaTestProject
import org.gradle.profiler.mutations.AbstractCleanupMutator
import org.gradle.profiler.mutations.ApplyAbiChangeToJavaSourceFileMutator
import org.gradle.profiler.mutations.ApplyNonAbiChangeToJavaSourceFileMutator
import org.gradle.profiler.mutations.ClearGradleUserHomeMutator
import org.gradle.profiler.mutations.ClearProjectCacheMutator
import org.gradle.test.fixtures.keystore.TestKeyStore

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject", "largeMonolithicJavaProject"])
)
class TaskOutputCachingJavaPerformanceTest extends AbstractTaskOutputCachingPerformanceTest {

    def setup() {
        runner.warmUpRuns = 11
        runner.runs = 21
        runner.minimumBaseVersion = "3.5"
    }

    def "clean assemble with remote http cache"() {
        setupTestProject(runner)
        protocol = "http"
        pushToRemote = true
        runner.useDaemon = false
        runner.warmUpRuns = 2
        runner.runs = 8
        runner.addBuildMutator { cleanLocalCache() }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    @RunFor(
        @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["smallJavaMultiProjectManyExternalDependencies"])
    )
    /*
     * Similar to the "first use" scenario, because ephemeral agents have no local caches, but we do have a well-populated remote
     * cache. This scenario measures how much overhead Gradle's startup and input fingerprinting add on top of the cache hits.
     */
    def "clean check on ephemeral ci with remote http cache"() {
        runner.cleanTasks = ["clean"]
        runner.tasksToRun = ["check"]
        protocol = "http"
        pushToRemote = true
        runner.useDaemon = false
        runner.warmUpRuns = 1
        runner.runs = 3
        runner.addBuildMutator { invocationSettings ->
            new ClearGradleUserHomeMutator(invocationSettings.gradleUserHome, AbstractCleanupMutator.CleanupSchedule.BUILD)
        }
        runner.addBuildMutator { invocationSettings ->
            new ClearProjectCacheMutator(invocationSettings.projectDir, AbstractCleanupMutator.CleanupSchedule.BUILD)
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "clean assemble with remote https cache"() {
        setupTestProject(runner)
        protocol = "https"
        pushToRemote = true
        runner.useDaemon = false
        runner.warmUpRuns = 2
        runner.runs = 8
        runner.addBuildMutator { cleanLocalCache() }

        def keyStore = TestKeyStore.init(temporaryFolder.file('ssl-keystore'))
        keyStore.enableSslWithServerCert(buildCacheServer)

        runner.gradleOpts.addAll(keyStore.serverAndClientCertArgs)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "clean assemble with empty local cache"() {
        given:
        setupTestProject(runner)
        runner.warmUpRuns = 2
        runner.runs = 8
        pushToRemote = false
        runner.useDaemon = false
        runner.addBuildMutator { cleanLocalCache() }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "clean assemble with empty remote http cache"() {
        given:
        setupTestProject(runner)
        runner.warmUpRuns = 2
        runner.runs = 8
        pushToRemote = true
        runner.useDaemon = false
        runner.addBuildMutator { cleanLocalCache() }
        runner.addBuildMutator { cleanRemoteCache() }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    @RunFor(
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject", "largeMonolithicJavaProject"])
    )
    def "clean assemble with local cache"() {
        given:
        setupTestProject(runner)
        runner.args += "--parallel"
        pushToRemote = false

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject"],
            comment = "We only test the multi-project here since for the monolithic project we would have no cache hits. This would mean we actually would test incremental compilation."
        )
    ])
    def "clean assemble for abi change with local cache"() {
        given:
        setupTestProject(runner)
        def testProject = JavaTestProject.projectFor(runner.testProject)
        runner.addBuildMutator { new ApplyAbiChangeToJavaSourceFileMutator(new File(it.projectDir, testProject.config.fileToChangeByScenario['assemble'])) }
        runner.args += "--parallel"
        pushToRemote = false

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject"],
            comment = "We only test the multi-project here since for the monolithic project we would have no cache hits. This would mean we actually would test incremental compilation."
        )
    ])
    def "clean assemble for non-abi change with local cache"() {
        given:
        setupTestProject(runner)
        def testProject = JavaTestProject.projectFor(runner.testProject)
        runner.addBuildMutator { new ApplyNonAbiChangeToJavaSourceFileMutator(new File(it.projectDir, testProject.config.fileToChangeByScenario['assemble'])) }
        runner.args += "--parallel"
        pushToRemote = false

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    static def setupTestProject(CrossVersionPerformanceTestRunner runner) {
        runner.tasksToRun = ["assemble"]
        runner.cleanTasks = ["clean"]
    }
}
