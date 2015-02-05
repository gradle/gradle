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

import org.gradle.model.persist.ReusingModelRegistryStore
import org.gradle.performance.fixture.BuildSpecification
import spock.lang.Unroll

class VariantsPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll
    def "#size project using variants #scenario build"() {
        given:
        runner.testGroup = "project using variants"
        runner.testId = "$size project using variants $scenario build"
        runner.buildSpecifications = [
                BuildSpecification.forProject("${size}VariantsNewModel").displayName("new model").gradleOpts("-D$ReusingModelRegistryStore.TOGGLE=true").tasksToRun(*tasks).useDaemon().build(),
                BuildSpecification.forProject("${size}VariantsOldModel").displayName("old model").tasksToRun(*tasks).useDaemon().build()
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
        runner.testGroup = "project using variants"
        runner.testId = "$size project using variants partial build"
        runner.buildSpecifications = [
                BuildSpecification.forProject("${size}VariantsNewModel").displayName("new model").gradleOpts("-D$ReusingModelRegistryStore.TOGGLE=true").tasksToRun('flavour1type1').useDaemon().build(),
                BuildSpecification.forProject("${size}VariantsOldModel").displayName("old model").tasksToRun('flavour1type1').useDaemon().build()
        ]

        when:
        def result = runner.run()

        then:
        result.assertEveryBuildSucceeds()

        where:
        size << ["medium", "big"]
    }

    @Unroll
    def "multiproject using variants #scenario build"() {
        given:
        runner.testGroup = "project using variants"
        runner.testId = "multiproject using variants $scenario build"
        runner.buildSpecifications = [
                BuildSpecification.forProject("variantsNewModelMultiproject").displayName("new model").gradleOpts("-D$ReusingModelRegistryStore.TOGGLE=true").tasksToRun(*tasks).useDaemon().build(),
                BuildSpecification.forProject("variantsOldModelMultiproject").displayName("old model").tasksToRun(*tasks).useDaemon().build()
        ]

        when:
        def result = runner.run()

        then:
        result.assertEveryBuildSucceeds()

        where:
        scenario                      | tasks
        "single variant"              | [":project1:flavour1type1"]
        "all variants single project" | [":project1:allVariants"]
        "all variants all projects"   | ["allVariants"]
    }
}
