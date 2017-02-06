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

package org.gradle.performance.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import spock.lang.Unroll

class DependencyResolutionPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("Resolves dependencies from #repoType repository - #testProject (daemon)")
    def "full build Java build"() {
        given:
        runner.testId = "resolves dependencies from $repoType repository $testProject (daemon)"
        runner.testProject = testProject
        runner.tasksToRun = ['resolveDependencies']
        runner.gradleOpts = ["-Xms256m", "-Xmx256m"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject              | repoType
        "lotDependencies"        | 'local'
        "lotProjectDependencies" | 'local'
    }

    def "exclude rule merging"() {
        given:
        runner.testId = "merges exclude rules (daemon)"
        runner.testProject = "excludeRuleMergingBuild"
        runner.tasksToRun = ['resolveDependencies']
        runner.gradleOpts = ["-Xms1g", "-Xmx1g"]
        // todo: revisit the nb of warmups/runs once 3.5 is out
        // because it is MUCH faster than 3.3/3/4 (more than 1min per iteration
        // for 3.3, vs ~7s for 3.5) so we cannot really afford a lot of iterations
        // with 3.3/3.4
        runner.warmUpRuns = 3
        runner.runs = 2

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
