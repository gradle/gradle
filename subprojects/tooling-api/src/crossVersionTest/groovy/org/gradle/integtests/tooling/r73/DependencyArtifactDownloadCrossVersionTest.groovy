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
import org.gradle.tooling.BuildException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType

@ToolingApiVersion(">=7.3")
@TargetGradleVersion(">=7.3")
class DependencyArtifactDownloadCrossVersionTest extends AbstractHttpCrossVersionSpec {
    def "generates typed events for downloads during dependency resolution"() {
        def modules = setupBuildWithArtifactDownloadDuringConfiguration()

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
        events.operations.each {
            assert it.parent == null
        }
        events.operation("Download ${modules.projectB.pom.uri}").assertIsDownload(modules.projectB.pom.uri)
        events.operation("Download ${modules.projectB.artifact.uri}").assertIsDownload(modules.projectB.artifact.uri)
        events.operation("Download ${modules.projectC.rootMetaData.uri}").assertIsDownload(modules.projectC.rootMetaData.uri)
        events.operation("Download ${modules.projectC.pom.uri}").assertIsDownload(modules.projectC.pom.uri)
        events.operation("Download ${modules.projectC.artifact.uri}").assertIsDownload(modules.projectC.artifact.uri)
        events.operation("Download ${modules.projectD.pom.uri}").assertIsDownload(modules.projectD.pom.uri)
        events.operation("Download ${modules.projectD.metaData.uri}").assertIsDownload(modules.projectD.metaData.uri)
        events.operation("Download ${modules.projectD.artifact.uri}").assertIsDownload(modules.projectD.artifact.uri)
    }

    def "generates typed events for failed downloads during dependency resolution"() {
        def modules = setupBuildWithFailedArtifactDownloadDuringTaskExecution()

        when:
        def events = ProgressEvents.create()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            collectOutputs(build)
            build.forTasks("resolve")
            build.addProgressListener(events, OperationType.FILE_DOWNLOAD)
                .run()
        }

        then:
        thrown(BuildException)

        events.operations.size() >= 4
        events.operations.each {
            assert it.parent == null
        }
        events.operation("Download ${modules.projectC.rootMetaData.uri}").assertIsDownload(modules.projectC.rootMetaData.uri)
        def brokenDownloads = events.operations("Download ${modules.projectC.pom.uri}")
        brokenDownloads.each {
            assert it.failed
            it.assertIsDownload(modules.projectC.pom.uri)
        }
    }

    def "attaches parent to events for downloads that happen during project configuration"() {
        def modules = setupBuildWithArtifactDownloadDuringConfiguration()

        when:
        def events = ProgressEvents.create()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            collectOutputs(build)
            build.addProgressListener(events, OperationType.FILE_DOWNLOAD, OperationType.PROJECT_CONFIGURATION)
                .run()
        }

        then:
        events.operations.size() == 10
        def configureRoot = events.operation("Configure project :")
        configureRoot.parent == null
        configureRoot.child("Configure project :a")
        configureRoot.child("Download ${modules.projectB.pom.uri}").assertIsDownload(modules.projectB.pom.uri)
        configureRoot.child("Download ${modules.projectB.artifact.uri}").assertIsDownload(modules.projectB.artifact.uri)
        configureRoot.child("Download ${modules.projectC.rootMetaData.uri}").assertIsDownload(modules.projectC.rootMetaData.uri)
        configureRoot.child("Download ${modules.projectC.pom.uri}").assertIsDownload(modules.projectC.pom.uri)
        configureRoot.child("Download ${modules.projectC.artifact.uri}").assertIsDownload(modules.projectC.artifact.uri)
        configureRoot.child("Download ${modules.projectD.pom.uri}").assertIsDownload(modules.projectD.pom.uri)
        configureRoot.child("Download ${modules.projectD.metaData.uri}").assertIsDownload(modules.projectD.metaData.uri)
        configureRoot.child("Download ${modules.projectD.artifact.uri}").assertIsDownload(modules.projectD.artifact.uri)
    }

    def "attaches parent to events for downloads that happen during task execution"() {
        def modules = setupBuildWithArtifactDownloadDuringTaskExecution()

        when:
        def events = ProgressEvents.create()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            collectOutputs(build)
            build.forTasks("resolve")
            build.addProgressListener(events, OperationType.FILE_DOWNLOAD, OperationType.TASK)
                .run()
        }

        then:
        events.operations.size() == 10
        events.operation("Task :a:compileJava")
        def task = events.operation("Task :resolve")
        task.child("Download ${modules.projectB.pom.uri}").assertIsDownload(modules.projectB.pom.uri)
        task.child("Download ${modules.projectB.artifact.uri}").assertIsDownload(modules.projectB.artifact.uri)
        task.child("Download ${modules.projectC.rootMetaData.uri}").assertIsDownload(modules.projectC.rootMetaData.uri)
        task.child("Download ${modules.projectC.pom.uri}").assertIsDownload(modules.projectC.pom.uri)
        task.child("Download ${modules.projectC.artifact.uri}").assertIsDownload(modules.projectC.artifact.uri)
        task.child("Download ${modules.projectD.pom.uri}").assertIsDownload(modules.projectD.pom.uri)
        task.child("Download ${modules.projectD.metaData.uri}").assertIsDownload(modules.projectD.metaData.uri)
        task.child("Download ${modules.projectD.artifact.uri}").assertIsDownload(modules.projectD.artifact.uri)
    }

    @TargetGradleVersion(">=3.5 <7.3")
    def "older versions do not generate typed events for downloads during dependency resolution"() {
        setupBuildWithArtifactDownloadDuringConfiguration()

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
        def modules = setupBuildWithArtifactDownloadDuringConfiguration()

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
        def modules = setupBuildWithArtifactDownloadDuringConfiguration()

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
