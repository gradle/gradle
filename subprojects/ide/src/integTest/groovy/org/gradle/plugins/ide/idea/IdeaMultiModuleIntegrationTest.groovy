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
package org.gradle.plugins.ide.idea

import org.gradle.integtests.fixtures.TestResources
import org.gradle.plugins.ide.AbstractIdeIntegrationTest
import org.junit.Rule
import org.junit.Test

class IdeaMultiModuleIntegrationTest extends AbstractIdeIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    @Test
    void buildsCorrectModuleDependencies() {
        def settingsFile = file("master/settings.gradle")
        settingsFile << """
include 'api'
include 'shared:api', 'shared:model'
include 'util'
        """

        def buildFile = file("master/build.gradle")
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
    configurations {
        utilities { extendsFrom testCompile }
    }
    dependencies {
        utilities project(':util')
    }
    idea {
        module {
            scopes.TEST.plus.add(configurations.utilities)
        }
    }
}
"""

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("ideaModule").run()

        //then
        def dependencies = parseIml("master/api/api.iml").dependencies
        assert dependencies.modules.size() == 2
        dependencies.assertHasModule("COMPILE", "shared-api")
        dependencies.assertHasModule("TEST", "model")

        dependencies = parseIml("master/shared/model/model.iml").dependencies
        assert dependencies.modules.size() == 1
        dependencies.assertHasModule("TEST", "util")
    }

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
            -utilities (renamed by user to 'util'
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
    idea {
        module {
            name = 'very-cool-model'
        }
    }
}

project(':services:utilities') {
    dependencies {
        compile project(':util'), project(':contrib:services:util'), project(':shared:api'), project(':shared:model')
    }
    idea {
        module {
            name = 'util'
        }
    }
}
"""

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("idea").run()

        //then
        assertIprContainsCorrectModules()

        def moduleDeps = parseIml("master/api/api.iml").dependencies
        assert moduleDeps.modules.size() == 2
        moduleDeps.assertHasModule("COMPILE", "shared-api")
        moduleDeps.assertHasModule("COMPILE", "very-cool-model")

        moduleDeps = parseIml("master/services/utilities/services-util.iml").dependencies
        assert moduleDeps.modules.size() == 4
        moduleDeps.assertHasModule("COMPILE", "shared-api")
        moduleDeps.assertHasModule("COMPILE", "very-cool-model")
        moduleDeps.assertHasModule("COMPILE", "util")
        moduleDeps.assertHasModule("COMPILE", "contrib-services-util")
    }

    def assertIprContainsCorrectModules() {
        List moduleFileNames = parseIprModules()

        ['master.iml',
         'shared-api.iml', 'shared.iml',
         'services.iml', 'services-util.iml',
         'contrib-services-util.iml', 'contrib.iml', 'contrib-services.iml',
         'very-cool-model.iml',
         'api.iml',
         'util.iml'].each {
            assert moduleFileNames.contains(it)
        }
    }

    List parseIprModules() {
        def ipr = parseFile(project: 'master', "master.ipr")
        ipr.component.modules.module.@filepath.collect {
            it.text().replaceAll(/.*\//, "")
        }
    }

    @Test
    void allowsFullyReconfiguredModuleNames() {
        //use case from the mailing list
        def settingsFile = file("master/settings.gradle")
        settingsFile << "include 'api', 'shared:model'"

        def buildFile = file("master/build.gradle")
        buildFile << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
}

subprojects {
    ideaModule {
        outputFile = file(project.projectDir.canonicalPath + "/" + rootProject.name + project.path.replace(':', '.') + ".iml")
    }
}

project(':api') {
    dependencies {
        compile project(':shared:model')
    }
}
"""

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("idea").run()

        //then
        def moduleFileNames = parseIprModules()

        assert moduleFileNames.contains("master.shared.model.iml")
        assert moduleFileNames.contains("master.api.iml")
        assert moduleFileNames.contains("master.shared.iml")
        assert moduleFileNames.contains("master.iml")
    }

    @Test
    void cleansCorrectlyWhenModuleNamesAreChangedOrDeduplicated() {
        def settingsFile = file("master/settings.gradle")
        settingsFile << "include 'api', 'shared:api', 'contrib'"

        def buildFile = file("master/build.gradle")
        buildFile << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
}

project(':contrib') {
    idea.module {
        name = 'cool-contrib'
    }
}
"""

        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("idea").run()
        assert getFile(project: 'master/shared/api', "shared-api.iml").exists()
        assert getFile(project: 'master/contrib', "cool-contrib.iml").exists()

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("cleanIdea").run()

        //then
        assert !getFile(project: 'master/shared/api', "shared-api.iml").exists()
        assert !getFile(project: 'master/contrib', "cool-contrib.iml").exists()
    }

    @Test
    void handlesInternalDependenciesToNonIdeaProjects() {
        def settingsFile = file("master/settings.gradle")
        settingsFile << "include 'api', 'nonIdeaProject'"

        def buildFile = file("master/build.gradle")
        buildFile << """
subprojects {
  apply plugin: 'java'
}

project(':api') {
    apply plugin: 'idea'

    dependencies {
        compile project(':nonIdeaProject')
    }
}
"""

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("idea").run()

        //then
        assert getFile(project: 'master/api', 'api.iml').exists()
    }

    @Test
    void doesNotCreateDuplicateEntriesInIpr() {
        def settingsFile = file("master/settings.gradle")
        settingsFile << "include 'api', 'iml'"

        def buildFile = file("master/build.gradle")
        buildFile << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
}
"""

        //when
        2.times { executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("ideaProject").run() }

        //then
        String content = getFile(project: 'master', 'master.ipr').text
        assert content.count('filepath="$PROJECT_DIR$/api/api.iml"') == 1
    }

    @Test
    void buildsCorrectModuleDependenciesWithScopes() {
        def settingsFile = file("master/settings.gradle")
        settingsFile << """
include 'api'
include 'impl'
include 'app'
"""

        def buildFile = file("master/build.gradle")
        buildFile << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
}

project(':impl') {
    dependencies {
        compile project(':api')
    }
}

project(':app') {
    dependencies {
        compile project(':api')
        testCompile project(':impl')
        runtime project(':impl')
    }
}
"""

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("ideaModule").run()

        //then
        def dependencies = parseIml("master/app/app.iml").dependencies
        assert dependencies.modules.size() == 3
        dependencies.assertHasInheritedJdk()
        dependencies.assertHasSource('false')
        dependencies.assertHasModule('COMPILE', 'api')
        dependencies.assertHasModule('TEST', 'impl')
        dependencies.assertHasModule('RUNTIME', 'impl')
    }
}
