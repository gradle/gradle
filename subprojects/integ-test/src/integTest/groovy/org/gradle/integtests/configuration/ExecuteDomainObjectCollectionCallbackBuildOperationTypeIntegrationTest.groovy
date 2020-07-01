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
import org.gradle.configuration.internal.ExecuteListenerBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.operations.trace.BuildOperationRecord
import spock.lang.Unroll

class ExecuteDomainObjectCollectionCallbackBuildOperationTypeIntegrationTest extends AbstractIntegrationSpec {

    def ops = new BuildOperationsFixture(executer, temporaryFolder)

    private static Closure fooTaskRealizationOpsQuery = { it.only(RealizeTaskBuildOperationType, { it.details.taskPath == ':foo' }) }
    private static Closure addingPluginBuildOpQuery = { it.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'AddingPlugin' }) }

    @Unroll
    def '#containerType container callbacks emit registrant when using #callbackName callback(before creation registered)'() {
        given:
        callbackScript(containerAccess, callbackName)
        buildFile << """
            ${requiresPlugins ? requiresPlugins.collect { "apply plugin: '${it}'" }.join('\n') : ''}
            ${callbackClass(containerAccess, callbackName)}
            ${addingPluginClass(containerItemCreation)}
            apply plugin: CallbackPlugin
            apply from: 'callbackScript.gradle'
            apply plugin: AddingPlugin
        """

        when:
        run('help')

        then:
        def creatingBuildOpParent = creatingBuildOpParentQuery(ops)
        assert creatingBuildOpParent.children.size() == requiresPlugins ? 2 + requiresPlugins.size() : 2 // additional plugin application results in another callback execution

        def callbackPluginApplicationId = ops.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'CallbackPlugin' }).details.applicationId
        creatingBuildOpParent.children.findAll {
            it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) && it.details.applicationId == callbackPluginApplicationId
        }.size == 1

        def scriptPluginApplicationId = ops.only(ApplyScriptPluginBuildOperationType, { it.details.file.endsWith('callbackScript.gradle') }).details.applicationId
        creatingBuildOpParent.children.findAll {
            it.hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details) && it.details.applicationId == scriptPluginApplicationId
        }.size == 1


        where:
        callbackName                         | containerType              | requiresPlugins  | containerAccess                                              | containerItemCreation               | creatingBuildOpParentQuery
        'all'                                | 'tasks'                    | []               | 'tasks'                                                      | "p.tasks.create('foo')"             | fooTaskRealizationOpsQuery
        'withType(Task)'                     | 'tasks'                    | []               | 'tasks'                                                      | "p.tasks.create('foo')"             | fooTaskRealizationOpsQuery
        'matching{true}.all'                 | 'tasks'                    | []               | 'tasks'                                                      | "p.tasks.create('foo')"             | fooTaskRealizationOpsQuery
        'all'                                | 'plugins'                  | []               | 'plugins'                                                    | ''                                  | addingPluginBuildOpQuery
        'withType(Plugin)'                   | 'plugins'                  | []               | 'plugins'                                                    | ''                                  | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'plugins'                  | []               | 'plugins'                                                    | ''                                  | addingPluginBuildOpQuery
        'all'                                | 'repositories'             | []               | 'repositories'                                               | "p.repositories.mavenCentral()"     | addingPluginBuildOpQuery
        'withType(ArtifactRepository)'       | 'repositories'             | []               | 'repositories'                                               | "p.repositories.mavenCentral()"     | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'repositories'             | []               | 'repositories'                                               | "p.repositories.mavenCentral()"     | addingPluginBuildOpQuery
        'all'                                | 'configurations'           | []               | 'configurations'                                             | createFooConfigurationSnippet()     | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'configurations'           | []               | 'configurations'                                             | createFooConfigurationSnippet()     | addingPluginBuildOpQuery
        'all'                                | 'dependencies'             | []               | "configurations.maybeCreate('foo').dependencies"             | createFooDependencySnippet()        | addingPluginBuildOpQuery
        'withType(ExternalModuleDependency)' | 'dependencies'             | []               | "configurations.maybeCreate('foo').dependencies"             | createFooDependencySnippet()        | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'dependencies'             | []               | "configurations.maybeCreate('foo').dependencies"             | createFooDependencySnippet()        | addingPluginBuildOpQuery
        'all'                                | 'allDependencies'          | []               | "configurations.maybeCreate('foo').allDependencies"          | createFooDependencySnippet()        | addingPluginBuildOpQuery
        'withType(ExternalModuleDependency)' | 'allDependencies'          | []               | "configurations.maybeCreate('foo').allDependencies"          | createFooDependencySnippet()        | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'allDependencies'          | []               | "configurations.maybeCreate('foo').allDependencies"          | createFooDependencySnippet()        | addingPluginBuildOpQuery
        'all'                                | 'dependencyConstraints'    | []               | "configurations.maybeCreate('foo').dependencyConstraints"    | createDependencyConstraintSnippet() | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'dependencyConstraints'    | []               | "configurations.maybeCreate('foo').dependencyConstraints"    | createDependencyConstraintSnippet() | addingPluginBuildOpQuery
        'all'                                | 'allDependencyConstraints' | []               | "configurations.maybeCreate('foo').allDependencyConstraints" | createDependencyConstraintSnippet() | addingPluginBuildOpQuery
        'matching{true}.all'                 | 'allDependencyConstraints' | []               | "configurations.maybeCreate('foo').allDependencyConstraints" | createDependencyConstraintSnippet() | addingPluginBuildOpQuery
        'all'                                | 'artifactTypes'            | []               | 'dependencies.artifactTypes'                                 | createArtifactTypeSnippet()         | addingPluginBuildOpQuery
        'all'                                | 'distributions'            | ['distribution'] | 'distributions'                                              | createFooDistributions()            | addingPluginBuildOpQuery
    }

    @Unroll
    def '#containerName container callbacks emit registrant with #callbackName callback(after creation registered)'() {
        given:
        callbackScript(containerAccess, callbackName)
        buildFile << """
            ${requiresPlugins ? requiresPlugins.collect { "apply plugin: '${it}'" }.join('\n') : ''}
            ${callbackClass(containerAccess, callbackName)}
            ${addingPluginClass(containerItemCreation)}
            apply plugin: AddingPlugin
            apply plugin: CallbackPlugin
            apply from: 'callbackScript.gradle'
        """

        when:
        run('help')

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
        callbackName         | containerName             | requiresPlugins                | containerAccess                          | containerItemCreation
        'all'                                | 'create tasks'             | []                             | 'tasks'                                       | "p.tasks.create('foo')"
        'withType(Task)'                     | 'create tasks'             | []                             | 'tasks'                                       | "p.tasks.create('foo')"
        'matching{true}.all'                 | 'create tasks'             | []                             | 'tasks'                                       | "p.tasks.create('foo')"
        'all'                                | 'plugins'                  | []                             | 'plugins'                                     | ''
        'withType(Plugin)'                   | 'plugins'                  | []                             | 'plugins'                                     | ''
        'matching{true}.all'                 | 'plugins'                  | []                             | 'plugins'                                     | ''
        'all'                                | 'repositories'             | []                             | 'repositories'                                | "p.repositories.mavenCentral()"
        'withType(ArtifactRepository)'       | 'repositories'             | []                             | 'repositories'                                | "p.repositories.mavenCentral()"
        'matching{true}.all'                 | 'repositories'             | []                             | 'repositories'                                | "p.repositories.mavenCentral()"
        'all'                                | 'configurations'           | []                             | 'configurations'                              | createFooConfigurationSnippet()
        'matching{true}.all'                 | 'configurations'           | []                             | 'configurations'                              | createFooConfigurationSnippet()
        'all'                                | 'dependencies'             | []                             | "configurations.foo.dependencies"             | createFooDependencySnippet()
        'withType(ExternalModuleDependency)' | 'dependencies'             | []                             | "configurations.foo.dependencies"             | createFooDependencySnippet()
        'matching{true}.all'                 | 'dependencies'             | []                             | "configurations.foo.dependencies"             | createFooDependencySnippet()
        'all'                                | 'allDependencies'          | []                             | "configurations.foo.allDependencies"          | createFooDependencySnippet()
        'withType(ExternalModuleDependency)' | 'allDependencies'          | []                             | "configurations.foo.allDependencies"          | createFooDependencySnippet()
        'matching{true}.all'                 | 'allDependencies'          | []                             | "configurations.foo.allDependencies"          | createFooDependencySnippet()
        'all'                                | 'dependencyConstraints'    | []                             | "configurations.foo.dependencyConstraints"    | createDependencyConstraintSnippet()
        'matching{true}.all'                 | 'dependencyConstraints'    | []                             | "configurations.foo.dependencyConstraints"    | createDependencyConstraintSnippet()
        'all'                                | 'allDependencyConstraints' | []                             | "configurations.foo.allDependencyConstraints" | createDependencyConstraintSnippet()
        'matching{true}.all'                 | 'allDependencyConstraints' | []                             | "configurations.foo.allDependencyConstraints" | createDependencyConstraintSnippet()
        'all'                                | 'artifactTypes'            | []                             | 'dependencies.artifactTypes'                  | createArtifactTypeSnippet()
        'all'                                | 'distributions'            | ['distribution']               | 'distributions'                               | createFooDistributions()
        "matching{it.name == 'foo'}.all"     | 'distributions'            | ['distribution']               | 'distributions'                               | createFooDistributions()
        "matching{true}.all" | 'test reports'            | ['java-library']               | 'test.reports'                           | ''
        "matching{true}.all" | 'checkstyle reports'      | ['java-library', 'checkstyle'] | 'checkstyleMain.reports'                 | ''
        "matching{true}.all" | 'pmd reports'             | ['java-library', 'pmd']        | 'pmdMain.reports'                        | ''
        "matching{true}.all" | 'codenarc reports'        | ['groovy', 'codenarc']         | 'codenarcMain.reports'                   | ''
        "matching{true}.all" | 'html dependency reports' | ['project-report']             | 'htmlDependencyReport.reports'           | ''
        "matching{true}.all" | 'build dashboard reports' | ['build-dashboard']            | 'buildDashboard.reports'                 | ''
        "matching{true}.all" | 'jacoco reports'          | ['java-library', 'jacoco']     | 'jacocoTestReport.reports'               | ''
    }

    def "task registration callback action executions emit build operation with script applicationId"() {
        given:
        file('registration.gradle') << """
            tasks.register('foo') {
                println 'task registration action'
            }
        """
        buildFile << """
            apply from: 'registration.gradle'
        """

        when:
        run('foo')

        then:
        def registerCallbackBuildOp = ops.only(ExecuteDomainObjectCollectionCallbackBuildOperationType)
        def registrationScriptApplication = ops.only(ApplyScriptPluginBuildOperationType, { it.details.file.endsWith('registration.gradle') })
        assert registrationScriptApplication.details.applicationId == registerCallbackBuildOp.details.applicationId
    }

    def "task registration callback action executions emit build operation with plugin applicationId"() {
        given:
        file('registration.gradle') << """

        """
        buildFile << """
            class RegisterPlugin implements Plugin<Project> {
                void apply(Project p) {
                    p.tasks.register('foo') {
                        println 'task registration action'
                    }
                }
            }
            apply plugin: RegisterPlugin
        """

        when:
        run('foo')

        then:
        def registerPluginApplication = ops.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'RegisterPlugin' })
        def registerCallbackBuildOp = ops.only(ExecuteDomainObjectCollectionCallbackBuildOperationType)
        assert registerPluginApplication.details.applicationId == registerCallbackBuildOp.details.applicationId
    }

    def "nested container callbacks emit build operation with application id"() {
        given:
        file('registration.gradle') << """

        """
        buildFile << """
            class CallbackPlugin implements Plugin<Project> {
                void apply(Project p) {
                    p.plugins.withType(RegisterPlugin) {
                        println 'plugin container callback'
                        p.tasks.all {
                            println 'task container callback'
                        }
                    }
                }
            }

            class RegisterPlugin implements Plugin<Project> {
               void apply(Project p) {
                    p.tasks.register('foo') {
                        println 'task registration callback'
                    }
                }
            }
            apply plugin: CallbackPlugin
            apply plugin: RegisterPlugin
        """

        when:
        run('help')

        then:
        def registerPluginApplication = ops.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'RegisterPlugin' })

        def taskRegistrationCallbackBuildOps = findCallbackActionBuildOps('task registration callback')
        taskRegistrationCallbackBuildOps.size == 1
        taskRegistrationCallbackBuildOps.every { it.details.applicationId == registerPluginApplication.details.applicationId }

        def callbackPluginApplication = ops.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'CallbackPlugin' })
        def pluginContainerCallbackBuildOps = findCallbackActionBuildOps('plugin container callback')
        pluginContainerCallbackBuildOps.size == 1
        pluginContainerCallbackBuildOps.every { it.details.applicationId == callbackPluginApplication.details.applicationId }

        def tasksContainerCallbackBuildOps = findCallbackActionBuildOps('task container callback')
        tasksContainerCallbackBuildOps.size > 0 // not necessary to track exact count here; adding removing build-in tasks should not break this
        tasksContainerCallbackBuildOps.every { it.details.applicationId == callbackPluginApplication.details.applicationId }
    }

    @Unroll
    def "filtered #container container callbacks emit build operation with application id for matching items only"() {
        given:
        file('script.gradle') << """
            ${container}.${filter}.all {
                println 'container callback'
            }
        """
        buildFile << """
            apply from:'script.gradle'
            class FooPlugin implements Plugin<Project> { void apply(Project p) {} }
            class BarPlugin implements Plugin<Project> { void apply(Project p) {} }

            ${domainObjectCreation('foo')}
            ${domainObjectCreation('bar')}
        """

        when:
        run('help')

        then:
        def scriptPluginApplication = ops.only(ApplyScriptPluginBuildOperationType, { it.details.file.endsWith('script.gradle') })
        def executeDomainObjectCallbackOps = ops.all(ExecuteDomainObjectCollectionCallbackBuildOperationType, { it.details.applicationId == scriptPluginApplication.details.applicationId })
        assert executeDomainObjectCallbackOps.size() == 1

        where:
        container        | filter                                                     | domainObjectCreation
        'tasks'          | "withType(Test).matching {it.name == 'foo'}"               | { name -> "tasks.register('$name', Test)" }
        'repositories'   | "withType(ArtifactRepository).matching {it.name == 'foo'}" | { name -> "repositories {maven { name = '$name' }}" }
        'configurations' | "matching {it.name == 'foo'}"                              | { name -> "configurations.create('$name')" }
        'plugins'        | "matching {it.class.simpleName == 'FooPlugin'}"            | { name -> "apply plugin:${name.capitalize()}Plugin" }
    }

    def "container callbacks registered from lifecycle listener emit build operation with application id"() {
        given:
        file('registration.gradle') << """

        """
        buildFile << """
            class CallbackPlugin implements Plugin<Project> {
                void apply(Project p) {
                    p.afterEvaluate {
                        println 'afterEvaluate callback'
                        p.repositories.all {
                            println 'repositories container callback'
                        }
                    }
                }
            }

            class RegisterPlugin implements Plugin<Project> {
               void apply(Project p) {
                    p.repositories.mavenCentral()
               }
            }
            apply plugin: CallbackPlugin
            apply plugin: RegisterPlugin
        """

        when:
        run('help')

        then:
        def callbackPluginApplication = ops.only(ApplyPluginBuildOperationType, { it.details.pluginClass == 'CallbackPlugin' })

        def afterEvaluateCallbackBuildOps = ops.only(ExecuteListenerBuildOperationType, { it.details.registrationPoint == 'Project.afterEvaluate' })
        afterEvaluateCallbackBuildOps.details.applicationId == callbackPluginApplication.details.applicationId
        afterEvaluateCallbackBuildOps.children.size() == 1
        afterEvaluateCallbackBuildOps.children[0].hasDetailsOfType(ExecuteDomainObjectCollectionCallbackBuildOperationType.Details.class)
        afterEvaluateCallbackBuildOps.children[0].details.applicationId == callbackPluginApplication.details.applicationId

        def repositoriesContainerCallbackBuildOps = findCallbackActionBuildOps('repositories container callback')
        repositoriesContainerCallbackBuildOps.size > 0 // not necessary to track exact count here; adding removing build-in tasks should not break this
        repositoriesContainerCallbackBuildOps.every { it.details.applicationId == callbackPluginApplication.details.applicationId }
    }

    def "applicationId for cross script buildscript configuration is emitted correctly"() {
        given:
        settingsFile << """
        gradle.allprojects {
            buildscript {
                repositories.all {
                    println "script repo callback"
                }
            }
            repositories.all {
                println "project repo callback"
            }
        }
        """
        buildFile << """
            buildscript {
                ${mavenCentralRepository()}
            }
            ${mavenCentralRepository()}
        """

        when:
        run('help')

        then:
        def settingsScriptApplicationId = ops.only(ApplyScriptPluginBuildOperationType, { it.details.file.endsWith('settings.gradle') })

        findCallbackActionBuildOp('project repo callback').details.applicationId == settingsScriptApplicationId.details.applicationId
        findCallbackActionBuildOp('script repo callback').details.applicationId == settingsScriptApplicationId.details.applicationId
    }

    def "applicationIds for container callbacks registered in beforeResolve and afterResolve callbacks are emitted correctly"() {
        given:
        file('callback.gradle') << """
            configurations {
                foo {
                    incoming.beforeResolve {
                        repositories.all {
                            println "before resolve repo container callback"
                        }
                    }
                    incoming.afterResolve {
                        repositories.all {
                            println "after resolve repo container callback"
                        }
                    }
                }
            }"""

        buildFile << """
            apply from: 'callback.gradle'

            repositories {
                mavenCentral()
            }

            configurations.foo.resolve()

        """

        when:
        run('help')

        then:
        def callbackScriptApplicationId = ops.only(ApplyScriptPluginBuildOperationType, { it.details.file.endsWith('callback.gradle') })

        findCallbackActionBuildOp('before resolve repo container callback').details.applicationId == callbackScriptApplicationId.details.applicationId
        findCallbackActionBuildOp('after resolve repo container callback').details.applicationId == callbackScriptApplicationId.details.applicationId
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

    def createFooDistributions() {
        """
           p.distributions.maybeCreate('foo')
        """
    }

    def createCheckstyleReport() {
        """
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

    void callbackScript(String containerAccess, String callbackName) {
        file("callbackScript.gradle") << """

        ${containerAccess}.${callbackName} {
            println "script callback from callbackScriptPlugin.gradle for \$it"
        }
        """
    }

    static String callbackClass(String containerAccess, String callbackName) {
        """class CallbackPlugin implements Plugin<Project> {
                void apply(Project p) {
                    p.${containerAccess}.$callbackName {
                        println "plugin callback \$it"
                    }
                }
            }"""
    }

    static String addingPluginClass(String creationLogic, String requiredPlugin = null) {
        """class AddingPlugin implements Plugin<Project> {
                void apply(Project p){
                    ${requiredPlugin != null ? "p.plugins.apply('${requiredPlugin}')" : ''}
                    $creationLogic
                }
            }"""
    }

    private List<BuildOperationRecord> findCallbackActionBuildOps(String markerOutput) {
        return ops.all(ExecuteDomainObjectCollectionCallbackBuildOperationType, { it.progress.size() == 1 && it.progress[0].details.spans[0].text.startsWith(markerOutput) })
    }

    private BuildOperationRecord findCallbackActionBuildOp(String markerOutput) {
        def ops = findCallbackActionBuildOps(markerOutput)
        assert ops.size() == 1
        ops[0]
    }

}
