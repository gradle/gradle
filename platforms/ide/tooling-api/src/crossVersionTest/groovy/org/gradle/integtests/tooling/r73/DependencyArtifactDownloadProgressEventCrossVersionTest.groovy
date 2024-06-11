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
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

@ToolingApiVersion(">=7.3")
@TargetGradleVersion(">=7.3")
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class DependencyArtifactDownloadProgressEventCrossVersionTest extends AbstractHttpCrossVersionSpec {

    def "generates typed events for downloads during dependency resolution"() {
        def modules = setupBuildWithArtifactDownloadDuringConfiguration()
        modules.useLargeJars()

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
        events.trees == events.operations
        events.operation("Download ${modules.projectB.pom.uri}").assertIsDownload(modules.projectB.pom)

        def downloadB = events.operation("Download ${modules.projectB.artifact.uri}")
        downloadB.assertIsDownload(modules.projectB.artifact)
        !downloadB.statusEvents.empty
        downloadB.statusEvents.each {
            assert it.event.displayName == "Download ${modules.projectB.artifact.uri} ${it.event.progress}/${it.event.total} ${it.event.unit} completed"
            assert it.event.progress > 0 && it.event.progress <= modules.projectB.artifact.file.length()
            assert it.event.total == modules.projectB.artifact.file.length()
        }

        events.operation("Download ${modules.projectC.rootMetaData.uri}").assertIsDownload(modules.projectC.rootMetaData)
        events.operation("Download ${modules.projectC.pom.uri}").assertIsDownload(modules.projectC.pom)
        events.operation("Download ${modules.projectC.artifact.uri}").assertIsDownload(modules.projectC.artifact)
        events.operation("Download ${modules.projectD.pom.uri}").assertIsDownload(modules.projectD.pom)
        events.operation("Download ${modules.projectD.metaData.uri}").assertIsDownload(modules.projectD.metaData.uri, modules.projectD.metaDataFile.length())
        events.operation("Download ${modules.projectD.artifact.uri}").assertIsDownload(modules.projectD.artifact)
    }

    @TargetGradleVersion('>=7.3')
    def "generates success event for failing first attempt to get dependency"() {
        toolingApi.requireIsolatedUserHome()

        def projectFModuleMissing = mavenHttpRepo.module('group', 'projectF', '2.0')
        projectFModuleMissing.missing()
        def projectF2 = getMavenHttpRepo("/repo2").module('group', 'projectF', '2.0').publish()
        projectF2.pom.expectGet()
        projectF2.artifact.expectGet()

        initSettingsFile()

        buildFile << """
            allprojects {
                apply plugin:'java-library'
            }
            ${repositories(mavenHttpRepo, getMavenHttpRepo("/repo2"))}
            dependencies {
                implementation "group:projectF:2.0"
            }
        """
        addConfigurationClassPathPrintToBuildFile()

        when:
        def events = ProgressEvents.create()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            collectOutputs(build)
            build.addProgressListener(events, OperationType.FILE_DOWNLOAD)
                .run()
        }

        then:
        events.operations.size() == 3
        events.trees == events.operations
        events.operations.every { it.successful }
        events.operation("Download ${projectFModuleMissing.pom.uri}").assertIsDownload(projectFModuleMissing.pom)
        events.operation("Download ${projectF2.pom.uri}").assertIsDownload(projectF2.pom)
        events.operation("Download ${projectF2.artifact.uri}").assertIsDownload(projectF2.artifact)
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
        events.trees == events.operations
        events.operation("Download ${modules.projectC.rootMetaData.uri}").assertIsDownload(modules.projectC.rootMetaData)
        def brokenDownloads = events.operations("Download ${modules.projectC.pom.uri}")
        brokenDownloads.each {
            assert it.failed
            it.assertIsDownload(modules.projectC.pom.uri, 0)
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
        events.trees.size() == 1
        def configureRoot = events.operation("Configure project :")
        configureRoot.parent == null
        configureRoot.child("Configure project :a")
        configureRoot.child("Download ${modules.projectB.pom.uri}").assertIsDownload(modules.projectB.pom)
        configureRoot.child("Download ${modules.projectB.artifact.uri}").assertIsDownload(modules.projectB.artifact)
        configureRoot.child("Download ${modules.projectC.rootMetaData.uri}").assertIsDownload(modules.projectC.rootMetaData)
        configureRoot.child("Download ${modules.projectC.pom.uri}").assertIsDownload(modules.projectC.pom)
        configureRoot.child("Download ${modules.projectC.artifact.uri}").assertIsDownload(modules.projectC.artifact)
        configureRoot.child("Download ${modules.projectD.pom.uri}").assertIsDownload(modules.projectD.pom)
        configureRoot.child("Download ${modules.projectD.metaData.uri}").assertIsDownload(modules.projectD.metaData.uri, modules.projectD.metaDataFile.length())
        configureRoot.child("Download ${modules.projectD.artifact.uri}").assertIsDownload(modules.projectD.artifact)
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
        events.trees.size() == 2
        events.operation("Task :a:compileJava")
        def task = events.operation("Task :resolve")
        task.child("Download ${modules.projectB.pom.uri}").assertIsDownload(modules.projectB.pom)
        task.child("Download ${modules.projectB.artifact.uri}").assertIsDownload(modules.projectB.artifact)
        task.child("Download ${modules.projectC.rootMetaData.uri}").assertIsDownload(modules.projectC.rootMetaData)
        task.child("Download ${modules.projectC.pom.uri}").assertIsDownload(modules.projectC.pom)
        task.child("Download ${modules.projectC.artifact.uri}").assertIsDownload(modules.projectC.artifact)
        task.child("Download ${modules.projectD.pom.uri}").assertIsDownload(modules.projectD.pom)
        task.child("Download ${modules.projectD.metaData.uri}").assertIsDownload(modules.projectD.metaData.uri, modules.projectD.metaDataFile.length())
        task.child("Download ${modules.projectD.artifact.uri}").assertIsDownload(modules.projectD.artifact)
    }

    def "does not generate events when file download type is not requested"() {
        setupBuildWithArtifactDownloadDuringTaskExecution()

        when:
        def events = ProgressEvents.create()
        withConnection { ProjectConnection connection ->
            def build = connection.newBuild()
            collectOutputs(build)
            build.forTasks("resolve")
            build.addProgressListener(events, EnumSet.complementOf(EnumSet.of(OperationType.FILE_DOWNLOAD)))
                .run()
        }

        then:
        !events.operations.any { it.download }
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

    @ToolingApiVersion(">=7.0 <7.3")
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
