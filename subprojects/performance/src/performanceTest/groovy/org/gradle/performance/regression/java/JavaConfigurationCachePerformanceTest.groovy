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

package org.gradle.performance.regression.java

import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.categories.PerformanceRegressionTest
import org.gradle.performance.fixture.GradleBuildExperimentRunner
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.instrument.PidInstrumentation
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import java.nio.file.Files

import static org.junit.Assert.assertTrue

@Category(PerformanceRegressionTest)
class JavaConfigurationCachePerformanceTest extends AbstractCrossVersionPerformanceTest {
    private File stateDirectory

    def setup() {
        stateDirectory = temporaryFolder.file(".gradle/configuration-cache")
    }

    // Disable pid instrumentation in case of configuration cache code daemon
    // Otherwise it complains: Gradle daemon was reused but should not be reused because init script is not executed properly
    // https://github.com/gradle/gradle-profiler/pull/238
    private void disablePidInstrumentationOnCodeDaemon(String daemon) {
        if (daemon == 'cold') {
            (runner.experimentRunner as GradleBuildExperimentRunner).with {
                it.pidInstrumentation = new PidInstrumentation() {
                    @Override
                    void calculateGradleArgs(List<String> gradleArgs) {
                    }

                    @Override
                    String getPidForLastBuild() {
                        return UUID.randomUUID().toString()
                    }
                }
            }
        }
    }

    @Unroll
    def "assemble #action configuration cache state with #daemon daemon"() {
        given:
        runner.targetVersions = ["6.7-20200915230440+0000"]
        runner.minimumBaseVersion = "6.6"
        runner.tasksToRun = ["assemble"]
        runner.args = ["-D${ConfigurationCacheOption.PROPERTY_NAME}=true"]

        and:
        disablePidInstrumentationOnCodeDaemon(daemon)
        runner.useDaemon = daemon == hot
        runner.addBuildMutator { configurationCacheInvocationListenerFor(it, action, stateDirectory) }
        runner.warmUpRuns = daemon == hot ? 20 : 1
        runner.runs = daemon == hot ? 60 : 25

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        daemon | action
        hot    | loading
        hot    | storing
        cold   | loading
        cold   | storing
    }

    static String loading = "loading"
    static String storing = "storing"
    static String hot = "hot"
    static String cold = "cold"

    static BuildMutator configurationCacheInvocationListenerFor(InvocationSettings invocationSettings, String action, File stateDirectory) {
        return new BuildMutator() {
            @Override
            void beforeBuild(BuildContext context) {
                if (action == storing) {
                    stateDirectory.deleteDir()
                }
            }

            @Override
            void afterBuild(BuildContext context, Throwable error) {
                if (context.iteration > 1) {
                    def tag = action == storing
                        ? "Calculating task graph as no configuration cache is available"
                        : "Reusing configuration cache"
                    File buildLog = new File(invocationSettings.projectDir, "profile.log")

                    def found = Files.lines(buildLog.toPath()).withCloseable { lines ->
                        lines.anyMatch { line -> line.contains(tag) }
                    }
                    if (!found) {
                        assertTrue("Configuration cache log '$tag' not found in '$buildLog'\n\n$buildLog.text", found)
                    }
                }
            }
        }
    }
}
