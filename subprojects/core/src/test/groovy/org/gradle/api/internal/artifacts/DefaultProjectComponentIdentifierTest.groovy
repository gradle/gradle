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
package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.util.Path
import spock.lang.Specification

import static org.gradle.util.Matchers.strictlyEquals

class DefaultProjectComponentIdentifierTest extends Specification {
    def "is instantiated with non-null constructor parameter values"() {
        when:
        def id = new DefaultProjectComponentIdentifier(Stub(BuildIdentifier), Path.path(":id:path"), Path.path(":project:path"), "projectName")

        then:
        id.projectPath == ':project:path'
        id.projectName == 'projectName'
        id.displayName == 'project :id:path'
        id.buildTreePath == ':id:path'
        id.toString() == 'project :id:path'
    }

    def "can compare with other instance (#projectPath)"() {
        expect:
        def id1 = newProjectId(':myProjectPath1')
        def id2 = newProjectId(projectPath)
        strictlyEquals(id1, id2) == equality
        (id1.hashCode() == id2.hashCode()) == hashCode
        (id1.toString() == id2.toString()) == stringRepresentation

        where:
        projectPath       | equality | hashCode | stringRepresentation
        ':myProjectPath1' | true     | true     | true
        ':myProjectPath2' | false    | false    | false
    }

    private static newProjectId(String path) {
        newProjectId(DefaultBuildIdentifier.ROOT, path)
    }

    private static newProjectId(BuildIdentifier build, String path) {
        new DefaultProjectComponentIdentifier(build, Path.path(path), Path.path(path), "name")
    }

}
