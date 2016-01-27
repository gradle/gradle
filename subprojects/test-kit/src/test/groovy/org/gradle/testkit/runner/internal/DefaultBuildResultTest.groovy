/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.internal

import org.gradle.testkit.runner.BuildTask
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.*

class DefaultBuildResultTest extends Specification {
    BuildTask successBuildResult = new DefaultBuildTask(':a', SUCCESS)
    BuildTask failedBuildResult = new DefaultBuildTask(':b', FAILED)
    BuildTask skippedBuildResult = new DefaultBuildTask(':c', SKIPPED)
    def buildTasks = [successBuildResult, failedBuildResult]
    DefaultBuildResult defaultBuildResult = new DefaultBuildResult('output', buildTasks)

    def "provides expected field values"() {
        expect:
        defaultBuildResult.output == 'output'
        defaultBuildResult.tasks == buildTasks
        defaultBuildResult.tasks(SUCCESS) == [successBuildResult]
        defaultBuildResult.tasks(FAILED) == [failedBuildResult]
        defaultBuildResult.taskPaths(SUCCESS) == [successBuildResult.path]
        defaultBuildResult.taskPaths(FAILED) == [failedBuildResult.path]
    }

    def "returned tasks are unmodifiable"() {
        when:
        defaultBuildResult.tasks << skippedBuildResult

        then:
        thrown(UnsupportedOperationException)

        when:
        defaultBuildResult.tasks(SUCCESS) << skippedBuildResult

        then:
        thrown(UnsupportedOperationException)

        when:
        defaultBuildResult.taskPaths(SUCCESS) << skippedBuildResult

        then:
        thrown(UnsupportedOperationException)
    }

}
