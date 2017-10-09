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

import org.gradle.initialization.ParallelismBuildOptionFactory
import org.gradle.performance.fixture.BuildExperimentInvocationInfo
import org.gradle.performance.fixture.BuildExperimentListenerAdapter
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

class TaskOutputCachingNativePerformanceTest extends AbstractTaskOutputCachingPerformanceTest {

    def setup() {
        runner.minimumVersion = "4.2"
        runner.targetVersions = ["4.3-20171004093631+0000"]
        runner.warmUpRuns = 5
        runner.runs = 5
        storesInCache = false
        runner.addBuildExperimentListener(new BuildExperimentListenerAdapter() {
            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                if (isFirstRunWithCache(invocationInfo)) {
                    // TODO: Fix version constraint as soon as we have a nightly including the depend plugin
                    new TestFile(invocationInfo.projectDir).file('build.gradle') << """      
                    allprojects {
                        if (org.gradle.util.GradleVersion.current() != org.gradle.util.GradleVersion.version('4.3-20171004093631+0000')) {
                            apply plugin: Class.forName('org.gradle.language.nativeplatform.plugins.DependPlugin')
                        }
                    }
                    """
                }
            }
        })
    }

    @Unroll
    def "clean assemble on #testProject (local cache)"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = [task]
        runner.gradleOpts = ["-Xms$maxMemory", "-Xmx$maxMemory"]
        runner.args += ["--parallel", "--${ParallelismBuildOptionFactory.MaxWorkersOption.LONG_OPTION}=6"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject        | task                         | maxMemory
        'nativeDependents' | 'libA0:buildDependentsLibA0' | '3g'
        'bigNative'        | 'assemble'                   | '1G'
    }
}
