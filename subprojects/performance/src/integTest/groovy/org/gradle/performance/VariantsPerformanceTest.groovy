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

import org.gradle.performance.fixture.Toggles
import spock.lang.Unroll

class VariantsPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll
    def "#size project using variants #scenario build"() {
        given:
        runner.testGroup = "project using variants"
        runner.testId = "$size project using variants $scenario build"
        runner.buildSpec {
            forProject("${size}VariantsNewModel").displayName("new model").tasksToRun(task).useDaemon()
        }
        runner.buildSpec {
            Toggles.modelReuse(it).forProject("${size}VariantsNewModel").displayName("new model (reuse)").tasksToRun(task).useDaemon()
        }
        runner.buildSpec {
            Toggles.noDaemonLogging(it).forProject("${size}VariantsNewModel").displayName("new model (no client logging)").tasksToRun(task).useDaemon()
        }
        runner.baseline {
            forProject("${size}VariantsOldModel").displayName("old model").tasksToRun(task).useDaemon()
        }
        runner.baseline {
            Toggles.noDaemonLogging(it).forProject("${size}VariantsOldModel").displayName("old model (no client logging)").tasksToRun(task).useDaemon()
        }

        when:
        def result = runner.run()

        then:
        result.assertEveryBuildSucceeds()

        where:
        scenario  | size     | task
        "empty"   | "small"  | "help"
        "empty"   | "medium" | "help"
        "empty"   | "big"    | "help"
        "full"    | "small"  | "allVariants"
        "full"    | "medium" | "allVariants"
        "full"    | "big"    | "allVariants"
        "partial" | "medium" | "flavour1type1"
        "partial" | "big"    | "flavour1type1"
    }

    @Unroll
    def "multiproject using variants #scenario build"() {
        given:
        runner.testGroup = "project using variants"
        runner.testId = "multiproject using variants $scenario build"
        runner.buildSpec {
            forProject("variantsNewModelMultiproject").displayName("new model").tasksToRun(*tasks).useDaemon()
        }
        runner.buildSpec {
            Toggles.modelReuse(it).forProject("variantsNewModelMultiproject").displayName("new model (reuse)").tasksToRun(*tasks).useDaemon()
        }
        runner.buildSpec {
            Toggles.noDaemonLogging(it).forProject("variantsNewModelMultiproject").displayName("new model (no client logging)").tasksToRun(*tasks).useDaemon()
        }
        runner.baseline {
            forProject("variantsOldModelMultiproject").displayName("old model").tasksToRun(*tasks).useDaemon()
        }
        runner.baseline {
            Toggles.noDaemonLogging(it).forProject("variantsOldModelMultiproject").displayName("old model (no client logging)").tasksToRun(*tasks).useDaemon()
        }

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
