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

package org.gradle.internal.progress

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildOperationServiceIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("buildSrc/src/main/groovy/BuildOperationLogPlugin.groovy") << """
        import org.gradle.internal.progress.* 
        import org.gradle.api.Project
        import org.gradle.api.Plugin
        
        class BuildOperationLogPlugin implements Plugin<Project> {
            void apply(Project project){
                project.ext.operStartAction = {p, e -> }
                project.ext.operFinishedAction = {p, r -> }
                def listener = new BuildOperationListener() {
                    @Override
                    void started(BuildOperationInternal operation, OperationStartEvent startEvent) {
                        project.operStartAction(operation, startEvent)
                    }
        
                    @Override
                    void finished(BuildOperationInternal operation, OperationResult result) {
                        project.operFinishedAction(operation, result)
                    }
                }
                project.gradle.services.get(BuildOperationService).addListener(listener)
                project.gradle.buildFinished {
                    project.gradle.services.get(BuildOperationService).removeListener(listener)
                }
            }
        }     
        """
        buildFile << "apply plugin:BuildOperationLogPlugin"
    }

    def "plugin can listen to build operations events"() {
        when:
        buildFile << """
            operStartAction = { op, event -> project.logger.lifecycle "START \$op.displayName"}
            operFinishedAction = { op, result -> project.logger.lifecycle "FINISH \$op.displayName"}
        """
        succeeds 'help'

        then:
        result.output.contains 'START Task :help'
        result.output.contains 'FINISH Task :help'

        when:
        buildFile.text = ""
        succeeds("help")

        then:
        !result.output.contains('START Task :help')
        !result.output.contains('FINISH Task :help')
    }

    def "requested tasks are exposed"() {
        given:
        settingsFile << """
        include "a"
        include "b"
        include "a:c"
"""
        buildFile << """
            allprojects {
                task someTask
            }

            operFinishedAction = { op, result -> 
                if(op.operationDescriptor != null && org.gradle.execution.taskgraph.CalculateTaskGraphDescriptor.class.isAssignableFrom(op.operationDescriptor.getClass())){
                    result.result.requestedTaskPaths.each { tskPath ->
                        println "requested task: \$tskPath"
                    }
                }
            }
        """
        when:
        succeeds('help')

        then:
        result.output.contains 'requested task: :help'

        when:
        succeeds('someTask')

        then:
        result.output.contains 'requested task: :someTask'
        result.output.contains 'requested task: :a:someTask'
        result.output.contains 'requested task: :b:someTask'
        result.output.contains 'requested task: :a:c:someTask'

        when:
        succeeds('someTask', '-x', ':b:someTask')

        then:
        result.output.contains 'requested task: :someTask'
        result.output.contains 'requested task: :a:someTask'
        result.output.contains 'requested task: :a:c:someTask'
        !result.output.contains('requested task: :b:someTask')


        when:
        succeeds(':a:someTask')

        then:
        result.output.contains('requested task: :a:someTask')
        !result.output.contains('requested task: :someTask')
        !result.output.contains('requested task: :a:c:someTask')
        !result.output.contains('requested task: :b:someTask')

    }
}
