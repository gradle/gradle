/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.tooling.r73

import org.gradle.integtests.tooling.fixture.AbstractHttpCrossVersionSpec
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType

@ToolingApiVersion(">=7.3")
@TargetGradleVersion(">=7.3")
class DependencyArtifactDownloadCrossVersionTest extends AbstractHttpCrossVersionSpec {
    def "generates typed events for downloads during dependency resolution"() {
        def modules = setupBuildWithArtifactDownload()

        when:
        def events = ProgressEvents.create()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            collectOutputs(build)
            build.addProgressListener(events, OperationType.FILE_DOWNLOAD)
                .run()
        }

        then:
        events.operations.size() == 8
        events.operation("Download ${modules.projectB.pom.uri}")
        events.operation("Download ${modules.projectB.artifact.uri}")
    }

    @TargetGradleVersion(">=3.5 <7.3")
    def "older versions do not generate typed events for downloads during dependency resolution"() {
        setupBuildWithArtifactDownload()

        when:
        def events = ProgressEvents.create()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            collectOutputs(build)
            build.addProgressListener(events, OperationType.FILE_DOWNLOAD)
                .run()
        }

        then:
        events.operations.empty
    }

    @TargetGradleVersion(">=3.5 <7.3")
    def "older versions generate generic events for downloads during dependency resolution"() {
        def modules = setupBuildWithArtifactDownload()

        when:
        def events = ProgressEvents.create()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            collectOutputs(build)
            build.addProgressListener(events)
                .run()
        }

        then:
        events.operation("Download ${modules.projectB.pom.uri}")
        events.operation("Download ${modules.projectB.artifact.uri}")
    }

    @ToolingApiVersion(">=3.5 <7.3")
    def "generates generic events for older tooling api clients"() {
        def modules = setupBuildWithArtifactDownload()

        when:
        def events = ProgressEvents.create()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            collectOutputs(build)
            build.addProgressListener(events, OperationType.GENERIC)
                .run()
        }

        then:
        events.operation("Download ${modules.projectB.pom.uri}")
        events.operation("Download ${modules.projectB.artifact.uri}")
    }
}
