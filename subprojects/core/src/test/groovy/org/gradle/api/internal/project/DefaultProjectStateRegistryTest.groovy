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
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.Path

class DefaultProjectStateRegistryTest extends ConcurrentSpec {
    def registry = new DefaultProjectStateRegistry()

    def "adds projects for a build"() {
        given:
        def build = build("p1", "p2")
        registry.registerProjects(build)

        expect:
        registry.allProjects.size() == 3

        def root = registry.stateFor(project(":"))
        root.name == "root"
        root.componentIdentifier.projectPath == ":"
        root.parent == null
        def p1 = registry.stateFor(project("p1"))
        p1.name == "p1"
        p1.parent == root
        p1.componentIdentifier.projectPath == ":p1"
        def p2 = registry.stateFor(project("p2"))
        p2.name == "p2"
        p2.parent == root
        p2.componentIdentifier.projectPath == ":p2"

        registry.stateFor(root.componentIdentifier).is(root)
        registry.stateFor(p1.componentIdentifier).is(p1)
        registry.stateFor(p2.componentIdentifier).is(p2)

        registry.stateFor(build.buildIdentifier, Path.ROOT).is(root)
        registry.stateFor(build.buildIdentifier, Path.path(":p1")).is(p1)
        registry.stateFor(build.buildIdentifier, Path.path(":p2")).is(p2)
    }

    def "one thread can access state at a time"() {
        given:
        def build = build("p1")
        def project = project("p1")

        registry.registerProjects(build)
        def state = registry.stateFor(project)

        when:
        async {
            start {
                state.withMutableState {
                    instant.start
                    thread.block()
                    instant.thread1
                }
            }
            start {
                thread.blockUntil.start
                state.withMutableState {
                    instant.thread2
                }
            }
        }

        then:
        instant.thread2 > instant.thread1
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
}
