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

package org.gradle.invocation

import org.gradle.StartParameter
import org.gradle.api.Action
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.BuildOperationProjectConfigurator
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.DefaultProjectRegistry
import org.gradle.api.internal.project.ProjectConfigurator
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.execution.TaskGraphExecuter
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.installation.GradleInstallation
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.progress.TestBuildOperationExecutor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.util.GradleVersion
import org.gradle.util.Path
import spock.lang.Specification

class DefaultGradleSpec extends Specification {

    AsmBackedClassGenerator classGenerator = new AsmBackedClassGenerator()
    ServiceRegistryFactory serviceRegistryFactory = Stub(ServiceRegistryFactory)
    ListenerManager listenerManager = Spy(DefaultListenerManager)

    StartParameter parameter = new StartParameter()
    CurrentGradleInstallation currentGradleInstallation = Mock(CurrentGradleInstallation)
    BuildOperationExecutor buildOperationExecutor = new TestBuildOperationExecutor()
    ProjectConfigurator projectConfigurator = new BuildOperationProjectConfigurator(buildOperationExecutor)

    GradleInternal gradle

    def setup() {
        def serviceRegistry = Stub(ServiceRegistry)
        _ * serviceRegistryFactory.createFor(_) >> serviceRegistry
        _ * serviceRegistry.get(ClassLoaderScopeRegistry) >> Mock(ClassLoaderScopeRegistry)
        _ * serviceRegistry.get(FileResolver) >> Mock(FileResolver)
        _ * serviceRegistry.get(ScriptHandler) >> Mock(ScriptHandler)
        _ * serviceRegistry.get(TaskGraphExecuter) >> Mock(TaskGraphExecuter)
        _ * serviceRegistry.newInstance(TaskContainerInternal) >> Mock(TaskContainerInternal)
        _ * serviceRegistry.get(ModelRegistry) >> Stub(ModelRegistry)
        _ * serviceRegistry.get(Instantiator) >> Mock(Instantiator)
        _ * serviceRegistry.get(ListenerManager) >> listenerManager
        _ * serviceRegistry.get(CurrentGradleInstallation) >> currentGradleInstallation
        _ * serviceRegistry.get(BuildOperationExecutor) >> buildOperationExecutor
        _ * serviceRegistry.get(ProjectConfigurator) >> projectConfigurator

        gradle = classGenerator.newInstance(DefaultGradle.class, null, parameter, serviceRegistryFactory)
    }

    def "uses gradle version"() {
        expect:
        gradle.gradleVersion == GradleVersion.current().version
    }

    def "uses distribution locator for gradle home dir"() {
        given:
        def gradleHome = new File("home")
        1 * currentGradleInstallation.installation >> new GradleInstallation(gradleHome)

        expect:
        gradle.gradleHomeDir == gradleHome
    }

    def "uses start parameter for user dir"() {
        given:
        parameter.gradleUserHomeDir = new File("user")

        expect:
        gradle.gradleUserHomeDir == new File("user").canonicalFile
    }

    def "broadcasts before project evaluate events to closures"() {
        given:
        def called = false
        def closure = { called = true }

        when:
        gradle.beforeProject(closure)

        and:
        gradle.projectEvaluationBroadcaster.beforeEvaluate(null)

        then:
        called
    }

    def "broadcasts after project evaluate events to closures"() {
        given:
        def called = false
        def closure = { called = true }

        when:
        gradle.afterProject(closure)

        and:
        gradle.projectEvaluationBroadcaster.afterEvaluate(null, null)

        then:
        called
    }

    def "broadcasts build started events to closures"() {
        given:
        def called = false
        def closure = { called = true }

        when:
        gradle.buildStarted(closure)

        and:
        gradle.buildListenerBroadcaster.buildStarted(gradle)

        then:
        called
    }

    def "broadcasts settings evaluated events to closures"() {
        given:
        def called = false
        def closure = { called = true }

        when:
        gradle.settingsEvaluated(closure)

        and:
        gradle.buildListenerBroadcaster.settingsEvaluated(null)

        then:
        called
    }

    def "broadcasts projects loaded events to closures"() {
        given:
        def called = false
        def closure = { called = true }

        when:
        gradle.projectsLoaded(closure)

        and:
        gradle.buildListenerBroadcaster.projectsLoaded(gradle)

        then:
        called
    }

    def "broadcasts projects evaluated events to closures"() {
        given:
        def called = false
        def closure = { called = true }

        when:
        gradle.projectsEvaluated(closure)

        and:
        gradle.buildListenerBroadcaster.projectsEvaluated(gradle)

        then:
        called
    }

