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

package org.gradle.composite.internal

import org.gradle.api.Transformer
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ForeignBuildIdentifier
import org.gradle.internal.build.BuildModelControllerServices
import org.gradle.internal.build.BuildLifecycleControllerFactory
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildState
import org.gradle.internal.buildtree.BuildTreeController
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.util.Path
import spock.lang.Specification

class DefaultIncludedBuildTest extends Specification {
    def owningBuild = Mock(BuildState)
    def buildFactory = Mock(BuildLifecycleControllerFactory)
    def buildDefinition = Stub(BuildDefinition)
    def gradleLauncher = Mock(BuildLifecycleController)
    def gradle = Mock(GradleInternal)
    def buildTree = Mock(BuildTreeController)
    DefaultIncludedBuild build

    def setup() {
        _ * owningBuild.nestedBuildFactory >> buildFactory
        _ * buildFactory.newInstance(_, _, _, _) >> gradleLauncher
        _ * gradleLauncher.gradle >> gradle
        _ * gradle.settings >> Stub(SettingsInternal)
        _ * buildTree.services >> new DefaultServiceRegistry()

        build = new DefaultIncludedBuild(Stub(BuildIdentifier), Path.path(":a:b:c"), buildDefinition, false, owningBuild, buildTree, Stub(WorkerLeaseRegistry.WorkerLease), buildFactory, Stub(BuildModelControllerServices))
    }

    def "creates a foreign id for projects"() {
        def projectId = new DefaultProjectComponentIdentifier(Stub(BuildIdentifier), Path.path("id"), Path.path("project"), "name")

        expect:
        def id = build.idToReferenceProjectFromAnotherBuild(projectId)
        id.build instanceof ForeignBuildIdentifier
        id.identityPath == projectId.identityPath
        id.projectPath == projectId.projectPath
        id.projectName == projectId.projectName
    }

    def "can run action against build state"() {
        def action = Mock(Transformer)

        when:
        def result = build.withState(action)

        then:
        result == "result"
        1 * action.transform(gradle) >> "result"
        0 * action._
    }
}
