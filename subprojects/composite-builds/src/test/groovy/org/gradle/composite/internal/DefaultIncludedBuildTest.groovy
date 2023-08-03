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
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildModelControllerServices
import org.gradle.internal.build.BuildState
import org.gradle.internal.buildtree.BuildTreeState
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.util.Path
import spock.lang.Specification

class DefaultIncludedBuildTest extends Specification {
    def owningBuild = Mock(BuildState)
    def buildFactory = Mock(BuildModelControllerServices)
    def buildDefinition = Stub(BuildDefinition)
    def controller = Mock(BuildLifecycleController)
    def gradle = Mock(GradleInternal)
    def buildTree = Mock(BuildTreeState)
    DefaultIncludedBuild build

    def setup() {
        _ * buildFactory.servicesForBuild(buildDefinition, _, owningBuild) >> Mock(BuildModelControllerServices.Supplier)
        _ * owningBuild.nestedBuildFactory >> buildFactory
        _ * buildFactory.newInstance(_, _, _, _) >> controller
        _ * controller.gradle >> gradle
        _ * gradle.settings >> Stub(SettingsInternal)
        def services = new DefaultServiceRegistry()
        services.add(gradle)
        services.add(buildFactory)
        services.add(controller)
        services.add(Stub(DocumentationRegistry))
        services.add(Stub(BuildTreeWorkGraphController))
        _ * buildTree.services >> services

        def buildId = Stub(BuildIdentifier) {
            buildPath >> Path.path(":a:b:c")
        }
        build = new DefaultIncludedBuild(buildId, buildDefinition, false, owningBuild, buildTree, Mock(Instantiator))
    }

    def "creates a foreign id for projects"() {
        def projectId = new DefaultProjectComponentIdentifier(Stub(BuildIdentifier), Path.path("id"), Path.path("project"), "name")

        expect:
        def id = build.idToReferenceProjectFromAnotherBuild(projectId)
        id.identityPath == projectId.identityPath
        id.identityPath.path == projectId.buildTreePath
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