    def "broadcasts build finished events to closures"() {
        given:
        def called = false
        def closure = { called = true }

        when:
        gradle.buildFinished(closure)

        and:
        gradle.buildListenerBroadcaster.buildFinished(null)

        then:
        called
    }

    def "broadcasts before project evaluate events to actions"() {
        given:
        def action = Mock(Action)

        when:
        gradle.beforeProject(action)

        and:
        gradle.projectEvaluationBroadcaster.beforeEvaluate(null)

        then:
        1 * action.execute(_)
    }

    def "broadcasts after project evaluate events to actions"() {
        given:
        def action = Mock(Action)

        when:
        gradle.afterProject(action)

        and:
        gradle.projectEvaluationBroadcaster.afterEvaluate(null, null)

        then:
        1 * action.execute(_)
    }

    def "broadcasts build started events to actions"() {
        given:
        def action = Mock(Action)

        when:
        gradle.buildStarted(action)

        and:
        gradle.buildListenerBroadcaster.buildStarted(gradle)

        then:
        1 * action.execute(gradle)
    }

    def "broadcasts settings evaluated events to actions"() {
        given:
        def action = Mock(Action)

        when:
        gradle.settingsEvaluated(action)

        and:
        gradle.buildListenerBroadcaster.settingsEvaluated(null)

        then:
        1 * action.execute(_)
    }

    def "broadcasts projects loaded events to actions"() {
        given:
        def action = Mock(Action)

        when:
        gradle.projectsLoaded(action)

        and:
        gradle.buildListenerBroadcaster.projectsLoaded(gradle)

        then:
        1 * action.execute(gradle)
    }

    def "broadcasts projects evaluated events to actions"() {
        given:
        def action = Mock(Action)

        when:
        gradle.projectsEvaluated(action)

        and:
        gradle.buildListenerBroadcaster.projectsEvaluated(gradle)

        then:
        1 * action.execute(gradle)
    }

    def "broadcasts build finished events to actions"() {
        given:
        def action = Mock(Action)

        when:
        gradle.buildFinished(action)

        and:
        gradle.buildListenerBroadcaster.buildFinished(null)

        then:
        1 * action.execute(_)
    }

    def "uses specified logger"() {
        given:
        def logger = new Object()

        when:
        gradle.useLogger(logger)

        then:
        1 * listenerManager.useLogger(logger)
    }

    def "get root project throws exception when root project is not available"() {
        when:
        gradle.rootProject

        then:
        thrown IllegalStateException

        when:
        def rootProject = project('root')
        gradle.rootProject = rootProject

        then:
        gradle.rootProject == rootProject
    }

    def "root project action is executed when projects are loaded"() {
        given:
        def action = Mock(Action)
        def rootProject = project('root')
        gradle.rootProject = rootProject

        when:
        gradle.rootProject(action)

        then:
        0 * action.execute(_)

        when:
        gradle.buildListenerBroadcaster.projectsLoaded(gradle)

        then:
        1 * action.execute(rootProject)
    }

    def "allprojects action is executed when projects are loaded"() {
        given:
        def action = Mock(Action)
        def rootProject = project('root')
        gradle.rootProject = rootProject

        when:
        gradle.allprojects(action)

        then:
        0 * action.execute(_)

        when:
        gradle.buildListenerBroadcaster.projectsLoaded(gradle)

        then:
        1 * rootProject.allprojects(action)
        1 * action.execute(rootProject)
    }

    def "has toString()"() {
        expect:
        gradle.toString() == 'build'

        when:
        gradle.rootProject = project('rootProject')

        then:
        gradle.toString() == "build 'rootProject'"
    }

    def "has identity path"() {
        given:
        def child1 = classGenerator.newInstance(DefaultGradle, gradle, Stub(StartParameter), serviceRegistryFactory)
        child1.rootProject = project('child1')

        and:
        def child2 = classGenerator.newInstance(DefaultGradle, child1, Stub(StartParameter), serviceRegistryFactory)
        child2.rootProject = project('child2')

        expect:
        gradle.identityPath == Path.ROOT
        child1.identityPath == Path.path(":child1")
        child2.identityPath == Path.path(":child1:child2")
    }

    def projectRegistry = new DefaultProjectRegistry()

    private ProjectInternal project(String name) {
        def project = Spy(DefaultProject, constructorArgs: [
            name,
            null, null, Stub(ScriptSource),
            gradle, serviceRegistryFactory,
            Stub(ClassLoaderScope), Stub(ClassLoaderScope)
        ])
        project.getProjectConfigurator() >> projectConfigurator
        projectRegistry.addProject(project)
        _ * project.getProjectRegistry() >> projectRegistry
        return project
    }
}
