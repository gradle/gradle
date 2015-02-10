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

import org.gradle.performance.fixture.BuildSpecification
import org.gradle.performance.fixture.Toggles
import spock.lang.Unroll

class OldVsNewJavaPluginPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll
    def "#size project old vs new java plugin #scenario build"() {
        given:
        runner.testGroup = "old vs new java plugin"
        runner.testId = "$size project old vs new java plugin $scenario build"
        runner.buildSpecifications = [
                BuildSpecification.forProject("${size}OldJava").displayName("old plugin").tasksToRun(*tasks).useDaemon().build(),
                Toggles.modelReuse(BuildSpecification.forProject("${size}NewJava")).displayName("new plugin").tasksToRun(*tasks).useDaemon().build()
        ]

        when:
        def result = runner.run()

        then:
        result.assertEveryBuildSucceeds()

        where:
        [size, tasks] << GroovyCollections.combinations(
                ["small", "medium", "big"],
                [["clean", "assemble"], ["help"]]
        )
        scenario = tasks == ["help"] ? "empty" : "full"
    }

    @Unroll
    def "#size project old vs new java plugin partial build"() {
        given:
        runner.testGroup = "old vs new java plugin"
        runner.testId = "$size project old vs new java plugin partial build"
        runner.buildSpecifications = [
                BuildSpecification.forProject("${size}OldJava").displayName("old plugin").tasksToRun(":project1:clean", ":project1:assemble").useDaemon().build(),
                Toggles.modelReuse(BuildSpecification.forProject("${size}NewJava").displayName("new plugin").tasksToRun(":project1:clean", ":project1:assemble").useDaemon()).build()
        ]

        when:
        def result = runner.run()

        then:
        result.assertEveryBuildSucceeds()

        where:
        size << ["medium", "big"]
    }
}
