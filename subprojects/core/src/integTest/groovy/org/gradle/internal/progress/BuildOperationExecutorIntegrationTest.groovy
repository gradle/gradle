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

    def "can be used at configuration time"() {
        given:
        settingsFile << 'rootProject.name = "root"'
        buildFile << '''
            plugins { id 'java' }
            repositories { jcenter() }
            dependencies { compile 'org.slf4j:slf4j-api:1.7.25' }
            configurations.compile.files // Triggers dependency resolution that uses it
        '''.stripIndent()

        when:
        succeeds 'help'

        then:
        buildOperations.operation('Resolve dependencies of :compile').parentId == buildOperations.operation("Apply script build.gradle to root project 'root'").id
    }
}
