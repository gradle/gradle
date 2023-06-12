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

package org.gradle.api.internal.project

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultProjectDescriptorRegistry
import org.gradle.internal.build.BuildState
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.Path
import org.gradle.util.TestUtil

import static org.junit.Assert.assertTrue

class DefaultProjectStateRegistryTest extends ConcurrentSpec {
    def workerLeaseService = new DefaultWorkerLeaseService(new DefaultResourceLockCoordinationService(), new DefaultParallelismConfiguration(true, 4))
    def registry = new DefaultProjectStateRegistry(workerLeaseService)
    def projectFactory = Mock(IProjectFactory)

    def setup() {
        workerLeaseService.startProjectExecution(true)
    }

    def "adds projects for a build"() {
        given:
        def build = build("p1", "p2")

        expect:
        registry.allProjects.size() == 3

        def root = registry.stateFor(projectId(":"))
        root.name == "root"
        root.displayName.displayName == "root project 'root'"
        root.identityPath == Path.ROOT
        root.projectPath == Path.ROOT
        root.componentIdentifier.projectPath == ":"
        root.componentIdentifier.buildTreePath == ":"
        root.parent == null

        def p1 = registry.stateFor(projectId("p1"))
        p1.name == "p1"
        p1.displayName.displayName == "project ':p1'"
        p1.identityPath == Path.path(":p1")
        p1.projectPath == Path.path(":p1")
        p1.parent.is(root)
        p1.componentIdentifier.projectPath == ":p1"
        p1.componentIdentifier.buildTreePath == ":p1"
        p1.childProjects.empty

        def p2 = registry.stateFor(projectId("p2"))
        p2.name == "p2"
        p2.displayName.displayName == "project ':p2'"
        p2.identityPath == Path.path(":p2")
        p2.projectPath == Path.path(":p2")
        p2.parent.is(root)
        p2.componentIdentifier.projectPath == ":p2"
        p2.componentIdentifier.buildTreePath == ":p2"
        p2.childProjects.empty

        root.childProjects.toList() == [p1, p2]

        registry.stateFor(root.componentIdentifier).is(root)
        registry.stateFor(p1.componentIdentifier).is(p1)
        registry.stateFor(p2.componentIdentifier).is(p2)

        def projects = registry.projectsFor(build.buildIdentifier)
        projects.rootProject.is(root)
        projects.getProject(Path.ROOT).is(root)
        projects.getProject(Path.path(":p1")).is(p1)
        projects.getProject(Path.path(":p2")).is(p2)

        projects.allProjects.toList() == [root, p1, p2]
    }

    def "can create mutable project model"() {
        given:
        def build = build("p1", "p2")

        def rootProject = project(":")
        def rootState = registry.stateFor(projectId(":"))

        1 * projectFactory.createProject(_, _, rootState, _, _, _, _) >> rootProject

        rootState.createMutableModel(Stub(ClassLoaderScope), Stub(ClassLoaderScope))

        def project = project("p1")
        def state = registry.stateFor(projectId("p1"))

        1 * projectFactory.createProject(_, _, state, _, _, _, _) >> project

        state.createMutableModel(Stub(ClassLoaderScope), Stub(ClassLoaderScope))

        expect:
        rootState.mutableModel == rootProject
        state.mutableModel == project
    }

    def "cannot query mutable project instance when not set"() {
        given:
        def build = build("p1", "p2")

        def state = registry.stateFor(projectId("p1"))

        when:
        state.mutableModel

        then:
        def e = thrown(IllegalStateException)
        e.message == "Project ':p1' should be in state Created or later."
    }

    def "one thread can access state at a time"() {
        given:
        def build = build("p1")
        def project = project("p1")

        createRootProject()
        def state = registry.stateFor(projectId("p1"))
        createProject(state, project)

        when:
        async {
            workerThread {
                assert !state.hasMutableState()
                state.applyToMutableState {
                    assert state.hasMutableState()
                    instant.start
                    thread.block()
                    state.applyToMutableState {
                        // nested
                    }
                    assert state.hasMutableState()
                    instant.thread1
                }
                assert !state.hasMutableState()
            }
            workerThread {
                thread.blockUntil.start
                assert !state.hasMutableState()
                state.applyToMutableState {
                    assert state.hasMutableState()
                    instant.thread2
                }
                assert !state.hasMutableState()
            }
        }

        then:
        instant.thread2 > instant.thread1
    }

