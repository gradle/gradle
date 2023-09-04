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

package org.gradle.plugins.ide.idea

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

import static org.gradle.plugins.ide.fixtures.IdeaFixtures.parseIml
import static org.gradle.plugins.ide.fixtures.IdeaFixtures.parseIpr

class IdeMultiProjectBuildIntegrationTest extends AbstractIntegrationSpec {
    @ToBeFixedForConfigurationCache
    def "includes module for each project in build"() {
        given:
        settingsFile << """
            include 'api'
            include 'shared:api', 'shared:model'
            rootProject.name = 'root'
        """

        buildFile << """
            allprojects {
                apply plugin: 'java'
                apply plugin: 'idea'
            }

            project(':api') {
                dependencies {
                    implementation project(':shared:api')
                    testImplementation project(':shared:model')
                }
            }
        """

        when:
        succeeds ":idea"

        then:
        result.assertTasksExecuted(":ideaModule", ":ideaProject", ":ideaWorkspace",
            ":api:ideaModule",
            ":shared:ideaModule",
            ":shared:api:ideaModule",
            ":shared:model:ideaModule",
            ":idea")

        def ipr = parseIpr(file('root.ipr'))
        ipr.modules.assertHasModules(
            '$PROJECT_DIR$/root.iml',
            '$PROJECT_DIR$/api/root-api.iml',
            '$PROJECT_DIR$/shared/shared.iml',
            '$PROJECT_DIR$/shared/api/shared-api.iml',
            '$PROJECT_DIR$/shared/model/model.iml')

        def apiDependencies = parseIml(file('api/root-api.iml')).dependencies
        apiDependencies.modules.size() == 2
        apiDependencies.assertHasModule('COMPILE', 'shared-api')
        apiDependencies.assertHasModule('TEST', 'model')

        parseIml(file('root.iml'))
        parseIml(file('shared/shared.iml'))
        parseIml(file('shared/api/shared-api.iml'))
        parseIml(file('shared/model/model.iml'))
    }
}
