/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.initialization.ParallelismBuildOptions
import spock.lang.Unroll

class TaskOutputCachingSwiftPerformanceTest extends AbstractTaskOutputCachingPerformanceTest {

    def setup() {
        runner.minimumBaseVersion = "4.5"
        runner.targetVersions = ["6.2-20200108160029+0000"]
        runner.args += ["--parallel", "--${ParallelismBuildOptions.MaxWorkersOption.LONG_OPTION}=6"]
    }

    @Unroll
    def "clean #task on #testProject with local cache"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = [task]
        runner.gradleOpts = ["-Xms$maxMemory", "-Xmx$maxMemory"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject        | task       | maxMemory
        'mediumSwiftMulti' | 'assemble' | '1G'
        'bigSwiftApp'      | 'assemble' | '1G'
    }
}
