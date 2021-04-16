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

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.internal.build.BuildModelControllerServices
import org.gradle.internal.build.BuildLifecycleControllerFactory
import org.gradle.internal.build.BuildLifecycleController
import org.gradle.internal.build.BuildState
import org.gradle.internal.buildtree.BuildTreeController
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.test.fixtures.work.TestWorkerLeaseService
import org.gradle.util.Path
import spock.lang.Specification

import java.util.function.Function

class DefaultNestedBuildTest extends Specification {
    def owner = Mock(BuildState)
    def tree = Mock(BuildTreeController)
    def factory = Mock(BuildLifecycleControllerFactory)
    def launcher = Mock(BuildLifecycleController)
    def parentGradle = Mock(GradleInternal)
    def gradle = Mock(GradleInternal)
    def action = Mock(Function)
    def sessionServices = Mock(ServiceRegistry)
    def buildDefinition = Mock(BuildDefinition)
    def buildIdentifier = Mock(BuildIdentifier)
    DefaultNestedBuild build

    def setup() {
        _ * owner.currentPrefixForProjectsInChildBuilds >> Path.path(":owner")
        _ * owner.mutableModel >> parentGradle
        _ * factory.newInstance(buildDefinition, _, parentGradle, _) >> launcher
        _ * buildDefinition.name >> "nested"
        _ * sessionServices.get(BuildOperationExecutor) >> Stub(BuildOperationExecutor)
        _ * sessionServices.get(WorkerLeaseService) >> new TestWorkerLeaseService()
        _ * tree.services >> new DefaultServiceRegistry()
        _ * launcher.gradle >> gradle
        _ * gradle.services >> sessionServices

        build = new DefaultNestedBuild(buildIdentifier, Path.path(":a:b:c"), buildDefinition, owner, tree, factory, Stub(BuildModelControllerServices))
    }

    def "stops launcher on stop"() {
        when:
        build.stop()

        then:
        1 * launcher.stop()
    }

    def "runs action and returns result"() {
        when:
        def result = build.run(action)

        then:
        result == '<result>'

        then:
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            '<result>'
        }
    }

    def "can have null result"() {
        when:
        def result = build.run(action)

        then:
        result == null

        and:
        1 * action.apply(!null) >> { BuildTreeLifecycleController controller ->
            return null
        }
    }
}
