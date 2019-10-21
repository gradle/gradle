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
        implementation project(':shared:api')
        testImplementation project(':shared:model')
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
        def dependencies = parseIml("master/api/master-api.iml").dependencies
        assert dependencies.modules.size() == 2
        dependencies.assertHasModule('COMPILE', "shared-api")
        dependencies.assertHasModule("TEST", "model")

        dependencies = parseIml("master/shared/model/model.iml").dependencies
        assert dependencies.modules.size() == 1
        dependencies.assertHasModule("TEST", "util")
    }


    @Test
    void buildsCorrectModuleDependenciesForDependencyOnRoot() {
        file("settings.gradle") << """
rootProject.name = 'root-project-1'
include 'api'
        """

        file("build.gradle") << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
}

project(':api') {
    dependencies {
        implementation project(':')
    }
}
"""

        //when
        executer.withTasks("ideaModule").run()

        //then
        def dependencies = parseIml("api/api.iml").dependencies
        assert dependencies.modules.size() == 1
        dependencies.assertHasModule(['COMPILE'], "root-project-1")

        dependencies = parseIml("root-project-1.iml").dependencies
        assert dependencies.modules.size() == 0
    }

    @Test
    void respectsApiOfJavaLibraries() {
        def settingsFile = file("master/settings.gradle")
        settingsFile << """
