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

    String operationListenerStartAction = "{p, e -> }"
    String operationListenerFinishedAction = "{p, r -> }"

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
                    void started(BuildOperationDescriptor operation, OperationStartEvent startEvent) {
                        project.operStartAction(operation, startEvent)
                    }
        
                    @Override
                    void finished(BuildOperationDescriptor operation, OperationFinishEvent result) {
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

        buildFile << """
            apply plugin:BuildOperationLogPlugin
        """

        executer.beforeExecute {
            buildFile << """
            project.plugins.withType(BuildOperationLogPlugin){
                operStartAction = $operationListenerStartAction
                operFinishedAction = $operationListenerFinishedAction
            
            }
            """
        }
    }

    def "plugin can listen to build operations events"() {
        given:
        operationListenerStartAction = '{ op, event -> project.logger.lifecycle "START \$op.displayName"}'
        operationListenerFinishedAction = '{ op, result -> project.logger.lifecycle "FINISH \$op.displayName"}'

        when:
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

}