    def "a given thread can only access the state of one project at a time"() {
        given:
        def build = build("p1", "p2")
        def project1 = project("p1")
        def project2 = project("p2")

        createRootProject()
        def state1 = registry.stateFor(projectId("p1"))
        createProject(state1, project1)
        def state2 = registry.stateFor(projectId("p2"))
        createProject(state2, project2)

        def projectLock1 = workerLeaseService.getProjectLock(build.getIdentityPath(), project1.getIdentityPath())
        def projectLock2 = workerLeaseService.getProjectLock(build.getIdentityPath(), project2.getIdentityPath())

        expect:
        async {
            workerThread {
                state1.applyToMutableState {
                    instant.start1
                    thread.blockUntil.start2
                    state2.applyToMutableState {
                        assert workerLeaseService.getCurrentProjectLocks().contains(projectLock2)
                        assert !workerLeaseService.getCurrentProjectLocks().contains(projectLock1)
                        instant.thread1
                        thread.blockUntil.thread2
                    }
                }
            }
            workerThread {
                state2.applyToMutableState {
                    instant.start2
                    thread.blockUntil.start1
                    state1.applyToMutableState {
                        assert workerLeaseService.getCurrentProjectLocks().contains(projectLock1)
                        assert !workerLeaseService.getCurrentProjectLocks().contains(projectLock2)
                        instant.thread2
                        thread.blockUntil.thread1
                    }
                }
            }
        }
    }

    def "can access projects with all projects locked"() {
        given:
        def build = build("p1", "p2")
        def state = registry.stateFor(projectId("p1"))
        def projects = registry.projectsFor(build.buildIdentifier)

        expect:
        !state.hasMutableState()

        and:
        projects.withMutableStateOfAllProjects {
            assert state.hasMutableState()
            projects.withMutableStateOfAllProjects {
                assert state.hasMutableState()
            }
            assert state.hasMutableState()
        }

        and:
        !state.hasMutableState()
    }

    def "one thread can access all project state at a time"() {
        given:
        def build = build("p1")
        def project = project("p1")

        createRootProject()
        def state = registry.stateFor(projectId("p1"))
        createProject(state, project)
        def projects = registry.projectsFor(build.buildIdentifier)

        when:
        async {
            workerThread {
                assert !state.hasMutableState()
                projects.withMutableStateOfAllProjects {
                    assert state.hasMutableState()
                    instant.start
                    thread.block()
                    projects.withMutableStateOfAllProjects {
                        // nested
                    }
                    assert state.hasMutableState()
                    instant.thread1
                }
                assert !state.hasMutableState()
            }
            workerThread {
                thread.blockUntil.start
                assert !state.hasMutableState()
                projects.withMutableStateOfAllProjects {
                    assert state.hasMutableState()
                    instant.thread2
                }
                assert !state.hasMutableState()
            }
        }

        then:
        instant.thread2 > instant.thread1
    }

    def "cannot lock project state while another thread has locked all projects"() {
        given:
        def build = build("p1", "p2")
        createRootProject()
        def state = registry.stateFor(projectId("p1"))
        createProject(state, project("p1"))
        def projects = registry.projectsFor(build.buildIdentifier)

        when:
        async {
            workerThread {
                projects.withMutableStateOfAllProjects {
                    instant.start
                    thread.block()
                    instant.thread1
                }
            }
            workerThread {
                thread.blockUntil.start
                state.applyToMutableState {
                    instant.thread2
                }
            }
        }

        then:
        instant.thread2 > instant.thread1
    }

    def "releases lock for all projects while running blocking operation"() {
        given:
        def build = build("p1", "p2")
        def projects = registry.projectsFor(build.buildIdentifier)

        when:
        async {
            workerThread {
                projects.withMutableStateOfAllProjects {
                    def state = registry.stateFor(projectId("p1"))
                    assert state.hasMutableState()
                    workerLeaseService.blocking {
                        assertTrue !state.hasMutableState()
                    }
                    assert state.hasMutableState()
                }
            }
        }

        then:
        noExceptionThrown()
    }

    def "thread can be granted uncontrolled access to all projects"() {
        given:
        def build = build("p1", "p2")
        createRootProject()
        def project1 = project("p1")
        def state1 = registry.stateFor(projectId("p1"))
        createProject(state1, project1)
        def state2 = registry.stateFor(projectId("p2"))

        when:
        async {
            start {
                state1.applyToMutableState { p ->
                    assert state1.hasMutableState()
                    assert !state2.hasMutableState()
                    instant.mutating1
                    thread.blockUntil.finished1
                }
                state1.applyToMutableState { p ->
                    assert state1.hasMutableState()
                    instant.mutating2
                    thread.blockUntil.finished2
                }
            }
            start {
                registry.allowUncontrolledAccessToAnyProject {
                    assert state1.hasMutableState()
                    assert state2.hasMutableState()
                    thread.blockUntil.mutating1
                    // both threads are accessing project
                    instant.finished1
                    thread.blockUntil.mutating2
                    // both threads are accessing project
                    instant.finished2
                }
            }
        }

        then:
        noExceptionThrown()
    }

