/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.initialization.NestedBuildFactory
import org.gradle.internal.invocation.BuildController
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationRef
import org.gradle.internal.service.ServiceRegistry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class GradleBuildTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def buildFactory = Mock(NestedBuildFactory)
    def buildController = Mock(BuildController)
    def gradle = Mock(GradleInternal)
    def services = Mock(ServiceRegistry)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def buildOperation = Mock(BuildOperationRef)
    GradleBuild task = TestUtil.create(temporaryFolder).task(GradleBuild, [nestedBuildFactory: buildFactory])

    def setup() {
        _ * buildController.getGradle() >> gradle
        _ * gradle.getServices() >> services
        _ * services.get(BuildOperationExecutor) >> buildOperationExecutor
        _ * buildOperationExecutor.currentOperation >> buildOperation
    }

    void usesCopyOfCurrentBuildsStartParams() {
        def expectedStartParameter = task.project.gradle.startParameter.newBuild()
        expectedStartParameter.currentDir = task.project.projectDir

        expect:
        task.startParameter == expectedStartParameter

        when:
        task.tasks = ['a', 'b']

        then:
        task.tasks == ['a', 'b']
        task.startParameter.taskNames == ['a', 'b']
    }

    void executesBuild() {
        when:
        task.build()

        then:

        1 * buildFactory.nestedBuildController(_, _) >> { BuildDefinition buildDefinition, BuildIdentifier buildIdentifier ->
            assert buildDefinition.startParameter == task.startParameter
            buildController
        }
        1 * buildController.run() >> gradle
        1 * buildController.stop()
    }

    void cleansUpOnBuildFailure() {
        def failure = new RuntimeException()

        when:
        task.build()

        then:
        RuntimeException e = thrown()
        e == failure
        1 * buildFactory.nestedBuildController(_, _) >> buildController
        1 * buildController.run() >> { throw failure }
        1 * buildController.stop()
    }
}
