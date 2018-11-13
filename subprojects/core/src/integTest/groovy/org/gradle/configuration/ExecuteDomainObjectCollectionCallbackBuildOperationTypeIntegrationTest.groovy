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
import spock.lang.Unroll

class ExecuteDomainObjectCollectionCallbackBuildOperationTypeIntegrationTest extends AbstractIntegrationSpec {

    def ops = new BuildOperationsFixture(executer, temporaryFolder)

    @Unroll
    def 'task container callbacks emit registrant with filter #containerFilter (callback registered before creation)'() {
        given:
        file("callbackScript.gradle") << """
        tasks.${containerFilter} {
            doLast {
                println "action block from callbackScriptPlugin.gradle"
            }
        }
        """
        buildFile << """
            class CallbackPlugin implements Plugin<Project> {
                void apply(Project p){
                    p.tasks.$containerFilter {
                        doLast {
                            println "task do last block1"
                        }
                    }
                }
            }

            class AddingPlugin implements Plugin<Project> {
                void apply(Project p){
                    p.tasks.create("hello")
                }
            }


            apply plugin: CallbackPlugin
            apply from: 'callbackScript.gradle'
            apply plugin: AddingPlugin
        """

        when:
        run('hello')

        then:
        def tasksCreated = ops.only(RealizeTaskBuildOperationType, { it.details.buildPath == ':' && it.details.taskPath == ':hello' })
        assert tasksCreated.children.size() == 2

        def callbackPluginApplicationId = ops.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'CallbackPlugin' }).details.applicationId
        tasksCreated.children.findAll { it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) && it.details.applicationId == callbackPluginApplicationId }.size == 1

        def scriptPluginApplicationId = ops.only(ApplyScriptPluginBuildOperationType, { it.details.file.endsWith('callbackScript.gradle') }).details.applicationId
        tasksCreated.children.findAll { it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) && it.details.applicationId == scriptPluginApplicationId }.size == 1

        where:
        containerFilter << ['all', 'withType(Task)', 'matching{true}.all']
    }

    @Unroll
    def 'task container callbacks emit registrant with filter #containerFilter (callback registered after creation)'() {
        given:
        file("callbackScript.gradle") << """
        tasks.${containerFilter} {
            doLast {
                println "action block from callbackScriptPlugin.gradle"
            }
        }
        """
        buildFile << """
            class CallbackPlugin implements Plugin<Project> {
                void apply(Project p){
                    p.tasks.$containerFilter {
                        doLast {
                            println "task do last block1"
                        }
                    }
                }
            }

            class AddingPlugin implements Plugin<Project> {
                void apply(Project p){
                    p.tasks.create("hello")
                }
            }


            apply plugin: CallbackPlugin
            apply from: 'callbackScript.gradle'
            apply plugin: AddingPlugin
        """

        when:
        run('hello')

        then:
        def callbackPluginApplication = ops.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'CallbackPlugin' })
        def callbackBuildOps = callbackPluginApplication.children.findAll { it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) }
        assert callbackBuildOps.every { it.details.applicationId == callbackPluginApplication.details.applicationId }

        def callbackScriptApplication = ops.only(ApplyScriptPluginBuildOperationType, { it.details.file.endsWith('callbackScript.gradle') })
        def callbackScriptChildrend = callbackScriptApplication.children.findAll { it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) }
        assert callbackScriptChildrend.every { it.details.applicationId == callbackScriptApplication.details.applicationId }

        where:
        containerFilter << ['all', 'withType(Task)', 'matching{true}.all']
    }
    
}
