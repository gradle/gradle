/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.junit.Test

/**
 * @author Szczepan Faber, @date 03.03.11
 */
class IdeaMultiModuleIntegrationTest extends AbstractIdeIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources()

    @Test
    void dealsWithDuplicatedModuleNames() {
      /*
      This is the multi-module project structure the integration test works with:
      -root
        -api
        -shared
          -api
          -model
        -services
          -util
        -util
        -contrib
          -services
            -util, soon (overwritten by user to 'util' using outputFile property)
      */

        def settingsFile = file("master/settings.gradle")
        settingsFile << """
include 'api'
include 'shared:api', 'shared:model'
include 'services:utilities'
include 'util'
include 'contrib:services:util'
        """

        def buildFile = file("master/build.gradle")
        buildFile << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
}

project(':api') {
    dependencies {
        compile project(':shared:api'), project(':shared:model')
    }
}

project(':shared:model') {
    ideaModule {
        moduleName = 'very-cool-model'
    }
}

project(':services:utilities') {
    dependencies {
        compile project(':util'), project(':contrib:services:util'), project(':shared:api'), project(':shared:model')
    }
    ideaModule {
        moduleName = 'util'
    }
}
"""

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("idea").run()
        println(getTestDir())
        //then
        assertIprContainsCorrectModules()
        assertApiModuleContainsCorrectDependencies()
        assertServicesUtilModuleContainsCorrectDependencies()
    }

    def assertServicesUtilModuleContainsCorrectDependencies() {
        def iml = parseFile(project: 'master/services/utilities', "services-util.iml")
        def moduleDeps = iml.component.orderEntry.'@module-name'.collect{ it.text() }

        assert moduleDeps.contains("very-cool-model")
        assert moduleDeps.contains("util")
        assert moduleDeps.contains("shared-api")
        assert moduleDeps.contains("contrib-services-util")
    }

    def assertApiModuleContainsCorrectDependencies() {
        def iml = parseFile(project: 'master/api', "api.iml")
        def moduleDeps = iml.component.orderEntry.'@module-name'.collect{ it.text() }

        assert moduleDeps.contains("very-cool-model")
        assert moduleDeps.contains("shared-api")
    }

    def assertIprContainsCorrectModules() {
        def ipr = parseFile(project: 'master', "master.ipr")
        def moduleFileNames = ipr.component.modules.module.@filepath.collect {
            it -> it.text().replaceAll(/.*\//, "")
        }

        assert moduleFileNames.contains("shared-api.iml")
        assert moduleFileNames.contains("services.iml")
        assert moduleFileNames.contains("contrib-services-util.iml")
        assert moduleFileNames.contains("contrib.iml")
        assert moduleFileNames.contains("contrib-services.iml")
        assert moduleFileNames.contains("very-cool-model.iml")
        assert moduleFileNames.contains("master.iml")
        assert moduleFileNames.contains("services-util.iml")
        assert moduleFileNames.contains("api.iml")
        assert moduleFileNames.contains("shared.iml")
        assert moduleFileNames.contains("util.iml")
    }
}
