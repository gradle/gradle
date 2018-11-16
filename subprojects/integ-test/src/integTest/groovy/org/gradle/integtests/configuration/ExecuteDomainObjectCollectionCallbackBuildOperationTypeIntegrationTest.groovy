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

package org.gradle.integtests.configuration

import org.gradle.api.internal.ExecuteDomainObjectCollectionCallbackBuildOperationType
import org.gradle.api.internal.plugins.ApplyPluginBuildOperationType
import org.gradle.api.internal.tasks.RealizeTaskBuildOperationType
import org.gradle.configuration.ApplyScriptPluginBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import spock.lang.Unroll

class ExecuteDomainObjectCollectionCallbackBuildOperationTypeIntegrationTest extends AbstractIntegrationSpec {

    def ops = new BuildOperationsFixture(executer, temporaryFolder)

    private static Closure fooTaskRealizationOpsQuery = { it.only(RealizeTaskBuildOperationType, { it.details.taskPath == ':foo' }) }
    private static Closure addingPluginBuildOpQuery = { it.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'AddingPlugin' }) }

    @Unroll
    def '#containerType container callbacks emit registrant with filter #containerFilter (callback registered before creation)'() {
        given:
        callbackScript(containerAccess, containerFilter)
        buildFile << """
            ${callbackClass(containerAccess, containerFilter)}
            ${addingPluginClass(containerItemCreation)}
            apply plugin: CallbackPlugin
            apply from: 'callbackScript.gradle'
            apply plugin: AddingPlugin
        """

        when:
        run('tasks')

        then:
        def creatingBuildOpParent = creatingBuildOpParentQuery(ops)
        assert creatingBuildOpParent.children.size() == 2

        def callbackPluginApplicationId = ops.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'CallbackPlugin' }).details.applicationId
        creatingBuildOpParent.children.findAll {
            it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) && it.details.applicationId == callbackPluginApplicationId
        }.size == 1

        def scriptPluginApplicationId = ops.only(ApplyScriptPluginBuildOperationType, { it.details.file.endsWith('callbackScript.gradle') }).details.applicationId
        creatingBuildOpParent.children.findAll {
            it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) && it.details.applicationId == scriptPluginApplicationId
        }.size == 1


        where:
        containerFilter                      | containerType              | containerAccess                                              | containerItemCreation               | creatingBuildOpParentQuery
        'all'                                | 'tasks'                    | 'tasks'                                                      | "p.tasks.create('foo')"             | fooTaskRealizationOpsQuery
        'withType(Task)'                     | 'tasks'                    | 'tasks'                                                      | "p.tasks.create('foo')"             | fooTaskRealizationOpsQuery
        'matching{true}.all'                 | 'tasks'                    | 'tasks'                                                      | "p.tasks.create('foo')"             | fooTaskRealizationOpsQuery
        'all'                                | 'plugins'                  | 'plugins'                                                    | ''                                  | addingPluginBuildOpQuery
        'withType(Plugin)'                   | 'plugins'                  | 'plugins'                                                    | ''                                  | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'plugins'                  | 'plugins'                                                    | ''                                  | addingPluginBuildOpQuery
        'all'                                | 'repositories'             | 'repositories'                                               | "p.repositories.mavenCentral()"     | addingPluginBuildOpQuery
        'withType(ArtifactRepository)'       | 'repositories'             | 'repositories'                                               | "p.repositories.mavenCentral()"     | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'repositories'             | 'repositories'                                               | "p.repositories.mavenCentral()"     | addingPluginBuildOpQuery
        'all'                                | 'configurations'           | 'configurations'                                             | createFooConfigurationSnippet()     | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'configurations'           | 'configurations'                                             | createFooConfigurationSnippet()     | addingPluginBuildOpQuery
        'all'                                | 'dependencies'             | "configurations.maybeCreate('foo').dependencies"             | createFooDependencySnippet()        | addingPluginBuildOpQuery
        'withType(ExternalModuleDependency)' | 'dependencies'             | "configurations.maybeCreate('foo').dependencies"             | createFooDependencySnippet()        | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'dependencies'             | "configurations.maybeCreate('foo').dependencies"             | createFooDependencySnippet()        | addingPluginBuildOpQuery
        'all'                                | 'allDependencies'          | "configurations.maybeCreate('foo').allDependencies"          | createFooDependencySnippet()        | addingPluginBuildOpQuery
        'withType(ExternalModuleDependency)' | 'allDependencies'          | "configurations.maybeCreate('foo').allDependencies"          | createFooDependencySnippet()        | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'allDependencies'          | "configurations.maybeCreate('foo').allDependencies"          | createFooDependencySnippet()        | addingPluginBuildOpQuery
        'all'                                | 'dependencyConstraints'    | "configurations.maybeCreate('foo').dependencyConstraints"    | createDependencyConstraintSnippet() | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'dependencyConstraints'    | "configurations.maybeCreate('foo').dependencyConstraints"    | createDependencyConstraintSnippet() | addingPluginBuildOpQuery
        'all'                                | 'allDependencyConstraints' | "configurations.maybeCreate('foo').allDependencyConstraints" | createDependencyConstraintSnippet() | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'allDependencyConstraints' | "configurations.maybeCreate('foo').allDependencyConstraints" | createDependencyConstraintSnippet() | addingPluginBuildOpQuery
        'all'                                | 'artifactTypes'            | 'dependencies.artifactTypes'                                 | createArtifactTypeSnippet()         | addingPluginBuildOpQuery
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
        containerFilter                      | containerName              | containerAccess                                              | containerItemCreation
        'all'                                | 'create tasks'             | 'tasks'                                                      | "p.tasks.create('foo')"
        'withType(Task)'                     | 'create tasks'             | 'tasks'                                                      | "p.tasks.create('foo')"
        'matching{true}.all'                 | 'create tasks'             | 'tasks'                                                      | "p.tasks.create('foo')"
        'all'                                | 'plugins'                  | 'plugins'                                                    | ''
        'withType(Plugin)'                   | 'plugins'                  | 'plugins'                                                    | ''
        'matching{true}.all'                 | 'plugins'                  | 'plugins'                                                    | ''
        'all'                                | 'repositories'             | 'repositories'                                               | "p.repositories.mavenCentral()"
        'withType(ArtifactRepository)'       | 'repositories'             | 'repositories'                                               | "p.repositories.mavenCentral()"
        'matching{true}.all'                 | 'repositories'             | 'repositories'                                               | "p.repositories.mavenCentral()"
        'all'                                | 'configurations'           | 'configurations'                                             | createFooConfigurationSnippet()
        'matching{true}.all'                 | 'configurations'           | 'configurations'                                             | createFooConfigurationSnippet()
        'all'                                | 'dependencies'             | "configurations.foo.dependencies"             | createFooDependencySnippet()
        'withType(ExternalModuleDependency)' | 'dependencies'             | "configurations.foo.dependencies"             | createFooDependencySnippet()
        'matching{true}.all'                 | 'dependencies'             | "configurations.foo.dependencies"             | createFooDependencySnippet()
        'all'                                | 'allDependencies'          | "configurations.foo.allDependencies"          | createFooDependencySnippet()
        'withType(ExternalModuleDependency)' | 'allDependencies'          | "configurations.foo.allDependencies"          | createFooDependencySnippet()
        'matching{true}.all'                 | 'allDependencies'          | "configurations.foo.allDependencies"          | createFooDependencySnippet()
        'all'                                | 'dependencyConstraints'    | "configurations.foo.dependencyConstraints"    | createDependencyConstraintSnippet()
        'matching{true}.all'                 | 'dependencyConstraints'    | "configurations.foo.dependencyConstraints"    | createDependencyConstraintSnippet()
        'all'                                | 'allDependencyConstraints' | "configurations.foo.allDependencyConstraints" | createDependencyConstraintSnippet()
        'matching{true}.all'                 | 'allDependencyConstraints' | "configurations.foo.allDependencyConstraints" | createDependencyConstraintSnippet()
        'all'                                | 'artifactTypes'            | 'dependencies.artifactTypes'                                 | createArtifactTypeSnippet()
    }

    def createArtifactTypeSnippet() {
        """
        p.dependencies {
            artifactTypes {
                jar {
                    attributes.attribute(Attribute.of('usage', String), 'java-runtime')
                    attributes.attribute(Attribute.of('javaVersion', String), '1.8')
                }
            }
        }
        """
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
        "p.configurations.maybeCreate('foo')"
    }

    private String createFooDependencySnippet() {
        """
        ${createFooConfigurationSnippet()}
        p.dependencies.add('foo', 'org.acme:foo:1.0')
        """

    }

    void callbackScript(String containerAccess, String containerFilter) {
        file("callbackScript.gradle") << """
        
        ${containerAccess}.${containerFilter} {
            println "script callback from callbackScriptPlugin.gradle for \$it"
        }
        """
    }

    static String callbackClass(String containerAccess, String containerFilter) {
        """class CallbackPlugin implements Plugin<Project> {
                void apply(Project p){
                    p.${containerAccess}.$containerFilter {
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