include 'api'
include 'impl'
include 'library'
include 'application'
        """

        def buildFile = file("master/build.gradle")
        buildFile << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
}

project(":library") {
    apply plugin: 'java-library'
    dependencies {
        api project(":api")
        implementation project(":impl")
    }
}

project(":application") {
    dependencies {
        implementation project(":library")
    }
}
"""

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("ideaModule").run()

        //then
        def dependencies = parseIml("master/library/library.iml").dependencies
        assert dependencies.modules.size() == 2
        dependencies.assertHasModule('COMPILE', "api")
        dependencies.assertHasModule('COMPILE', "impl")

        dependencies = parseIml("master/application/application.iml").dependencies
        assert dependencies.modules.size() == 4

        dependencies.assertHasModule('COMPILE', "library")
        dependencies.assertHasModule('COMPILE', "api")
        dependencies.assertHasModule('RUNTIME', "impl")
        dependencies.assertHasModule('TEST', "impl")
    }

    @Test
    void buildsCorrectModuleDependenciesWhenRootProjectDoesNotApplyIdePlugin() {
        file("settings.gradle") << """
rootProject.name = 'root-project-1'

include 'api'
include 'util'
include 'other'
        """

        file("build.gradle") << """
apply plugin: 'java'

subprojects {
    apply plugin: 'java'
    apply plugin: 'idea'
}

project(':api') {
    dependencies {
        implementation project(':util')
        implementation project(':other')
    }
}

project(':other') {
    idea.module.name = 'other-renamed'
}

project(':util') {
    dependencies {
        testImplementation project(':')
    }
}

"""

        //when
        executer.withTasks("ideaModule").run()

        //then
        def dependencies = parseIml("api/api.iml").dependencies
        assert dependencies.modules.size() == 2
        dependencies.assertHasModule('COMPILE', "util")
        dependencies.assertHasModule('COMPILE', "other-renamed")

        def utilDependencies = parseIml("util/util.iml").dependencies
        assert utilDependencies.modules.size() == 1
        utilDependencies.assertHasModule(['TEST'], "root-project-1")
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
          -utilities (renamed by user to 'util')
        -util
        -contrib
          -services
            -util
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
        implementation project(':shared:api'), project(':shared:model')
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
        implementation project(':util'), project(':contrib:services:util'), project(':shared:api'), project(':shared:model')
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

        def moduleDeps = parseIml("master/api/master-api.iml").dependencies
        assert moduleDeps.modules.size() == 2
        moduleDeps.assertHasModule('COMPILE', "shared-api")
        moduleDeps.assertHasModule('COMPILE', "very-cool-model")

        moduleDeps = parseIml("master/services/utilities/util.iml").dependencies
        assert moduleDeps.modules.size() == 4
        moduleDeps.assertHasModule('COMPILE', "shared-api")
        moduleDeps.assertHasModule('COMPILE', "very-cool-model")
        moduleDeps.assertHasModule('COMPILE', "master-util")
        moduleDeps.assertHasModule('COMPILE', "services-util")
    }

    def assertIprContainsCorrectModules() {
        List moduleFileNames = parseIprModules()

        ['master.iml',
         'shared-api.iml', 'shared.iml',
         'master-services.iml', 'services-util.iml',
         'util.iml', 'contrib.iml', 'contrib-services.iml',
         'very-cool-model.iml',
         'master-api.iml',
         'master-util.iml'].each {
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
        implementation project(':shared:model')
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
    void handlesModuleDependencyCycles() {
        def settingsFile = file("master/settings.gradle")
        settingsFile << """
include 'one'
include 'two'
include 'three'
        """

        def buildFile = file("master/build.gradle")
        buildFile << """
allprojects {
    apply plugin: 'java-library'
    apply plugin: 'idea'
}

project(':one') {
    dependencies {
        api project(':two')
    }
}

project(':two') {
    dependencies {
        api project(':three')
    }
}

project(':three') {
    dependencies {
        api project(':one')
    }
}
"""

        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("idea").run()

        //then
        def dependencies = parseIml("master/one/one.iml").dependencies
        assert dependencies.modules.size() == 2
        dependencies.assertHasModule('COMPILE', "two")
        dependencies.assertHasModule('COMPILE', "three")

        dependencies = parseIml("master/two/two.iml").dependencies
        assert dependencies.modules.size() == 2
        dependencies.assertHasModule('COMPILE', "three")
        dependencies.assertHasModule('COMPILE', "one")

        dependencies = parseIml("master/three/three.iml").dependencies
        assert dependencies.modules.size() == 2
        dependencies.assertHasModule('COMPILE', "one")
        dependencies.assertHasModule('COMPILE', "two")
    }

    @Test
    void classpathContainsConflictResolvedDependencies() {
        def someLib1Jar = mavenRepo.module('someGroup', 'someLib', '1.0').publish().artifactFile
        def someLib2Jar= mavenRepo.module('someGroup', 'someLib', '2.0').publish().artifactFile

        def settingsFile = file("master/settings.gradle")
        settingsFile << """
include 'one'
include 'two'
        """
        def buildFile = file("master/build.gradle")
        buildFile << """
allprojects {
    apply plugin: 'java-library'
    apply plugin: 'idea'

    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}

project(':one') {
    dependencies {
        implementation ('someGroup:someLib:1.0') {
            force = project.hasProperty("forceDeps")
        }
        implementation project(':two')
    }
}

project(':two') {
    dependencies {
        api 'someGroup:someLib:2.0'
    }
}

"""
        //when
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withTasks("idea").run()

        //then
        def dependencies = parseIml("master/one/one.iml").dependencies
        dependencies.assertHasModule('COMPILE', "two")
        assert dependencies.libraries*.jarName as Set == [someLib2Jar.name] as Set

        dependencies = parseIml("master/two/two.iml").dependencies
        assert dependencies.libraries*.jarName as Set == [someLib2Jar.name] as Set

        executer.expectDeprecationWarning()
        executer.usingBuildScript(buildFile).usingSettingsFile(settingsFile).withArgument("-PforceDeps=true").withTasks("idea").run()

        //then
        dependencies = parseIml("master/one/one.iml").dependencies
        assert dependencies.modules.size() == 1
        dependencies.assertHasModule('COMPILE', "two")
        assert dependencies.libraries*.jarName as Set == [someLib1Jar.name] as Set

        dependencies = parseIml("master/two/two.iml").dependencies
        assert dependencies.libraries*.jarName as Set == [someLib2Jar.name] as Set
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
        implementation project(':nonIdeaProject')
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
        implementation project(':api')
    }
}

project(':app') {
    dependencies {
        implementation project(':api')
        testImplementation project(':impl')
        runtimeOnly project(':impl')
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
        dependencies.assertHasModule(['RUNTIME','TEST'], 'impl')
    }
}
