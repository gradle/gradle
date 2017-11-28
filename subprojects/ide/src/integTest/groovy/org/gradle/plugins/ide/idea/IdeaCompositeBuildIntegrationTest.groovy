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

import org.gradle.plugins.ide.AbstractIdeIntegrationTest
import org.junit.Test

class IdeaCompositeBuildIntegrationTest extends AbstractIdeIntegrationTest {
    @Test
    void "handle composite build"() {
        def settingsFile = file("settings.gradle")
        settingsFile << """
include 'api'
include 'shared:api', 'shared:model'
includeBuild 'util'
rootProject.name = 'root'
        """

        def buildFile = file("build.gradle")
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
        file('util/settings.gradle') << "rootProject.name = 'util'"
        file('util/build.gradle') << """
apply plugin: 'java'
apply plugin: 'idea'
group = 'test'
version = '1.3'
"""

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("idea").run()

        //then
        def dependencies = parseIml("api/root-api.iml").dependencies
        assert dependencies.modules.size() == 2
        dependencies.assertHasModule('COMPILE', "shared-api")
        dependencies.assertHasModule("TEST", "model")

        dependencies = parseIml("shared/model/model.iml").dependencies
        assert dependencies.modules.size() == 1
        dependencies.assertHasModule("TEST", "util")

        def ipr = parseIpr('root.ipr')
        ipr.modules.assertHasModules('$PROJECT_DIR$/root.iml', '$PROJECT_DIR$/api/root-api.iml', '$PROJECT_DIR$/shared/shared.iml',
            '$PROJECT_DIR$/shared/api/shared-api.iml', '$PROJECT_DIR$/shared/model/model.iml', '$PROJECT_DIR$/util/util.iml')
    }
}
