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

package org.gradle.initialization

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.build.BuildProjectRegistry
import org.gradle.internal.build.BuildState
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.ProgressDisplayName
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.util.Path
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/gradle/gradle/issues/39539")
class NotifyingBuildLoaderTest extends Specification {

    def buildOperationRunner = new TestBuildOperationRunner()
    def loader = new NotifyingBuildLoader(Stub(BuildLoader), buildOperationRunner, Stub(BuildOperationProgressEventEmitter))

    def "shows the projects loaded listeners of the #build on a worker line"() {
        when:
        loader.load(Stub(SettingsInternal), gradle(buildPath, ownerToShow))

        then:
        def notify = buildOperationRunner.log.mostRecent(NotifyProjectsLoadedBuildOperationType)
        notify.descriptor.displayName == displayName
        notify.descriptor.progressDisplayName == ProgressDisplayName.of(ownerToShow, "Running projects loaded listeners")

        where:
        build            | buildPath         | ownerToShow | displayName
        "root build"     | Path.ROOT         | null        | "Notify projectsLoaded listeners"
        "included build" | Path.path(":lib") | ":lib"      | "Notify projectsLoaded listeners (:lib)"
    }

    @Issue("https://github.com/gradle/gradle/issues/39634")
    def "shows the loading of the projects of the #build on a worker line"() {
        when:
        loader.load(Stub(SettingsInternal), gradle(buildPath, ownerToShow))

        then:
        def load = buildOperationRunner.log.mostRecent(LoadProjectsBuildOperationType)
        load.descriptor.displayName == displayName
        load.descriptor.progressDisplayName == ProgressDisplayName.of(ownerToShow, "Loading projects")

        where:
        build            | buildPath         | ownerToShow | displayName
        "root build"     | Path.ROOT         | null        | "Load projects"
        "included build" | Path.path(":lib") | ":lib"      | "Load projects (:lib)"
    }

    private GradleInternal gradle(Path buildPath, String ownerToShow) {
        def rootProject = Stub(ProjectState) {
            getIdentity() >> ProjectIdentity.forRootProject(buildPath, "root")
            getProjectDir() >> new File("root")
            getMutableModel() >> Stub(ProjectInternal) {
                getBuildFile() >> new File("root/build.gradle")
            }
        }
        Stub(GradleInternal) {
            getIdentityPath() >> buildPath
            getOwner() >> Stub(BuildState) {
                getOwnerToShow() >> ownerToShow
                getProjects() >> Stub(BuildProjectRegistry) {
                    getRootProject() >> rootProject
                }
            }
        }
    }
}
