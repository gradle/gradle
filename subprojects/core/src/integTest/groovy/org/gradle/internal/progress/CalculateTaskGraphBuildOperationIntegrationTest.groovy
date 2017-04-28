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

package org.gradle.internal.progress

class CalculateTaskGraphBuildOperationIntegrationTest extends AbstractBuildOperationServiceIntegrationTest {

    def "requested and filtered tasks are exposed"() {
        given:
        operationListenerFinishedAction = """ { op, result -> 
                if(op.details != null && org.gradle.execution.taskgraph.CalculateTaskGraphDetails.class.isAssignableFrom(op.details.getClass())){
                    result.result.requestedTaskPaths.each { tskPath ->
                        println "requested task: \$tskPath"
                    }
                    result.result.filteredTaskPaths.each { tskPath ->
                        println "filtered task: \$tskPath"
                    }
                }
            }
        """
        settingsFile << """
        include "a"
        include "b"
        include "a:c"
        """

        buildFile << """
            allprojects {
                task otherTask
                task someTask
                someTask.dependsOn otherTask
            }
        """
        when:
        succeeds('help')

        then:
        result.output.contains 'requested task: :help'
        !result.output.contains('excluded task:')

        when:
        succeeds('someTask')

        then:
        result.output.contains 'requested task: :someTask'
        result.output.contains 'requested task: :a:someTask'
        result.output.contains 'requested task: :b:someTask'
        result.output.contains 'requested task: :a:c:someTask'
        !result.output.contains('filtered task:')

        when:
        succeeds('someTask', '-x', ':b:someTask')

        then:
        result.output.contains 'requested task: :someTask'
        result.output.contains 'requested task: :a:someTask'
        result.output.contains 'requested task: :a:c:someTask'
        result.output.contains('requested task: :b:someTask')
        result.output.contains('filtered task: :b:someTask')

        when:
        succeeds('someTask', '-x', 'otherTask')

        then:
        result.output.contains 'requested task: :someTask'
        result.output.contains 'requested task: :a:someTask'
        result.output.contains 'requested task: :a:c:someTask'
        result.output.contains('requested task: :b:someTask')
        result.output.contains('filtered task: :otherTask')
        result.output.contains('filtered task: :a:otherTask')
        result.output.contains('filtered task: :b:otherTask')
        result.output.contains('filtered task: :a:c:otherTask')

        when:
        succeeds(':a:someTask')

        then:
        result.output.contains('requested task: :a:someTask')
        !result.output.contains('requested task: :someTask')
        !result.output.contains('requested task: :a:c:someTask')
        !result.output.contains('requested task: :b:someTask')
        !result.output.contains('filtered task:')

    }

    def "errors in calculating task graph are exposed"() {
        given:
        operationListenerFinishedAction = """ { op, result -> 
                if(op.details != null && org.gradle.execution.taskgraph.CalculateTaskGraphDetails.class.isAssignableFrom(op.details.getClass())){
                    println "Calculate task graph failure: " + result.failure.getMessage()
                }
            }
        """

        when:
        fails('someNonExisting')

        then:
        result.output.contains 'Calculate task graph failure: Task \'someNonExisting\' not found in root project'
    }

}
