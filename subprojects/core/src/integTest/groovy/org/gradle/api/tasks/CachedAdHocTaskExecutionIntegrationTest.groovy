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

package org.gradle.api.tasks

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture

class CachedAdHocTaskExecutionIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    @NotYetImplemented
    def "ad hoc tasks with different actions don't share results"() {
        file("input.txt").text = "data"
        buildFile << """
            def input = file("input.txt")

            task taskA {
                def output = file("build/task-a/output.txt")
                inputs.file input
                outputs.file output
                outputs.cacheIf { true }
                doFirst {
                    mkdir(output.parentFile)
                    output.text = input.text + " from task A"
                }
            }
 
            task taskB {
                def output = file("build/task-b/output.txt")
                inputs.file input
                outputs.file output
                outputs.cacheIf { true }
                doFirst {
                    mkdir(output.parentFile)
                    output.text = input.text + " from task B"
                }
            }
        """

        when:
        withBuildCache().succeeds "taskA", "taskB"
        then:
        nonSkippedTasks == [":taskA", ":taskB"]
        file("build/task-a/output.txt").text == "data from task A"
        file("build/task-b/output.txt").text == "data from task B"
    }
}
