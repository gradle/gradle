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

import org.gradle.api.problems.internal.DetectionContext
import org.gradle.api.problems.internal.ProjectIdentity
import org.gradle.api.problems.internal.TaskIdentity
import org.gradle.api.problems.internal.location.BuildFrame
import org.gradle.internal.Describables
import org.gradle.internal.code.Locality
import org.gradle.internal.code.UserCodeSource
import org.gradle.api.problems.internal.location.PluginApplicationFrame
import org.gradle.api.problems.internal.location.ProjectFrame
import org.gradle.api.problems.internal.location.TaskFrame
import spock.lang.Specification

class DetectionTracesTest extends Specification {

    def "task in the root build yields task, project and build, leaf first"() {
        when:
        def frames = DetectionTraces.detectedIn(task(":app:test", ":app", ":")).frames

        then:
        frames*.class == [TaskFrame, ProjectFrame, BuildFrame]
        frames*.displayName == ["task :app:test", "project :app", "build :"]
        frames[0].buildTreePath == ":app:test"
        frames[1].buildTreePath == ":app"
        frames[2].buildPath == ":"

        and: "the task takes the locality of its registering code; project and build are the user's"
        frames*.locality == [Locality.EXTERNAL, Locality.LOCAL, Locality.LOCAL]
    }

    def "task in an included build also yields the root build"() {
        when:
        def frames = DetectionTraces.detectedIn(task(":lib:core:compileJava", ":lib:core", ":lib")).frames

        then:
        frames*.class == [TaskFrame, ProjectFrame, BuildFrame, BuildFrame]
        frames*.displayName == ["task :lib:core:compileJava", "project :lib:core", "build :lib", "build :"]
    }

    def "while a project is configured the trace holds the project and its builds"() {
        when:
        def frames = DetectionTraces.detectedIn(new DetectionContext(null, new ProjectIdentity(":lib:core", ":lib"), null)).frames

        then:
        frames*.class == [ProjectFrame, BuildFrame, BuildFrame]
        frames*.displayName == ["project :lib:core", "build :lib", "build :"]
    }

    def "a plugin being applied to a project is the leaf"() {
        when:
        def frames = DetectionTraces.detectedIn(new DetectionContext(null, new ProjectIdentity(":app", ":"), plugin("com.acme.foo", Locality.LOCAL))).frames

        then:
        frames*.class == [PluginApplicationFrame, ProjectFrame, BuildFrame]
        frames*.displayName == ["plugin application com.acme.foo", "project :app", "build :"]
        frames[0].pluginId == "com.acme.foo"
        frames*.locality == [Locality.LOCAL, Locality.LOCAL, Locality.LOCAL]
    }

    def "a plugin being applied outside a project sits on the root build"() {
        when:
        def frames = DetectionTraces.detectedIn(new DetectionContext(null, null, plugin("com.acme.settings", Locality.EXTERNAL))).frames

        then:
        frames*.displayName == ["plugin application com.acme.settings", "build :"]
        frames*.locality == [Locality.EXTERNAL, Locality.LOCAL]
    }

    def "while a task runs the plugin being applied is not part of the trace"() {
        when:
        def frames = DetectionTraces.detectedIn(new DetectionContext(new TaskIdentity(":app:test", ":app", ":", "com.acme.AcmeTask", null), new ProjectIdentity(":app", ":"), plugin("com.acme.foo", Locality.LOCAL))).frames

        then:
        frames*.displayName == ["task :app:test", "project :app", "build :"]
        frames[0].locality == Locality.UNKNOWN
    }

    def "a task registered by a plugin takes the plugin's locality"() {
        when:
        def frames = DetectionTraces.detectedIn(new DetectionContext(new TaskIdentity(":app:test", ":app", ":", "com.acme.AcmeTask", plugin("org.gradle.java", Locality.CORE)), null, null)).frames

        then:
        frames[0].locality == Locality.CORE
    }

    def "without a task the trace holds the root build alone"() {
        when:
        def frames = DetectionTraces.detectedIn(DetectionContext.NONE).frames

        then:
        frames*.class == [BuildFrame]
        frames*.displayName == ["build :"]
    }

    private static DetectionContext task(String taskPath, String projectPath, String buildPath) {
        new DetectionContext(new TaskIdentity(taskPath, projectPath, buildPath, "com.acme.AcmeTask", plugin("com.acme.foo", Locality.EXTERNAL)), null, null)
    }

    private static UserCodeSource.Binary plugin(String id, Locality locality) {
        new UserCodeSource.Binary(Describables.of("plugin '$id'"), "com.acme.FooPlugin", id, locality)
    }
}
