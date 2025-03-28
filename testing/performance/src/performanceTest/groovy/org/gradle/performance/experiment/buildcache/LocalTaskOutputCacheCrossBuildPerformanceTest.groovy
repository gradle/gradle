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

package org.gradle.performance.experiment.buildcache

import org.gradle.performance.AbstractCrossBuildPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.performance.fixture.GradleBuildExperimentSpec
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.mutations.AbstractScheduledMutator
import org.gradle.profiler.mutations.ClearBuildCacheMutator

import static org.gradle.integtests.tooling.fixture.TextUtil.escapeString
import static org.gradle.performance.annotations.ScenarioType.PER_WEEK
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor([
    @Scenario(type = PER_WEEK, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProject", "largeMonolithicJavaProject"])
])
class LocalTaskOutputCacheCrossBuildPerformanceTest extends AbstractCrossBuildPerformanceTest {
    def "assemble with local cache (build comparison)"() {
        def noPushInitScript = temporaryFolder.file("no-push.gradle")
        noPushInitScript << """
            settingsEvaluated { settings ->
                settings.buildCache {
                    local {
                        push = false
                    }
                }
            }
        """.stripIndent()

        given:
        runner.addBuildMutator { invocationSettings ->
            new ClearBuildCacheMutator(invocationSettings.gradleUserHome, AbstractScheduledMutator.Schedule.SCENARIO)
        }
        runner.testGroup = "task output cache"
        runner.buildSpec {
            displayName("always-miss pull-only cache")
            invocation {
                cleanTasks("clean")
                args(
                    "--build-cache",
                    "--init-script", escapeString(noPushInitScript.absolutePath)
                )
            }
            warmUpCount = 2
            invocationCount = 4
        }
        runner.buildSpec {
            displayName("fully cached")
            invocation {
                cleanTasks("clean")
                args("--build-cache")
            }
        }
        runner.buildSpec {
            displayName("push-only")
            addBuildMutator { InvocationSettings invocationSettings ->
                new ClearBuildCacheMutator(invocationSettings.gradleUserHome, AbstractScheduledMutator.CleanupSchedule.BUILD)
            }
            invocation {
                cleanTasks("clean")
                args("--build-cache")
            }
            warmUpCount = 2
            invocationCount = 4
        }
        runner.baseline {
            displayName("fully up-to-date")
        }
        runner.baseline {
            displayName("non-cached")
            invocation {
                cleanTasks('clean')
            }
            warmUpCount = 2
            invocationCount = 4
        }

        when:
        def results = runner.run()

        then:
        results
    }

    @Override
    protected void defaultSpec(GradleBuildExperimentSpec.GradleBuilder builder) {
        super.defaultSpec(builder)
        builder.invocation {
            tasksToRun("assemble")
        }
    }
}
