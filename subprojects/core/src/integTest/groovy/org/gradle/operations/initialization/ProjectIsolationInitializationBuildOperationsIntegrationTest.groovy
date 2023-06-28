/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.operations.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture

class ProjectIsolationInitializationBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits settings finalized event when not configured"() {
        given:
        file("buildSrc/src/main/java/Thing.java") << "class Thing {}"

        when:
        succeeds("help")

        then:
        events().enabled == [false]
    }

    def "emits settings finalized event when explicitly #status"() {
        given:
        file("buildSrc/src/main/java/Thing.java") << "class Thing {}"

        when:
        succeeds("help", "-Dorg.gradle.unsafe.isolated-projects=$enabled")

        then:
        events().enabled == [enabled]

        when:
        succeeds("help", "-Dorg.gradle.unsafe.isolated-projects=$enabled")

        then:
        events().enabled == [enabled]

        where:
        status     | enabled
        "enabled"  | true
        "disabled" | false
    }

    List<ProjectIsolationSettingsFinalizedProgressDetails> events() {
        return operations.progress(ProjectIsolationSettingsFinalizedProgressDetails).details
    }

}
