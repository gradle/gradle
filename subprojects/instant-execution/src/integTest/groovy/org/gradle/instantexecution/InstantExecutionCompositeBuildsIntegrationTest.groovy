/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution


class InstantExecutionCompositeBuildsIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "warns for not implemented included builds support"() {

        given:
        def instantExecution = newInstantExecutionFixture()
        settingsFile << """includeBuild("included")"""
        file("included/settings.gradle") << ""

        when:
        instantRun("help")

        then:
        instantExecution.assertStateStored()
        output.contains("Gradle runtime: support for included builds is not yet implemented with instant execution.")

        when:
        instantRun("help")

        then:
        instantExecution.assertStateLoaded()
    }
}
