/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.problems.internal.services

import org.gradle.api.problems.internal.TaskIdentity
import org.gradle.api.problems.internal.location.BuildFrame
import org.gradle.api.problems.internal.location.Locality
import org.gradle.api.problems.internal.location.ProjectFrame
import org.gradle.api.problems.internal.location.TaskFrame
import spock.lang.Specification

class DetectionTracesTest extends Specification {

    def "task in the root build yields task, project and build, leaf first"() {
        when:
        def frames = DetectionTraces.detectedIn(new TaskIdentity(":app:test", ":app", ":")).frames

        then:
        frames*.class == [TaskFrame, ProjectFrame, BuildFrame]
        frames*.displayName == ["task :app:test", "project :app", "build :"]
        frames[0].buildTreePath == ":app:test"
        frames[1].buildTreePath == ":app"
        frames[2].buildPath == ":"
        frames*.locality.unique() == [Locality.UNKNOWN]
    }

    def "task in an included build also yields the root build"() {
        when:
        def frames = DetectionTraces.detectedIn(new TaskIdentity(":lib:core:compileJava", ":lib:core", ":lib")).frames

        then:
        frames*.class == [TaskFrame, ProjectFrame, BuildFrame, BuildFrame]
        frames*.displayName == ["task :lib:core:compileJava", "project :lib:core", "build :lib", "build :"]
    }

    def "without a task the trace holds the root build alone"() {
        when:
        def frames = DetectionTraces.detectedIn(null).frames

        then:
        frames*.class == [BuildFrame]
        frames*.displayName == ["build :"]
    }
}
