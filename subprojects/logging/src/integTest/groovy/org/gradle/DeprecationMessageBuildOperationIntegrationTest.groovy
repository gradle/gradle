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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.featurelifecycle.buildscan.DeprecationWarningBuildOperationType

class DeprecationMessageBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits operation for deprecation warnings"() {
        when:
        buildScript """
            task t {
                doLast {
                    org.gradle.util.DeprecationLogger.nagUserOfDeprecated('test deprecation warning');
                }
            }
        """
        and:
        executer.noDeprecationChecks()
        succeeds "t"

        then:
        def op = operations.first(DeprecationWarningBuildOperationType) { it.details.message.contains('test deprecation warning') }
        op.details.stackTrace.size > 0
        op.details.stackTrace[0].fileName.endsWith('build.gradle')
        op.details.stackTrace[0].lineNumber == 4
    }

}
