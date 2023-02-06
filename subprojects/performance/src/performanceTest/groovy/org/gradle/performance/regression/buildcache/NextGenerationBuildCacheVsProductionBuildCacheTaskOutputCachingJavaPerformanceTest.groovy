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

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.CrossBuildPerformanceTestRunner
import org.gradle.performance.fixture.GradleBuildExperimentSpec
import org.gradle.performance.fixture.JavaTestProject
import org.gradle.performance.results.BaselineVersion
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.profiler.mutations.ApplyAbiChangeToJavaSourceFileMutator
import org.gradle.profiler.mutations.ApplyNonAbiChangeToJavaSourceFileMutator
import org.gradle.test.fixtures.keystore.TestKeyStore

import java.util.function.Consumer

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX
import static org.gradle.performance.results.PerformanceTestResult.hasRegressionChecks
/**
 * This test tests Next generation build cache vs production build cache. The version we test will always use next generation build cache
 * and baseline will use production build cache.
 */
@RunFor(
    @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject", "largeMonolithicJavaProject"])
)
class NextGenerationBuildCacheVsProductionBuildCacheTaskOutputCachingJavaPerformanceTest extends AbstractTaskOutputCachingCrossBuildTest {

    private static final String NG_BUILD_CACHE_DISPLAY_NAME = "Next generation build cache"
    private static final String PRODUCTION_BUILD_CACHE_DISPLAY_NAME = "Production build cache"

    boolean callClean

    def setup() {
        callClean = true
        runner.testGroup = "Build cache NG"
    }

    def "clean assemble with remote http cache"() {
        setupTestProject(runner) { GradleBuildExperimentSpec.GradleBuilder builder ->
            builder.warmUpCount = 2
            builder.invocationCount = 8
            builder.invocation.useDaemon = false
            builder.addBuildMutator { cleanLocalCache() }
        }
        pushToRemote = true
        protocol = "http"

        when:
        def result = runner.run()

        then:
        assertNotRegressed(result)
    }

    def "clean assemble with remote https cache"() {
        def keyStore = TestKeyStore.init(temporaryFolder.file('ssl-keystore'))
        keyStore.enableSslWithServerCert(buildCacheServer)
        setupTestProject(runner) { GradleBuildExperimentSpec.GradleBuilder builder ->
            builder.warmUpCount = 2
            builder.invocationCount = 8
            builder.invocation.useDaemon = false
            builder.addBuildMutator { cleanLocalCache() }
            builder.invocation.args.addAll(keyStore.serverAndClientCertArgs)
        }
        protocol = "https"
        pushToRemote = true

        when:
        def result = runner.run()

        then:
        assertNotRegressed(result)
    }

    def "clean assemble with empty local cache"() {
        given:
        setupTestProject(runner) { GradleBuildExperimentSpec.GradleBuilder builder ->
            builder.warmUpCount = 2
            builder.invocationCount = 8
            builder.invocation.useDaemon = false
            builder.addBuildMutator { cleanLocalCache() }
        }
        pushToRemote = false

        when:
        def result = runner.run()

        then:
        assertNotRegressed(result)
    }

    def "clean assemble with empty remote http cache"() {
        given:
        setupTestProject(runner) { GradleBuildExperimentSpec.GradleBuilder builder ->
            builder.warmUpCount = 2
            builder.invocationCount = 8
            builder.invocation.useDaemon = false
            builder.addBuildMutator { cleanLocalCache() }
            builder.addBuildMutator { cleanRemoteCache() }
        }
        pushToRemote = true

        when:
        def result = runner.run()

        then:
        assertNotRegressed(result)
    }

