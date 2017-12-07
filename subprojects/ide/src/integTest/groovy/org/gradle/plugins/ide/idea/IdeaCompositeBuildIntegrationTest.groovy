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

package org.gradle.plugins.ide.idea

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.plugins.ide.fixtures.IdeaFixtures.*

class IdeaCompositeBuildIntegrationTest extends AbstractIntegrationSpec {
    def "handle composite build"() {
        given:
        settingsFile << """
            include 'api'
            include 'shared:api', 'shared:model'
            includeBuild 'util'
            rootProject.name = 'root'
        """

        buildFile << """
            allprojects {
                apply plugin: 'java'
                apply plugin: 'idea'
            }

            project(':api') {
                dependencies {
                    compile project(':shared:api')
                    testCompile project(':shared:model')
                }
            }

            project(':shared:model') {
                dependencies {
                    testCompile "test:util:1.3"
                }
            }
        """
        buildTestFixture.withBuildInSubDir()
        singleProjectBuild("util") {
            settingsFile << "rootProject.name = '${rootProjectName}'"
            buildFile << """
                apply plugin: 'java'
                apply plugin: 'idea'
                group = 'test'
                version = '1.3'
            """
        }

        when:
        succeeds 'idea'

        then:
        def apiDependencies = parseIml(file('api/root-api.iml')).dependencies
        apiDependencies.modules.size() == 2
        apiDependencies.assertHasModule('COMPILE', 'shared-api')
        apiDependencies.assertHasModule('TEST', 'model')

        def modelDependencies = parseIml(file('shared/model/model.iml')).dependencies
        modelDependencies.modules.size() == 1
        modelDependencies.assertHasModule('TEST', 'util')

        def ipr = parseIpr(file('root.ipr'))
        ipr.modules.assertHasModules('$PROJECT_DIR$/root.iml', '$PROJECT_DIR$/api/root-api.iml', '$PROJECT_DIR$/shared/shared.iml',
            '$PROJECT_DIR$/shared/api/shared-api.iml', '$PROJECT_DIR$/shared/model/model.iml', '$PROJECT_DIR$/util/util.iml')
    }
}
