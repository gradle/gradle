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

package org.gradle.api.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.build.BuildTestFile

class BuildLayoutIntegrationTest extends AbstractIntegrationSpec {
    private String printLocations() {
        settingsScriptSnippet """
            println "settings root dir: " + layout.rootDirectory + "."
            println "settings dir: " + layout.settingsDirectory + "."
            println "settings source file: " + layout.settingsDirectory.file(providers.provider { buildscript.sourceFile.name }).get() + "."
        """
    }

    def "layout is available for injection"() {
        settingsFile """
            abstract class SomePlugin implements Plugin<Settings> {
                @Inject
                abstract BuildLayout getLayout()

                @Inject
                abstract ProviderFactory getProviders()

                void apply(Settings s) {
                    s.with {
                        ${printLocations()}
                    }
                }
            }

            apply plugin: SomePlugin
        """

        when:
        run("help")

        then:
        outputContains("settings root dir: " + testDirectory + ".")
        outputContains("settings dir: " + testDirectory + ".")
        outputContains("settings source file: " + settingsFile + ".")
    }

    def "layout is available for scripts"() {
        settingsFile """
            ${printLocations()}
        """

        when:
        run("help")

        then:
        outputContains("settings root dir: " + testDirectory + ".")
        outputContains("settings dir: " + testDirectory + ".")
        outputContains("settings source file: " + settingsFile + ".")
    }

    def "locations are as expected in an included build"() {
        buildTestFixture.withBuildInSubDir()
        def buildB = singleProjectBuild("buildB") { BuildTestFile build ->
            settingsFile build.settingsFile, """
                ${printLocations()}
            """
        }

        def rootBuild = singleProjectBuild("buildA") { BuildTestFile build ->
            settingsFile build.settingsFile, """
                includeBuild "${buildB.toURI()}"
            """
        }

        when:
        run("project", "--project-dir", rootBuild.absolutePath)

        then:
        outputContains("settings root dir: " + buildB.absolutePath + ".")
        outputContains("settings dir: " + buildB.absolutePath + ".")
        outputContains("settings source file: " + buildB.settingsFile.absolutePath + ".")
    }

    def "locations are as expected in buildSrc settings"() {
        settingsFile """
        // just a marker file
        """

        def buildSrcDir = file("buildSrc")
        def buildSrcSettingsFile = buildSrcDir.file("settings.gradle")
        settingsFile buildSrcSettingsFile, """
            ${printLocations()}
        """

        when:
        run("project")

        then:
        outputContains("settings root dir: " + buildSrcDir + ".")
        outputContains("settings dir: " + buildSrcDir + ".")
        outputContains("settings source file: " + buildSrcSettingsFile + ".")
    }

    def "injecting internal #serviceName is deprecated"() {
        buildFile """
             abstract class SomePlugin implements Plugin<Project> {
                @Inject
                abstract ${serviceClass.name} getService()

                void apply(Project p) {
                    getService()
                }
            }

            apply plugin: SomePlugin
        """

        executer.expectDocumentedDeprecationWarning("Injecting 'org.gradle.initialization.layout.BuildLayout' or 'org.gradle.initialization.SettingsLocation' service has been deprecated. This is scheduled to be removed in Gradle 10. These classes are not part of the public API. Instead 'org.gradle.api.file.BuildLayout.settingsDirectory' in settings plugins or 'ProjectLayout.settingsDirectory' in project plugins. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecate-internal-buildlayout")

        expect:
        run "help"

        where:
        serviceClass << [org.gradle.initialization.layout.BuildLayout, org.gradle.initialization.SettingsLocation]
        serviceName = serviceClass.simpleName
    }
}
