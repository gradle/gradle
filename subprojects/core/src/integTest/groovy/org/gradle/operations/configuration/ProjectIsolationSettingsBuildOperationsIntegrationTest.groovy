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

package org.gradle.operations.configuration

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.configurationcache.options.ConfigurationCacheSettingsFinalizedProgressDetails

class ProjectIsolationSettingsBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits settings finalized event when not configured"() {
        given:
        file("buildSrc/src/main/java/Thing.java") << "class Thing {}"

        when:
        succeeds("help")

        then:
        projectIsolationEvents().enabled == [false]
        configurationCacheEvents().enabled == [GradleContextualExecuter.configCache]
    }

    def "emits settings finalized event when explicitly #status"() {
        given:
        file("buildSrc/src/main/java/Thing.java") << "class Thing {}"

        when:
        succeeds("help", "-Dorg.gradle.unsafe.isolated-projects=$enabled")
        then:
        projectIsolationEvents().enabled == [enabled]
        configurationCacheEvents().enabled == [GradleContextualExecuter.configCache || enabled]

        // Ensure events are produced on CC hit as well
        when:
        succeeds("help", "-Dorg.gradle.unsafe.isolated-projects=$enabled")
        then:
        projectIsolationEvents().enabled == [enabled]
        configurationCacheEvents().enabled == [GradleContextualExecuter.configCache || enabled]

        where:
        status     | enabled
        "enabled"  | true
        "disabled" | false
    }

    List<ProjectIsolationSettingsFinalizedProgressDetails> projectIsolationEvents() {
        return operations.progress(ProjectIsolationSettingsFinalizedProgressDetails).details
    }

    List<ConfigurationCacheSettingsFinalizedProgressDetails> configurationCacheEvents() {
        return operations.progress(ConfigurationCacheSettingsFinalizedProgressDetails).details
    }

}
