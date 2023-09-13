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
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings

import java.nio.file.Files
import java.util.regex.Pattern

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX
import static org.junit.Assert.assertTrue

class JavaConfigurationCachePerformanceTest extends AbstractCrossVersionPerformanceTest {
    private File stateDirectory

    def setup() {
        stateDirectory = temporaryFolder.file(".gradle/configuration-cache")
        runner.minimumBaseVersion = "6.6"
    }

    @RunFor([
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["smallJavaMultiProject"], iterationMatcher = ".*with hot.*"),
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProjectNoBuildSrc"], iterationMatcher = "assemble loading configuration cache state with cold daemon"),
        @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProjectNoBuildSrc"], iterationMatcher = "assemble storing configuration cache state with hot daemon"),
        @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProjectNoBuildSrc"], iterationMatcher = "assemble storing configuration cache state with cold daemon"),
        @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProjectNoBuildSrc"], iterationMatcher = "assemble loading configuration cache state with hot daemon")
    ])
    def "assemble #action configuration cache state with #daemon daemon"() {
        given:
        runner.tasksToRun = ["assemble"]
        // use the deprecated property so it works with previous versions
        runner.args = ["-D${ConfigurationCacheOption.DEPRECATED_PROPERTY_NAME}=true"]

        // Workaround to make that test work on Java 17
        // Unable to make field private final java.lang.Object[] java.lang.invoke.SerializedLambda.capturedArgs accessible
        // module java.base does not "opens java.lang.invoke" to unnamed module
        runner.gradleOpts += ["--add-opens=java.base/java.lang.invoke=ALL-UNNAMED"]

        and:
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
                        ? "Calculating task graph as no (cached configuration|configuration cache) is available"
                        : "Reusing configuration cache"
                    File buildLog = new File(invocationSettings.projectDir, "profile.log")

                    def found = Files.lines(buildLog.toPath()).withCloseable { lines ->
                        def pattern = Pattern.compile(tag)
                        lines.anyMatch { line -> pattern.matcher(line).find() }
                    }
                    if (!found) {
                        assertTrue("Configuration cache log '$tag' not found in '$buildLog'\n\n$buildLog.text", found)
                    }
                }
            }
        }
    }
}
