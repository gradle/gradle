/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.performance.categories.BasicPerformanceTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(BasicPerformanceTest)
class GradleScriptKotlinBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll("Project '#testProject' with args #runnerArgs (first use)")
    def "build"() {
        given:
        runner.testId = "first use $testProject" + (recompileScripts ? ' (recompile-scripts)' : '')
        runner.testProject = testProject
        runner.tasksToRun = ['help']
        runner.targetVersions = ['last']
        runner.useDaemon = true
        if (recompileScripts) {
            runner.args += ['--recompile-scripts']
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject       | recompileScripts
        "ktsManyProjects" | true
        "ktsManyProjects" | false
    }
}
