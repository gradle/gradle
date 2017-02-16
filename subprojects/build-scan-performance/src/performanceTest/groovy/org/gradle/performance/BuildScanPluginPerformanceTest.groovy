/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.performance

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.performance.categories.PerformanceRegressionTest
import org.gradle.performance.fixture.BuildExperimentRunner
import org.gradle.performance.fixture.BuildExperimentSpec
import org.gradle.performance.fixture.BuildScanPerformanceTestRunner
import org.gradle.performance.fixture.CrossBuildPerformanceTestRunner
import org.gradle.performance.fixture.GradleSessionProvider
import org.gradle.performance.results.BuildScanResultsStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.junit.experimental.categories.Category
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static org.gradle.performance.measure.Duration.millis

@Category(PerformanceRegressionTest)
class BuildScanPluginPerformanceTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    @AutoCleanup
    @Shared
    def resultStore = new BuildScanResultsStore()

    private static final String WITH_PLUGIN_LABEL = "with plugin"
    private static final String WITHOUT_PLUGIN_LABEL = "without plugin"

    protected final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    CrossBuildPerformanceTestRunner runner

    private int warmupBuilds = 1
    private int measuredBuilds = 7

    void setup() {
        def incomingDir = "../../incoming" // System.getProperty('incomingArtifactDir')
        assert incomingDir: "'incomingArtifactDir' system property is not set"
        def versionJsonFile = new File(incomingDir, "version.json")
        assert versionJsonFile.exists()

        def versionJsonData = new JsonSlurper().parse(versionJsonFile) as Map<String, ?>
        assert versionJsonData.commitId
        def pluginCommitId = versionJsonData.commitId as String

        runner = new BuildScanPerformanceTestRunner(new BuildExperimentRunner(new GradleSessionProvider(buildContext)), resultStore, pluginCommitId, buildContext) {
            @Override
            protected void defaultSpec(BuildExperimentSpec.Builder builder) {
                super.defaultSpec(builder)
                builder.workingDirectory = tmpDir.testDirectory
            }
        }
    }

    def "build scan plugin comparison"() {
        given:
        def sourceProject = "largeJavaProjectWithBuildScanPlugin"
        def tasks = ['clean', 'build']
        def jobArgs = ['--continue', '--parallel', '--max-workers=2', '-q']
        def opts = ['-Xms256m', '-Xmx256m']

        runner.testGroup = "build scan plugin"
        runner.testId = "large java project with and without build scan"


        runner.baseline {
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            projectName(sourceProject)
            displayName(WITHOUT_PLUGIN_LABEL)
            invocation {
                args(*jobArgs)
                tasksToRun(*tasks)
                gradleOpts(*opts)
                expectFailure()
            }
        }

        runner.buildSpec {
            warmUpCount warmupBuilds
            invocationCount measuredBuilds
            projectName(sourceProject)
            displayName(WITH_PLUGIN_LABEL)
            invocation {
                args(*jobArgs)
                args("-Dscan", "-Dscan.dump")
                tasksToRun(*tasks)
                gradleOpts(*opts)
                expectFailure()
            }
        }

        when:
        def results = runner.run()

        then:
        def (with, without) = [results.buildResult(WITH_PLUGIN_LABEL), results.buildResult(WITHOUT_PLUGIN_LABEL)]

        // cannot be more than 1s slower
        with.totalTime.average - without.totalTime.average < millis(1000)
    }
}
