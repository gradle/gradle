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

package org.gradle.api.tasks

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.operations.trace.BuildOperationTrace
import spock.lang.Issue

class GradleBuildBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    @NotYetImplemented
    @Issue("https://github.com/gradle/gradle/issues/3983")
    def "can run a build with operation trace enabled"() {
        given:
        buildFile << """
            task otherBuild(type: GradleBuild) {
                dir = 'other'
                startParameter.searchUpwards = false
            }
        """

        expect:
        succeeds 'otherBuild', "-D${BuildOperationTrace.SYSPROP}=build.log"
    }
}
