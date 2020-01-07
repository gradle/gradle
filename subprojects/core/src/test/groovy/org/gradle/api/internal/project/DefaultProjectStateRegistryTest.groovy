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

import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultProjectDescriptorRegistry
import org.gradle.internal.build.BuildState
import org.gradle.internal.concurrent.ParallelismConfigurationManagerFixture
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.Path

class DefaultProjectStateRegistryTest extends ConcurrentSpec {
    def workerLeaseService =  new DefaultWorkerLeaseService(new DefaultResourceLockCoordinationService(), new ParallelismConfigurationManagerFixture(true, 4))
    def parentLease = workerLeaseService.getWorkerLease()
    def registry = new DefaultProjectStateRegistry(workerLeaseService)

    def "adds projects for a build"() {
        given:
        def build = build("p1", "p2")
        registry.registerProjects(build)

        expect:
        registry.allProjects.size() == 3

        def root = registry.stateFor(project(":"))
        root.name == "root"
        root.identityPath == Path.ROOT
        root.projectPath == Path.ROOT
        root.componentIdentifier.projectPath == ":"
        root.parent == null

        def p1 = registry.stateFor(project("p1"))
        p1.name == "p1"
        p1.identityPath == Path.path(":p1")
        p1.projectPath == Path.path(":p1")
        p1.parent == root
        p1.componentIdentifier.projectPath == ":p1"

        def p2 = registry.stateFor(project("p2"))
        p2.name == "p2"
        p2.identityPath == Path.path(":p2")
        p2.projectPath == Path.path(":p2")
        p2.parent == root
        p2.componentIdentifier.projectPath == ":p2"

        registry.stateFor(root.componentIdentifier).is(root)
        registry.stateFor(p1.componentIdentifier).is(p1)
        registry.stateFor(p2.componentIdentifier).is(p2)

        registry.stateFor(build.buildIdentifier, Path.ROOT).is(root)
        registry.stateFor(build.buildIdentifier, Path.path(":p1")).is(p1)
        registry.stateFor(build.buildIdentifier, Path.path(":p2")).is(p2)
    }

    def "can attach mutable project instance"() {
        given:
        def build = build("p1", "p2")
        registry.registerProjects(build)
        def project = project("p1")

        def state = registry.stateFor(project)

        state.attachMutableModel(project)

        expect:
        state.mutableModel == project
    }

    def "cannot query mutable project instance when not set"() {
        given:
        def build = build("p1", "p2")
        registry.registerProjects(build)
        def project = project("p1")

        def state = registry.stateFor(project)

        when:
        state.mutableModel

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The project object for project :p1 has not been attached yet.'
    }

    def "one thread can access state at a time"() {
        given:
        def build = build("p1")
        def project = project("p1")

        registry.registerProjects(build)
        def state = registry.stateFor(project)

        when:
        async {
            workerThread {
                state.withMutableState {
                    instant.start
                    thread.block()
                    instant.thread1
                }
            }
            workerThread {
                thread.blockUntil.start
                state.withMutableState {
                    instant.thread2
                }
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

        registry.registerProjects(build)
        def state1 = registry.stateFor(project1)
        def state2 = registry.stateFor(project2)

        def projectLock1 = workerLeaseService.getProjectLock(build.getIdentityPath(), project1.getIdentityPath())
        def projectLock2 = workerLeaseService.getProjectLock(build.getIdentityPath(), project2.getIdentityPath())

        expect:
        async {
            workerThread {
                state1.withMutableState {
                    instant.start1
                    thread.blockUntil.start2
                    state2.withMutableState {
                        assert workerLeaseService.getCurrentProjectLocks().contains(projectLock2)
                        assert !workerLeaseService.getCurrentProjectLocks().contains(projectLock1)
                        instant.thread1
                        thread.blockUntil.thread2
                    }
                }
            }
            workerThread {
                state2.withMutableState {
                    instant.start2
                    thread.blockUntil.start1
                    state1.withMutableState {
                        assert workerLeaseService.getCurrentProjectLocks().contains(projectLock1)
                        assert !workerLeaseService.getCurrentProjectLocks().contains(projectLock2)
                        instant.thread2
                        thread.blockUntil.thread1
                    }
                }
            }
        }
    }

    def "can access projects with lenient state"() {
        given:
        def build = build("p1", "p2")
        registry.registerProjects(build)

        expect:
        !registry.stateFor(project("p1")).hasMutableState()

        and:
        registry.withLenientState({ assert registry.stateFor(project("p1")).hasMutableState() })

        and:
        !registry.stateFor(project("p1")).hasMutableState()
    }

    ProjectInternal project(String name) {
        def project = Stub(ProjectInternal)
        project.identityPath >> (name == ':' ? Path.ROOT : Path.ROOT.child(name))
        return project
    }

    BuildState build(String... projects) {
        def descriptors = new DefaultProjectDescriptorRegistry()
        def root = new DefaultProjectDescriptor(null, "root", null, descriptors, null)
        descriptors.addProject(root)
        projects.each {
            descriptors.addProject(new DefaultProjectDescriptor(root, it, null, descriptors, null))
        }

        def settings = Stub(SettingsInternal)
        settings.projectRegistry >> descriptors

        def build = Stub(BuildState)
        build.loadedSettings >> settings
        build.buildIdentifier >> DefaultBuildIdentifier.ROOT
        build.getIdentityPathForProject(_) >> { Path path -> path }
        build.getIdentifierForProject(_) >> { Path path -> new DefaultProjectComponentIdentifier(DefaultBuildIdentifier.ROOT, path, path, "??") }
        return build
    }

    void workerThread(Closure closure) {
        start {
            workerLeaseService.withLocks([parentLease.createChild()], closure)
        }
    }
}
