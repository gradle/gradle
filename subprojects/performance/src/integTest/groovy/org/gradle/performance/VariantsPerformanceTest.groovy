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
import spock.lang.Unroll

class VariantsPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll
    def "#size project using variants #scenario build"() {
        given:
        runner.testId = "$size project using variants $scenario build"
        runner.buildSpecifications = [
                BuildSpecification.forProject("${size}VariantsNewModel").displayName("new model").tasksToRun(*tasks).build(),
                BuildSpecification.forProject("${size}VariantsOldModel").displayName("old model").tasksToRun(*tasks).build()
        ]

        when:
        def result = runner.run()

        then:
        result.assertEveryBuildSucceeds()

        where:
        [size, tasks] << GroovyCollections.combinations(
                ["small", "medium", "big"],
                [["allVariants"], ["help"]]
        )
        scenario = tasks == ["help"] ? "empty" : "full"
    }

    @Unroll
    def "#size project using variants partial build"() {
        given:
        def tasks = (0..<builtVariants).collect { "flavour${(it % flavourAndTypeCount) + 1}type${it.intdiv(flavourAndTypeCount) + 1}" }

        runner.testId = "$size project using variants partial build"
        runner.buildSpecifications = [
                BuildSpecification.forProject("${size}VariantsNewModel").displayName("new model").tasksToRun(*tasks).build(),
                BuildSpecification.forProject("${size}VariantsOldModel").displayName("old model").tasksToRun(*tasks).build()
        ]

        when:
        def result = runner.run()

        then:
        result.assertEveryBuildSucceeds()

        where:
        size     | flavourAndTypeCount | builtVariants
        "medium" | 5                   | 5
        "big"    | 23                  | 100
    }
}