    def "multiple threads can nest calls with uncontrolled access to all projects"() {
        given:
        def build = build("p1", "p2")
        createRootProject()
        def project1 = project("p1")
        def state1 = registry.stateFor(projectId("p1"))
        createProject(state1, project1)
        def state2 = registry.stateFor(projectId("p2"))

        when:
        async {
            def action = {
                registry.allowUncontrolledAccessToAnyProject {
                    assert state1.hasMutableState()
                    assert state2.hasMutableState()
                    registry.allowUncontrolledAccessToAnyProject {
                        assertTrue state1.hasMutableState()
                        assertTrue state2.hasMutableState()
                    }
                    state1.applyToMutableState {
                        assertTrue state1.hasMutableState()
                        assertTrue state2.hasMutableState()
                    }
                    assert state1.hasMutableState()
                    assert state2.hasMutableState()
                }
            }
            start(action)
            start(action)
            start(action)
            start(action)
            start(action)
        }

        then:
        noExceptionThrown()
    }

    def "thread can be granted uncontrolled access to a single project"() {
        given:
        def build = build("p1", "p2")
        createRootProject()
        def project1 = project("p1")
        def state1 = registry.stateFor(projectId("p1"))
        createProject(state1, project1)
        def state2 = registry.stateFor(projectId("p2"))

        when:
        async {
            start {
                state1.applyToMutableState { p ->
                    assert state1.hasMutableState()
                    assert !state2.hasMutableState()
                    instant.mutating1
                    thread.blockUntil.finished1
                }
                state1.applyToMutableState { p ->
                    assert state1.hasMutableState()
                    instant.mutating2
                    thread.blockUntil.finished2
                }
            }
            start {
                state1.forceAccessToMutableState {
                    assert state1.hasMutableState()
                    assert !state2.hasMutableState()
                    thread.blockUntil.mutating1
                    // both threads are accessing project
                    instant.finished1
                    thread.blockUntil.mutating2
                    // both threads are accessing project
                    instant.finished2
                }
            }
        }

        then:
        noExceptionThrown()
    }

    def "multiple threads can nest calls with uncontrolled access to specific projects"() {
        given:
        def build = build("p1", "p2")
        createRootProject()
        def project1 = project("p1")
        def state1 = registry.stateFor(projectId("p1"))
        createProject(state1, project1)
        def project2 = project("p2")
        def state2 = registry.stateFor(projectId("p2"))
        createProject(state2, project2)

        when:
        async {
            def action = {
                state1.forceAccessToMutableState {
                    assert state1.hasMutableState()
                    assert !state2.hasMutableState()
                    state1.forceAccessToMutableState {
                        assertTrue state1.hasMutableState()
                        assertTrue !state2.hasMutableState()
                    }
                    state1.applyToMutableState {
                        assertTrue state1.hasMutableState()
                        assertTrue !state2.hasMutableState()
                    }
                    state2.applyToMutableState {
                        assertTrue state1.hasMutableState()
                        assertTrue state2.hasMutableState()
                    }
                    assert state1.hasMutableState()
                    assert !state2.hasMutableState()
                }
            }
            start(action)
            start(action)
            start(action)
            start(action)
            start(action)
        }

        then:
        noExceptionThrown()
    }

    def "thread must own project state in order to set calculated value"() {
        given:
        def build = build("p1", "p2")
        createRootProject()
        def project1 = project("p1")
        def project2 = project("p2")
        def state1 = registry.stateFor(projectId("p1"))
        createProject(state1, project1)
        def state2 = registry.stateFor(projectId("p2"))
        createProject(state2, project2)
        def calculatedValue = state1.newCalculatedValue("initial")

        when:
        calculatedValue.set("bad")

        then:
        thrown(IllegalStateException)
        calculatedValue.get() == "initial"

        when:
        state1.applyToMutableState {
            calculatedValue.set("updated")
        }

        then:
        calculatedValue.get() == "updated"

        when:
        state1.applyToMutableState {
            state2.applyToMutableState {
                // Thread no longer owns the project state
                calculatedValue.set("bad")
            }
        }

        then:
        thrown(IllegalStateException)
        calculatedValue.get() == "updated"
    }

    def "thread must own project state in order to update calculated value"() {
        given:
        def build = build("p1", "p2")
        createRootProject()
        def project1 = project("p1")
        def project2 = project("p2")
        def state1 = registry.stateFor(projectId("p1"))
        createProject(state1, project1)
        def state2 = registry.stateFor(projectId("p2"))
        createProject(state2, project2)
        def calculatedValue = state1.newCalculatedValue("initial")

        when:
        calculatedValue.update { throw new RuntimeException() }

        then:
        thrown(IllegalStateException)
        calculatedValue.get() == "initial"

        when:
        state1.applyToMutableState {
            calculatedValue.update {
                assert it == "initial"
                "updated"
            }
        }

        then:
        calculatedValue.get() == "updated"

        when:
        state1.applyToMutableState {
            state2.applyToMutableState {
                // Thread no longer owns the project state
                calculatedValue.update { throw new RuntimeException() }
            }
        }

        then:
        thrown(IllegalStateException)
        calculatedValue.get() == "updated"
    }

