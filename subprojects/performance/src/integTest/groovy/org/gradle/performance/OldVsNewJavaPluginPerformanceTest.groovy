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
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(Experiment.class)
class OldVsNewJavaPluginPerformanceTest extends AbstractCrossBuildPerformanceTest {

    @Unroll
    def "#size project old vs new java plugin #scenario build"() {
        given:
        runner.testGroup = "old vs new java plugin"
        runner.testId = "$size project old vs new java plugin $scenario build"
        runner.buildSpec {
            forProject("${size}NewJava").displayName("new plugin").tasksToRun(*tasks).useDaemon()
        }
        runner.buildSpec {
            Toggles.transformedDsl(Toggles.modelReuse(it)).forProject("${size}NewJava").displayName("new plugin (reuse)").tasksToRun(*tasks).useDaemon()
        }
        runner.buildSpec {
            Toggles.transformedDsl(Toggles.noDaemonLogging(it)).forProject("${size}NewJava").displayName("new plugin (no client logging)").tasksToRun(*tasks).useDaemon()
        }
        runner.baseline {
            forProject("${size}OldJava").displayName("old plugin").tasksToRun(*tasks).useDaemon()
        }
        runner.baseline {
            Toggles.noDaemonLogging(it).forProject("${size}OldJava").displayName("old plugin (no client logging)").tasksToRun(*tasks).useDaemon()
        }

        when:
        def result = runner.run()

        then:
        result.assertEveryBuildSucceeds()

        where:
        scenario  | size     | tasks
        "empty"   | "small"  | ["help"]
        "empty"   | "medium" | ["help"]
        "empty"   | "big"    | ["help"]
        "full"    | "small"  | ["clean", "assemble"]
        "full"    | "medium" | ["clean", "assemble"]
        "full"    | "big"    | ["clean", "assemble"]
        "partial" | "medium" | [":project1:clean", ":project1:assemble"]
        "partial" | "big"    | [":project1:clean", ":project1:assemble"]
    }
}
