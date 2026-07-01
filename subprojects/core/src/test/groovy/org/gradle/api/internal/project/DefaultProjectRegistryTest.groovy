/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.internal.build.BuildProjectRegistry
import org.gradle.util.Path
import spock.lang.Specification

class DefaultProjectRegistryTest extends Specification {

    ProjectState rootMock = newState()
    ProjectState childMock = newState()
    ProjectState childChildMock = newState()

    BuildProjectRegistry delegate = Mock(BuildProjectRegistry) {
        findProject(Path.path(":")) >> rootMock
        findProject(Path.path(":child")) >> childMock
        findProject(Path.path(":child:child")) >> childChildMock
    }

    DefaultProjectRegistry projectRegistry = new DefaultProjectRegistry(delegate)

    def "returns projects if present"() {
        expect:
        projectRegistry.getProject(":").is(rootMock.mutableModel)
        projectRegistry.getProject(":child").is(childMock.mutableModel)
        projectRegistry.getProject(":child:child").is(childChildMock.mutableModel)
    }

    def "returns null if no project"() {
        expect:
        projectRegistry.getProject(":foo") == null
    }

    def newState() {
        def project = Mock(ProjectInternal)
        def state = Mock(ProjectState) {
            getMutableModel() >> project
        }
        project.getOwner() >> state
        return state
    }

}
