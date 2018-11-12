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

package org.gradle.configuration

import org.gradle.api.internal.ExecuteDomainObjectCollectionCallbackBuildOperationType
import org.gradle.api.internal.plugins.ApplyPluginBuildOperationType
import org.gradle.api.internal.tasks.RealizeTaskBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture

class ExecuteDomainObjectCollectionCallbackBuildOperationTypeIntegrationTest extends AbstractIntegrationSpec {

    def ops = new BuildOperationsFixture(executer, temporaryFolder)

    def 'task container callbacks are attributed to the correct registrant'() {
        given:
        file("scriptPlugin.gradle") << """
        tasks.all {
            doLast {
                println "action block from scriptPlugin.gradle"
            }
        }
        """
        buildFile << """
            class CallBackPlugin implements Plugin<Project> {
                void apply(Project p){
                    p.tasks.all {
                        doLast {
                            println "task do last block1"
                        }
                    }
                    
                    p.tasks.all {
                        doLast {
                            println "task do last block2"
                        }
                    }
                }
            }

            class TaskAddingPlugin implements Plugin<Project> {
                void apply(Project p){
                    p.tasks.create("hello")
                }
            }


            apply from: 'scriptPlugin.gradle'            
            apply plugin: CallBackPlugin
            apply plugin: TaskAddingPlugin
        """

        when:
        run('hello')

        then:
        def tasksCreated = ops.only(RealizeTaskBuildOperationType, { it.details.buildPath == ':' && it.details.taskPath == ':hello' })
        assert tasksCreated.children.size() == 3

        def callbackPluginApplicationId= ops.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'CallBackPlugin' }).details.applicationId
        tasksCreated.children.findAll { it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) && it.details.applicationId == callbackPluginApplicationId }.size == 2

        def scriptPluginApplicationId = ops.only(ApplyScriptPluginBuildOperationType, { it.details.file.endsWith('scriptPlugin.gradle') }).details.applicationId
        tasksCreated.children.findAll { it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) && it.details.applicationId == scriptPluginApplicationId }.size == 1
    }
}
