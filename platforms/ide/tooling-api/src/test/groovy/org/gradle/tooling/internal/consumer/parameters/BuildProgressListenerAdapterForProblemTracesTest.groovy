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

package org.gradle.tooling.internal.consumer.parameters

import org.gradle.internal.build.event.types.DefaultBuildFrame
import org.gradle.internal.build.event.types.DefaultFrame
import org.gradle.internal.build.event.types.DefaultProjectFrame
import org.gradle.internal.build.event.types.DefaultTaskFrame
import org.gradle.internal.build.event.types.DefaultTrace
import org.gradle.tooling.events.problems.BuildFrame
import org.gradle.tooling.events.problems.Locality
import org.gradle.tooling.events.problems.ProjectFrame
import org.gradle.tooling.events.problems.TaskFrame
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion4
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion5
import org.gradle.tooling.internal.protocol.problem.InternalFrame
import spock.lang.Specification

class BuildProgressListenerAdapterForProblemTracesTest extends Specification {

    def "typed frames arrive as their public types, leaf first"() {
        given:
        def details = detailsWith(
            new DefaultTaskFrame("task :app:test", "UNKNOWN", ":app:test"),
            new DefaultProjectFrame("project :app", "LOCAL", ":app"),
            new DefaultBuildFrame("build :", "UNKNOWN", ":")
        )

        when:
        def frames = BuildProgressListenerAdapter.toDetectionTrace(details).frames

        then:
        frames.size() == 3
        frames[2] instanceof BuildFrame
        frames[2].buildPath == ":"
        frames[0] instanceof TaskFrame
        frames[0].buildTreePath == ":app:test"
        frames[0].displayName == "task :app:test"
        frames[0].locality == Locality.UNKNOWN
        frames[1] instanceof ProjectFrame
        frames[1].buildTreePath == ":app"
        frames[1].locality == Locality.LOCAL
    }

    def "a frame kind the client has no type for stays in its place as a plain frame"() {
        given:
        def futureKind = Stub(FrameKindFromTheFuture) {
            getDisplayName() >> "source set main"
            getLocality() >> "LOCAL"
            getName() >> "main"
        }
        def details = detailsWith(
            new DefaultTaskFrame("task :app:test", "UNKNOWN", ":app:test"),
            futureKind,
            new DefaultProjectFrame("project :app", "UNKNOWN", ":app")
        )

        when:
        def frames = BuildProgressListenerAdapter.toDetectionTrace(details).frames

        then:
        frames*.displayName == ["task :app:test", "source set main", "project :app"]
        frames[0] instanceof TaskFrame
        !(frames[1] instanceof TaskFrame)
        !(frames[1] instanceof ProjectFrame)
        !(frames[1] instanceof BuildFrame)
        frames[1].locality == Locality.LOCAL
        frames[2] instanceof ProjectFrame
    }

    def "a locality the client doesn't know becomes UNKNOWN"() {
        given:
        def details = detailsWith(new DefaultFrame("source set main", "VENDORED"))

        expect:
        BuildProgressListenerAdapter.toDetectionTrace(details).frames[0].locality == Locality.UNKNOWN
    }

    def "no trace when the build doesn't send one"() {
        given:
        def olderDetails = Stub(InternalBasicProblemDetailsVersion4)
        def detailsWithoutTrace = Stub(InternalBasicProblemDetailsVersion5) {
            getDetectionTrace() >> null
        }

        expect:
        BuildProgressListenerAdapter.toDetectionTrace(olderDetails) == null
        BuildProgressListenerAdapter.toDetectionTrace(detailsWithoutTrace) == null
    }

    private InternalBasicProblemDetailsVersion5 detailsWith(InternalFrame... frames) {
        Stub(InternalBasicProblemDetailsVersion5) {
            getDetectionTrace() >> new DefaultTrace(frames.toList())
        }
    }

    /**
     * Stands for a protocol interface that a newer Gradle version has and this client doesn't.
     */
    interface FrameKindFromTheFuture extends InternalFrame {
        String getName()
    }
}
