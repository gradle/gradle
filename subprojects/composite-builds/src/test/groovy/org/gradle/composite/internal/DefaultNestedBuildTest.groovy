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
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.initialization.GradleLauncher
import org.gradle.initialization.NestedBuildFactory
import org.gradle.internal.invocation.BuildController
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.test.fixtures.work.TestWorkerLeaseService
import spock.lang.Specification

class DefaultNestedBuildTest extends Specification {
    def factory = Mock(NestedBuildFactory)
    def launcher = Mock(GradleLauncher)
    def gradle = Mock(GradleInternal)
    def action = Mock(Transformer)
    def sessionServices = Mock(ServiceRegistry)
    def buildDefinition = Mock(BuildDefinition)
    DefaultNestedBuild build

    def setup() {
        _ * buildDefinition.name >> "nested"
        _ * sessionServices.get(BuildOperationExecutor) >> Stub(BuildOperationExecutor)
        _ * sessionServices.get(WorkerLeaseService) >> new TestWorkerLeaseService()
        _ * launcher.gradle >> gradle
        _ * gradle.services >> sessionServices

        build = new DefaultNestedBuild(buildDefinition, factory, Stub(BuildStateListener))
    }

    def "has identifier"() {
        expect:
        build.buildIdentifier == new DefaultBuildIdentifier("nested")
    }

    def "creates launcher and runs action after notifying listeners"() {
        when:
        def result = build.run(action)

        then:
        result == '<result>'

        and:
        1 * factory.nestedInstance(buildDefinition, build.buildIdentifier) >> launcher

        then:
        1 * action.transform(!null) >> { BuildController controller ->
            '<result>'
        }

        then:
        1 * launcher.stop()
    }

    def "can have null result"() {
        when:
        def result = build.run(action)

        then:
        result == null

        and:
        1 * factory.nestedInstance(buildDefinition, build.buildIdentifier) >> launcher
        1 * action.transform(!null) >> { BuildController controller ->
            return null
        }
        1 * launcher.stop()
    }
}
