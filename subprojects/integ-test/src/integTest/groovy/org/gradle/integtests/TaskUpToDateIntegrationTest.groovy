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


package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Unroll

class TaskUpToDateIntegrationTest extends AbstractIntegrationSpec {
    @Unroll
    @Issue("https://issues.gradle.org/browse/GRADLE-3540")
    def "order of #annotation doesn't mark task out-of-date"() {
        file("buildSrc/src/main/groovy/MyTask.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.file.*
            import org.gradle.api.tasks.*

            class MyTask extends DefaultTask {
                @Output${files ? "Files" : "Directories"} FileCollection out

                @TaskAction def exec() {
                    out.each { it${files ? ".text = 'data'" : ".mkdirs()"} }
                }
            }
        """

        buildFile.text = buildFileWithOutputs "out1", "out2"

        run "myTask"
        skippedTasks.empty

        when:
        run "myTask"
        then:
        skippedTasks.contains ":myTask"

        when:
        buildFile.text = buildFileWithOutputs "out2", "out1"
        run "myTask"
        then:
        skippedTasks.contains ":myTask"

        where:
        annotation           | files
        '@OutputFiles'       | true
        '@OutputDirectories' | false
    }

    def buildFileWithOutputs(String... outputs) {
        """
            task myTask(type: MyTask) {
                out = files("${outputs.join('", "')}")
            }
        """
    }
}
