/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.progress

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.junit.Rule

class BuildOperationExecutorIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final BuildOperationsFixture buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def "can use as soon as in settingsEvaluated"() {
        given:
        settingsFile << """
            gradle.addListener(new BuildAdapter() {
                @Override
                void settingsEvaluated(Settings settings) {
                    gradle.services.get(${BuildOperationExecutor.name}).run('Anything', {} as Action)
                }
            })
        """.stripIndent()

        when:
        succeeds 'help'

        then:
        buildOperations.hasOperation 'Anything'
    }
}
