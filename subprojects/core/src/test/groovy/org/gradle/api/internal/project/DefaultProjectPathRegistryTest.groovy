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
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultProjectDescriptorRegistry
import org.gradle.internal.build.BuildState
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.util.Path

class DefaultProjectPathRegistryTest extends ConcurrentSpec {
    def registry = new DefaultProjectPathRegistry()

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
        project.identityPath >> Path.ROOT.child(name)
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
        build.getIdentityPathForProject(_) >> { Path path -> path }
        return build
    }
}
