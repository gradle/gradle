/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.TestResources
import org.gradle.plugins.ide.AbstractIdeIntegrationTest
import org.junit.Rule
import org.junit.Test

class EclipseMultiModuleIntegrationTest extends AbstractIdeIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources(testDirectoryProvider)

    @Test
    @ToBeFixedForConfigurationCache
    void dealsWithDuplicatedModuleNames() {
      /*
      This is the multi-module project structure the integration test works with:
      -root
        -api
        -shared
          -api
          -model
        -services
          -utilities (renamed by user to 'util'
        -util
        -contrib
          -services
            -util
      */

        def settingsFile = file("settings.gradle")
        createDirs("api", "shared", "shared/api", "shared/model", "services", "services/utilities", "util", "contrib", "contrib/services", "contrib/services/util")
        settingsFile << """
rootProject.name = 'root'
include 'api'
include 'shared:api', 'shared:model'
include 'services:utilities'
include 'util'
include 'contrib:services:util'

        """

        def buildFile = file("build.gradle")
        buildFile << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'eclipse'
}

project(':api') {
    dependencies {
        implementation project(':shared:api'), project(':shared:model')
    }
}

project(':shared:model') {
    eclipse {
        project.name = 'very-cool-model'
    }
}

project(':services:utilities') {
    dependencies {
        implementation project(':util'), project(':contrib:services:util'), project(':shared:api'), project(':shared:model')
    }
    eclipse {
        project.name = 'util'
    }
}
"""

        //when
        executer.withTasks("eclipse").run()

        //then
        assertApiProjectContainsCorrectDependencies()
        assertServicesUtilProjectContainsCorrectDependencies()
    }

    def assertServicesUtilProjectContainsCorrectDependencies() {
        List deps = parseEclipseProjectDependencies(project: 'services/utilities')

        assert deps.contains("/very-cool-model")
        assert deps.contains("/root-util")
        assert deps.contains("/shared-api")
        assert deps.contains("/services-util")
    }

    def assertApiProjectContainsCorrectDependencies() {
        def deps = parseEclipseProjectDependencies(project: 'api')

        assert deps.contains("/very-cool-model")
        assert deps.contains("/shared-api")
    }

    @Test
    @ToBeFixedForConfigurationCache
    void shouldCreateCorrectClasspathEvenIfUserReconfiguresTheProjectName() {
        //use case from the mailing list
        def settingsFile = file("settings.gradle")
        createDirs("api", "shared", "shared/model", "nonEclipse")
        settingsFile << """
            rootProject.name = "master"
            include 'api', 'shared:model', 'nonEclipse'
        """

        def buildFile = file("build.gradle")
        buildFile << """
            allprojects {
                apply plugin: 'java'
                if (project.name != 'nonEclipse') {
                    apply plugin: 'eclipse'
                }
            }

            subprojects {
                eclipse {
                    project {
                        name = rootProject.name + path.replace(':', '-')
                    }
                }
            }

            project(':api') {
                dependencies {
                    //let's add a nonEclipse project to stress the test
                    implementation project(':shared:model'), project(':nonEclipse')
                }
            }
        """

        //when
        executer.withTasks("eclipse").run()

        //then
        def deps = parseEclipseProjectDependencies(project: 'api')

        assert deps.contains("/master-shared-model")
        assert deps.contains("/nonEclipse")
    }

    @Test
    @ToBeFixedForConfigurationCache
    void shouldCreateCorrectClasspathEvenIfUserReconfiguresTheProjectNameAndRootProjectDoesNotApplyEclipsePlugin() {
        createDirs("api", "shared", "shared/model")
        file("settings.gradle") << """
            rootProject.name = "master"
            include 'api', 'shared:model'
        """

        file("build.gradle") << """
            subprojects {
                apply plugin: 'java'
                apply plugin: 'eclipse'

                eclipse {
                    project {
                        name = rootProject.name + path.replace(':', '-')
                    }
                }
            }

            project(':api') {
                dependencies {
                    implementation project(':shared:model')
                }
            }
        """

        //when
        executer.withTasks("eclipse").run()

        //then
        def deps = parseEclipseProjectDependencies(project: 'api')

        assert deps.contains("/master-shared-model")
    }

    List parseEclipseProjectDependencies(def options) {
        def eclipseClasspathFile = parseFile(options, ".classpath")
        def deps = eclipseClasspathFile.classpathentry.findAll { it.@kind.text() == 'src' }.collect { it.@path.text() }
        return deps
    }
}
