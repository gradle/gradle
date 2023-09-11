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

package org.gradle.internal.logging.console.taskgrouping


import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

abstract class AbstractConsoleVerboseRenderingFunctionalTest extends AbstractConsoleVerboseBasicFunctionalTest {

    def 'up-to-date task result can be rendered'() {
        given:
        buildFile << '''
            task upToDate{
                outputs.upToDateWhen {true}
                doLast {}
            }
        '''

        when:
        succeeds('upToDate')

        then:
        result.groupedOutput.task(':upToDate').outcome == null

        when:
        succeeds('upToDate')

        then:
        result.groupedOutput.task(':upToDate').outcome == 'UP-TO-DATE'
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "task headers for long running tasks are printed only once when there is no output"() {
        given:
        settingsFile << """
            12.times { i ->
                include ":project\${i}"
            }
        """
        buildFile << """
            task allTasks

            12.times { i ->
                project(":project\${i}") {
                    task "slowTask\${i}" {
                        doLast {
                            sleep 2000 + (1000*(i%2))
                        }
                    }

                    rootProject.allTasks.dependsOn ":project\${i}:slowTask\${i}"
                }
            }
        """

        when:
        executer.withArguments("--parallel")
        succeeds "allTasks"

        then:
        12.times { i ->
            assert result.groupedOutput.task(":project${i}:slowTask${i}").output == ''
        }
    }
}