    def "update thread blocks other update threads"() {
        given:
        def build = build("p1", "p2")
        createRootProject()
        def project1 = project("p1")
        def state1 = registry.stateFor(projectId("p1"))
        createProject(state1, project1)
        def calculatedValue = state1.newCalculatedValue("initial")

        when:
        async {
            workerThread {
                state1.applyToMutableState {
                    calculatedValue.update {
                        assert it == "initial"
                        instant.start
                        thread.block()
                        instant.thread1
                        "updated1"
                    }
                }
            }
            workerThread {
                thread.blockUntil.start
                state1.applyToMutableState {
                    calculatedValue.update {
                        assert it == "updated1"
                        instant.thread2
                        "updated2"
                    }
                }
            }
        }

        then:
        calculatedValue.get() == "updated2"
        instant.thread1 < instant.thread2
    }

    def "update thread does not block other read threads"() {
        given:
        def build = build("p1", "p2")
        createRootProject()
        def project1 = project("p1")
        def state1 = registry.stateFor(projectId("p1"))
        createProject(state1, project1)
        def calculatedValue = state1.newCalculatedValue("initial")

        when:
        async {
            workerThread {
                state1.applyToMutableState {
                    calculatedValue.update {
                        assert it == "initial"
                        instant.start
                        thread.blockUntil.read
                        "updated"
                    }
                }
            }
            workerThread {
                thread.blockUntil.start
                assert calculatedValue.get() == "initial"
                instant.read
            }
        }

        then:
        calculatedValue.get() == "updated"
    }

    def "can have cycle in project dependencies"() {
        given:
        def build = build("p1", "p2")
        createRootProject()
        def project1 = project("p1")
        def state1 = registry.stateFor(projectId("p1"))
        createProject(state1, project1)
        def project2 = project("p2")
        def state2 = registry.stateFor(projectId("p2"))
        createProject(state2, project2)
        def calculatedValue = state1.newCalculatedValue("initial")

        when:
        async {
            workerThread {
                state1.applyToMutableState {
                    instant.start
                    thread.blockUntil.start2
                    calculatedValue.update {
                        assert it == "initial"
                        state2.applyToMutableState {
                        }
                        "updated1"
                    }
                }
            }
            workerThread {
                thread.blockUntil.start
                state2.applyToMutableState {
                    instant.start2
                    state1.applyToMutableState {
                        calculatedValue.update {
                            assert it == "updated1"
                            "updated2"
                        }
                    }
                }
            }
        }

        then:
        calculatedValue.get() == "updated2"
    }

    void createRootProject() {
        def rootProject = project(':')
        def rootState = registry.stateFor(projectId(':'))

        1 * projectFactory.createProject(_, _, rootState, _, _, _, _) >> rootProject

        rootState.createMutableModel(Stub(ClassLoaderScope), Stub(ClassLoaderScope))
    }

    void createProject(ProjectState state, ProjectInternal project) {
        1 * projectFactory.createProject(_, _, state, _, _, _, _) >> project

        state.createMutableModel(Stub(ClassLoaderScope), Stub(ClassLoaderScope))
    }

    ProjectComponentIdentifier projectId(String name) {
        def path = name == ':' ? Path.ROOT : Path.ROOT.child(name)
        return new DefaultProjectComponentIdentifier(DefaultBuildIdentifier.ROOT, path, path, name)
    }

    ProjectInternal project(String name) {
        def project = Stub(ProjectInternal)
        def path = name == ':' ? Path.ROOT : Path.ROOT.child(name)
        project.identityPath >> path
        return project
    }

    BuildState build(String... projects) {
        def descriptors = new DefaultProjectDescriptorRegistry()
        def root = new DefaultProjectDescriptor(null, "root", null, descriptors, null)
        projects.each {
            new DefaultProjectDescriptor(root, it, null, descriptors, null)
        }

        def settings = Stub(SettingsInternal)
        settings.projectRegistry >> descriptors

        def build = Stub(BuildState)
        build.loadedSettings >> settings
        build.buildIdentifier >> DefaultBuildIdentifier.ROOT
        build.identityPath >> Path.ROOT
        build.calculateIdentityPathForProject(_) >> { Path path -> path }
        def services = new DefaultServiceRegistry()
        services.add(projectFactory)
        services.add(TestUtil.stateTransitionControllerFactory())
        build.mutableModel >> Stub(GradleInternal) {
            getServices() >> services
        }

        registry.registerProjects(build, descriptors)

        return build
    }

    void workerThread(Closure closure) {
        start {
            workerLeaseService.runAsWorkerThread(closure)
        }
    }
}
