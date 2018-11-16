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
    def '#containerType container callbacks emit registrant with filter #containerFilter (callback registered before creation)'() {
        given:
        callbackScript(containerType, containerFilter)
        buildFile << """
            ${callbackClass(containerType, containerFilter)}
            ${addingPluginClass(creationLogic)}

            apply plugin: CallbackPlugin
            apply from: 'callbackScript.gradle'
            apply plugin: AddingPlugin
        """


        when:
        run('tasks')


        then:
        def addingPluginApplicationId = operationQuery(ops)
        assert addingPluginApplicationId.children.size() == 2

        def callbackPluginApplicationId = ops.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'CallbackPlugin' }).details.applicationId
        addingPluginApplicationId.children.findAll {
            it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) && it.details.applicationId == callbackPluginApplicationId
        }.size == 1

        def scriptPluginApplicationId = ops.only(ApplyScriptPluginBuildOperationType, { it.details.file.endsWith('callbackScript.gradle') }).details.applicationId
        addingPluginApplicationId.children.findAll {
            it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) && it.details.applicationId == scriptPluginApplicationId
        }.size == 1


        where:
        containerFilter                | containerType  | creationLogic                   | operationQuery
        'all'                          | 'tasks'        | "p.tasks.create('hello')"       | { it.only(RealizeTaskBuildOperationType, { it.details.taskPath == ':hello' }) }
        'withType(Task)'               | 'tasks'        | "p.tasks.create('hello')"       | { it.only(RealizeTaskBuildOperationType, { it.details.taskPath == ':hello' }) }
        'matching{true}.all'           | 'tasks'        | "p.tasks.create('hello')"       | { it.only(RealizeTaskBuildOperationType, { it.details.taskPath == ':hello' }) }
        'all'                          | 'plugins'      | ''                              | { it.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'AddingPlugin' }) }
        'withType(Plugin)'             | 'plugins'      | ''                              | { it.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'AddingPlugin' }) }
        'matching{true}.all'           | 'plugins'      | ''                              | { it.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'AddingPlugin' }) }
        'all'                          | 'repositories' | "p.repositories.mavenCentral()" | { it.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'AddingPlugin' }) }
        'withType(ArtifactRepository)' | 'repositories' | "p.repositories.mavenCentral()" | { it.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'AddingPlugin' }) }
        'matching{true}.all'           | 'repositories' | "p.repositories.mavenCentral()" | { it.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'AddingPlugin' }) }

    }

    @Unroll
    def '#containerName container callbacks emit registrant with filter #containerFilter (callback registered after creation)'() {
        given:
        callbackScript(containerAccess, containerFilter)
        buildFile << """
            ${callbackClass(containerAccess, containerFilter)}
            ${addingPluginClass(containerItemCreation)}

            apply plugin: AddingPlugin

            apply plugin: CallbackPlugin
            apply from: 'callbackScript.gradle'
        """


        when:
        run('tasks')


        then:
        def callbackPluginApplication = ops.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'CallbackPlugin' })
        def callbackBuildOps = callbackPluginApplication.children.findAll { it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) }
        !callbackBuildOps.isEmpty()
        assert callbackBuildOps.every { it.details.applicationId == callbackPluginApplication.details.applicationId }

        def callbackScriptApplication = ops.only(ApplyScriptPluginBuildOperationType, { it.details.file.endsWith('callbackScript.gradle') })
        def callbackScriptChildren = callbackScriptApplication.children.findAll { it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) }
        !callbackScriptChildren.isEmpty()
        assert callbackScriptChildren.every { it.details.applicationId == callbackScriptApplication.details.applicationId }


        where:
        containerFilter                      | containerName              | containerAccess                               | containerItemCreation
        'all'                                | 'tasks'                    | 'tasks'                                       | "p.tasks.create('hello')"
        'withType(Task)'                     | 'tasks'                    | 'tasks'                                       | "p.tasks.create('hello')"
        'matching{true}.all'                 | 'tasks'                    | 'tasks'                                       | "p.tasks.create('hello')"
        'all'                                | 'plugins'                  | 'plugins'                                     | ''
        'withType(Plugin)'                   | 'plugins'                  | 'plugins'                                     | ''
        'matching{true}.all'                 | 'plugins'                  | 'plugins'                                     | ''
        'all'                                | 'repositories'             | 'repositories'                                | "p.repositories.mavenCentral()"
        'withType(ArtifactRepository)'       | 'repositories'             | 'repositories'                                | "p.repositories.mavenCentral()"
        'matching{true}.all'                 | 'repositories'             | 'repositories'                                | "p.repositories.mavenCentral()"
        'all'                                | 'configurations'           | 'configurations'                              | createFooConfigurationSnippet()
        'matching{true}.all'                 | 'configurations'           | 'configurations'                              | createFooConfigurationSnippet()
        'all'                                | 'dependencies'             | 'configurations.foo.dependencies'             | createFooDependencySnippet()
        'withType(ExternalModuleDependency)' | 'dependencies'             | 'configurations.foo.dependencies'             | createFooDependencySnippet()
        'matching{true}.all'                 | 'dependencies'             | 'configurations.foo.dependencies'             | createFooDependencySnippet()
        'all'                                | 'allDependencies'          | 'configurations.foo.allDependencies'          | createFooDependencySnippet()
        'withType(ExternalModuleDependency)' | 'allDependencies'          | 'configurations.foo.allDependencies'          | createFooDependencySnippet()
        'matching{true}.all'                 | 'allDependencies'          | 'configurations.foo.allDependencies'          | createFooDependencySnippet()
        'all'                                | 'dependencyConstraints'    | 'configurations.foo.dependencyConstraints'    | createDependencyConstraintSnippet()
        'matching{true}.all'                 | 'dependencyConstraints'    | 'configurations.foo.dependencyConstraints'    | createDependencyConstraintSnippet()
        'all'                                | 'allDependencyConstraints' | 'configurations.foo.allDependencyConstraints' | createDependencyConstraintSnippet()
        'matching{true}.all'                 | 'allDependencyConstraints' | 'configurations.foo.allDependencyConstraints' | createDependencyConstraintSnippet()
    }

    private String createDependencyConstraintSnippet() {
        """
            ${createFooDependencySnippet()}
            p.dependencies {
                constraints { foo('org.acme:foo:1.0') { because "testing" }}
            }
        """
    }

    private String createFooConfigurationSnippet() {
        "p.configurations.create('foo')"
    }

    private String createFooDependencySnippet() {
        """
        ${createFooConfigurationSnippet()}
        p.dependencies.add('foo', 'org.acme:foo:1.0')
        """

    }

    void callbackScript(String containerType, String containerFilter) {
        file("callbackScript.gradle") << """
        
        ${containerType}.${containerFilter} {
            println "script callback from callbackScriptPlugin.gradle for \$it"
        }
        """
    }

    static String callbackClass(String containerType, String containerFilter) {
        """class CallbackPlugin implements Plugin<Project> {
                void apply(Project p){
                    p.${containerType}.$containerFilter {
                        println "plugin callback \$it"
                    }
                }
            }"""
    }

    static String addingPluginClass(String creationLogic) {
        """class AddingPlugin implements Plugin<Project> {
                void apply(Project p){
                    $creationLogic
                }
            }"""
    }

}
