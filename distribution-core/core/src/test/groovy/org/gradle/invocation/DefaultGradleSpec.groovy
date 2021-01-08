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


import org.gradle.api.Action
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator
import org.gradle.api.internal.project.CrossProjectConfigurator
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.DefaultProjectRegistry
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.configuration.internal.ListenerBuildOperationDecorator
import org.gradle.configuration.internal.TestListenerBuildOperationDecorator
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.internal.management.DependencyResolutionManagementInternal
import org.gradle.internal.build.DefaultPublicBuildPath
import org.gradle.internal.build.PublicBuildPath
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.installation.GradleInstallation
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.util.GradleVersion
import org.gradle.util.Path
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.function.Consumer

class DefaultGradleSpec extends Specification {
    ServiceRegistryFactory serviceRegistryFactory = Stub(ServiceRegistryFactory)
    ListenerManager listenerManager = Spy(TestListenerManager)

    StartParameterInternal parameter = new StartParameterInternal()
    CurrentGradleInstallation currentGradleInstallation = Mock(CurrentGradleInstallation)
    BuildOperationExecutor buildOperationExecutor = new TestBuildOperationExecutor()
    ListenerBuildOperationDecorator listenerBuildOperationDecorator = new TestListenerBuildOperationDecorator()
    CrossProjectConfigurator crossProjectConfigurator = new BuildOperationCrossProjectConfigurator(buildOperationExecutor)
    ProjectState projectState = Mock(ProjectState)

    GradleInternal gradle

    def setup() {
        def serviceRegistry = Stub(ServiceRegistry)
        _ * serviceRegistryFactory.createFor(_) >> serviceRegistry
        _ * serviceRegistry.get(ClassLoaderScopeRegistry) >> Mock(ClassLoaderScopeRegistry)
        _ * serviceRegistry.get(FileResolver) >> Mock(FileResolver)
        _ * serviceRegistry.get(ScriptHandler) >> Mock(ScriptHandler)
        _ * serviceRegistry.get(TaskExecutionGraphInternal) >> Mock(TaskExecutionGraphInternal)
        _ * serviceRegistry.get(TaskContainerInternal) >> Mock(TaskContainerInternal)
        _ * serviceRegistry.get(ModelRegistry) >> Stub(ModelRegistry)
        _ * serviceRegistry.get(InstantiatorFactory) >> Mock(InstantiatorFactory)
        _ * serviceRegistry.get(ListenerManager) >> listenerManager
        _ * serviceRegistry.get(CurrentGradleInstallation) >> currentGradleInstallation
        _ * serviceRegistry.get(BuildOperationExecutor) >> buildOperationExecutor
        _ * serviceRegistry.get(ListenerBuildOperationDecorator) >> listenerBuildOperationDecorator
        _ * serviceRegistry.get(CrossProjectConfigurator) >> crossProjectConfigurator
        _ * serviceRegistry.get(PublicBuildPath) >> new DefaultPublicBuildPath(Path.ROOT)
        _ * serviceRegistry.get(DependencyResolutionManagementInternal) >> Stub(DependencyResolutionManagementInternal)
        _ * serviceRegistry.get(GradleEnterprisePluginManager) >> new GradleEnterprisePluginManager()

        gradle = TestUtil.instantiatorFactory().decorateLenient().newInstance(DefaultGradle.class, null, parameter, serviceRegistryFactory)
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

    def "broadcasts before settings events to actions"() {
        given:
        def action = Mock(Action)

        when:
        gradle.beforeSettings(action)

        and:
        gradle.buildListenerBroadcaster.beforeSettings(null)

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

    def "get settings throws exception when settings is not available"() {
        when:
        gradle.settings

        then:
        thrown IllegalStateException

        when:
        def settings = Stub(SettingsInternal)
        gradle.settings = settings

        then:
        gradle.settings == settings
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

    def projectRegistry = new DefaultProjectRegistry()

    private ProjectInternal project(String name) {
        def project = Spy(DefaultProject, constructorArgs: [
            name,
            null, null, null, Stub(ScriptSource),
            gradle, Stub(ProjectState), serviceRegistryFactory,
            Stub(ClassLoaderScope), Stub(ClassLoaderScope)
        ])
        project.getProjectConfigurator() >> crossProjectConfigurator
        projectRegistry.addProject(project)
        _ * project.getProjectRegistry() >> projectRegistry
        _ * project.getMutationState() >> projectState
        _ * projectState.applyToMutableState(_) >> { Consumer consumer -> consumer.accept(project) }
        return project
    }

    static class TestListenerManager extends DefaultListenerManager {
        TestListenerManager() {
            super(Scopes.Build)
        }
    }
}
