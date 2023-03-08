/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory

import org.gradle.api.DefaultTask
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.util.Path
import spock.lang.Specification

class TaskIdentityTest extends Specification {
    def projectPath = Path.path(":my-project")
    def gradleInternal = Stub(GradleInternal) {
        getIdentityPath() >> Path.path(":")
    }
    def project = Stub(ProjectInternal) {
        projectPath(_ as String) >> { String name -> projectPath.append(Path.path(name))}
        identityPath(_ as String) >> { String name -> projectPath.append(Path.path(name)) }
        getGradle() >> gradleInternal
    }

    def "possibly increments id when unique id is passed in"() {
        when:
        def id1 = TaskIdentity.create("first", DefaultTask, project)
        def id2 = TaskIdentity.create("second", DefaultTask, project)
        then:
        id1.id < id2.id

        when:
        def recreatedId1 = TaskIdentity.create("first", DefaultTask, project, id1.id)
        def id3 = TaskIdentity.create("third", DefaultTask, project)
        then:
        recreatedId1.id < id2.id
        id2.id < id3.id

        when:
        def id4WithManualId = TaskIdentity.create("forth", DefaultTask, project, id3.id + 100)
        def id5 = TaskIdentity.create("fifth", DefaultTask, project)
        then:
        id3.id < id4WithManualId.id
        id4WithManualId.id < id5.id

    }
}
