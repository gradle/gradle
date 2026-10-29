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
import org.gradle.internal.build.BuildState
import org.gradle.internal.operations.ProgressDisplayName
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.util.Path
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/gradle/gradle/issues/39539")
class ProjectsEvaluatedNotifierTest extends Specification {

    def buildOperationRunner = new TestBuildOperationRunner()
    def notifier = new ProjectsEvaluatedNotifier(buildOperationRunner)

    def "shows the projects evaluated listeners of the #build on a worker line"() {
        given:
        def gradle = Stub(GradleInternal) {
            getIdentityPath() >> buildPath
            getOwner() >> Stub(BuildState) {
                getOwnerToShow() >> ownerToShow
            }
        }

        when:
        notifier.notify(gradle)

        then:
        def notify = buildOperationRunner.log.mostRecent(NotifyProjectsEvaluatedBuildOperationType)
        notify.descriptor.displayName == displayName
        notify.descriptor.progressDisplayName == ProgressDisplayName.of(ownerToShow, "Running projects evaluated listeners")

        where:
        build            | buildPath         | ownerToShow | displayName
        "root build"     | Path.ROOT         | null        | "Notify projectsEvaluated listeners"
        "included build" | Path.path(":lib") | ":lib"      | "Notify projectsEvaluated listeners (:lib)"
    }
}