    @RunFor(
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject", "largeMonolithicJavaProject"])
    )
    def "clean assemble with local cache"() {
        given:
        setupTestProject(runner) { GradleBuildExperimentSpec.GradleBuilder builder ->
            builder.invocation.args += "--parallel"
        }
        pushToRemote = false

        when:
        def result = runner.run()

        then:
        assertNotRegressed(result)
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject"],
            comment = "We only test the multi-project here since for the monolithic project we would have no cache hits. This would mean we actually would test incremental compilation."
        )
    ])
    def "clean assemble for abi change with local cache"() {
        given:
        def testProject = JavaTestProject.projectFor(runner.testProject)
        setupTestProject(runner) { GradleBuildExperimentSpec.GradleBuilder builder ->
            builder.addBuildMutator { new ApplyAbiChangeToJavaSourceFileMutator(new File(it.projectDir, testProject.config.fileToChangeByScenario['assemble'])) }
            builder.invocation.args += "--parallel"
        }
        pushToRemote = false

        when:
        def result = runner.run()

        then:
        assertNotRegressed(result)
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject"],
            comment = "We only test the multi-project here since for the monolithic project we would have no cache hits. This would mean we actually would test incremental compilation."
        )
    ])
    def "clean assemble for non-abi change with local cache"() {
        given:
        def testProject = JavaTestProject.projectFor(runner.testProject)
        setupTestProject(runner) { GradleBuildExperimentSpec.GradleBuilder builder ->
            builder.addBuildMutator { new ApplyNonAbiChangeToJavaSourceFileMutator(new File(it.projectDir, testProject.config.fileToChangeByScenario['assemble'])) }
            builder.invocation.args += "--parallel"
        }
        pushToRemote = false

        when:
        def result = runner.run()

        then:
        assertNotRegressed(result)
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject"],
            comment = "We only test the multi-project here since for the monolithic project we would have no cache hits. This would mean we actually would test incremental compilation."
        )
    ])
    def "no-clean assemble for non-abi change with local cache"() {
        given:
        callClean = false
        def testProject = JavaTestProject.projectFor(runner.testProject)
        setupTestProject(runner) { GradleBuildExperimentSpec.GradleBuilder builder ->
            builder.addBuildMutator { new ApplyNonAbiChangeToJavaSourceFileMutator(new File(it.projectDir, testProject.config.fileToChangeByScenario['assemble'])) }
            builder.invocation.args += "--parallel"
        }
        pushToRemote = false

        when:
        def result = runner.run()

        then:
        assertNotRegressed(result)
    }

    def setupTestProject(CrossBuildPerformanceTestRunner runner, Consumer<GradleBuildExperimentSpec.GradleBuilder> configure) {
        Consumer<GradleBuildExperimentSpec.GradleBuilder> defaultConfiguration = { GradleBuildExperimentSpec.GradleBuilder builder ->
            builder.invocation {
                tasksToRun("assemble")
                if (callClean) {
                    cleanTasks("clean")
                }
                args("-D${StartParameterBuildOptions.BuildCacheOption.GRADLE_PROPERTY}=true")
            }
            builder.warmUpCount = 11
            builder.invocationCount = 21
        }
        runner.buildSpec { GradleBuildExperimentSpec.GradleBuilder builder ->
            builder.displayName(NG_BUILD_CACHE_DISPLAY_NAME)
            builder.invocation {
                args("-Dorg.gradle.unsafe.cache.ng=true")
            }
            defaultConfiguration.accept(builder)
            configure.accept(builder)
        }
        runner.baseline { GradleBuildExperimentSpec.GradleBuilder builder ->
            builder.displayName(PRODUCTION_BUILD_CACHE_DISPLAY_NAME)
            defaultConfiguration.accept(builder)
            configure.accept(builder)
        }
    }

    static def assertNotRegressed(CrossBuildPerformanceResults result) {
        def nextGeneration = result.buildResult(NG_BUILD_CACHE_DISPLAY_NAME)
        def production = result.buildResult(PRODUCTION_BUILD_CACHE_DISPLAY_NAME)

        def productionResults = new BaselineVersion("Production")
        productionResults.results.addAll(production)
        def stats = productionResults.getSpeedStatsAgainst("Next-Generation", nextGeneration)
        println(stats)
        boolean isProductionFaster = productionResults.significantlyFasterThan(nextGeneration)
        if (isProductionFaster && hasRegressionChecks()) {
            throw new AssertionError(stats as Object)
        }
        return result
    }
}
