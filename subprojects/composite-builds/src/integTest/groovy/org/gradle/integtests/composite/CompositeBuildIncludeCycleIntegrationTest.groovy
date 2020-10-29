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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.build.BuildTestFile

class CompositeBuildIncludeCycleIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    BuildTestFile buildB
    BuildTestFile buildC

    def setup() {
        buildB = singleProjectBuild("buildB")
        includedBuilds << buildB
        buildC = singleProjectBuild("buildC")
        includedBuilds << buildC
    }

    @ToBeFixedForConfigurationCache(because = "configuration cache serialization issue")
    def "two included builds can include each other"() {
        when:
        buildB.settingsFile << "includeBuild '../buildC'"
        buildC.settingsFile << "includeBuild '../buildB'"

        then:
        execute(buildA, 'help')
    }

    def "included build can include root build"() {
        when:
        buildB.settingsFile << "includeBuild '../buildA'"
        buildC.settingsFile << "includeBuild '../buildA'"

        then:
        execute(buildA, 'help')
    }

    def "nested build can include root build"() {
        when:
        buildB.settingsFile << "includeBuild '../buildC'"
        buildC.settingsFile << "includeBuild '../buildA'"

        then:
        execute(buildA, 'help')
    }
}
