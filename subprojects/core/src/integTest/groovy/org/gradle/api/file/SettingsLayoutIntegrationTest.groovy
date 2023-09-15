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

class SettingsLayoutIntegrationTest extends AbstractIntegrationSpec {
    private String printLocations() {
        groovyScript """
            println "settings root dir: " + layout.rootDirectory + "."
            println "settings dir: " + layout.settingsDirectory + "."
            println "settings source file: " + layout.dir(providers.provider { buildscript.sourceFile }).get() + "."
            println "settings relative location: " + layout.file(providers.provider { new File("somefile.txt") }).get() + "."
        """
    }

    def "layout is available for injection"() {
        settingsFile """
            import javax.inject.Inject
            import org.gradle.api.file.FileSystemLayout
            import org.gradle.api.provider.ProviderFactory
            import org.gradle.api.initialization.Settings
            abstract class SomePlugin implements Plugin<Settings> {
                @Inject
                abstract SettingsLayout getLayout()

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
        outputContains("settings relative location: " + testDirectory.file("somefile.txt") + ".")
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
        outputContains("settings relative location: " + testDirectory.file("somefile.txt") + ".")
    }

    def "locations are as expected for non-standard settings locations available for scripts"() {
        def customSettingsPath = "custom-subdir/custom-settings.gradle"
        def customSettingsFile = testDirectory.file(customSettingsPath)
        def customSettingsDir = customSettingsFile.parentFile
        // setting a custom settings location is deprecated
        executer.noDeprecationChecks()
        groovyFile(customSettingsFile, """
            rootProject.projectDir = file('..')
            ${printLocations()}
        """)

        when:
        run("help", "--settings-file", customSettingsPath)

        then:
        outputContains("settings root dir: " + testDirectory + ".")
        outputContains("settings dir: " + customSettingsDir + ".")
        outputContains("settings source file: " + customSettingsFile + ".")
        outputContains("settings relative location: " + customSettingsDir.file("somefile.txt") + ".")
    }

    def "locations are as expected in an included build"() {
        buildTestFixture.withBuildInSubDir()
        def buildB = singleProjectBuild("buildB") { BuildTestFile build ->
            groovyFile(build.settingsFile, """
                ${printLocations()}
            """)
        }

        def rootBuild = singleProjectBuild("buildA") { BuildTestFile build ->
            groovyFile(build.settingsFile, """
                includeBuild "${buildB.toURI()}"
            """)
        }

        when:
        run("project", "--project-dir", rootBuild.absolutePath)

        then:
        outputContains("settings root dir: " + buildB.absolutePath + ".")
        outputContains("settings dir: " + buildB.absolutePath + ".")
        outputContains("settings source file: " + buildB.settingsFile.absolutePath + ".")
        outputContains("settings relative location: " + buildB.file("somefile.txt") + ".")
    }

    def "locations are as expected in buildSrc settings"() {
        settingsFile """
        // just a marker file
        """

        def buildSrcDir = file("buildSrc")
        def buildSrcSettingsFile = buildSrcDir.file("settings.gradle")
        groovyFile(buildSrcSettingsFile, """
            ${printLocations()}
        """)

        when:
        run("project")

        then:
        outputContains("settings root dir: " + buildSrcDir + ".")
        outputContains("settings dir: " + buildSrcDir + ".")
        outputContains("settings source file: " + buildSrcSettingsFile + ".")
        outputContains("settings relative location: " + buildSrcDir.file("somefile.txt") + ".")
    }
}
