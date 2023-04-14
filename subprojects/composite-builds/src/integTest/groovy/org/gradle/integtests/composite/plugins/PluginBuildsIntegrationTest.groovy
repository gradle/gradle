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

package org.gradle.integtests.composite.plugins

import org.gradle.integtests.fixtures.resolve.ResolveFailureTestFixture

class PluginBuildsIntegrationTest extends AbstractPluginBuildIntegrationTest {

    def "included plugin builds can contribute settings plugins"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}")
            }
        """

        when:
        succeeds()

        then:
        pluginBuild.assertSettingsPluginApplied()
    }

    def "included plugin builds can contribute project plugins"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}")
            }
        """

        when:
        succeeds()

        then:
        pluginBuild.assertProjectPluginApplied()
    }

    def "included plugin build can contribute both settings and project plugins"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}")
            }
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}")
            }
        """

        when:
        succeeds()

        then:
        pluginBuild.assertSettingsPluginApplied()
        pluginBuild.assertProjectPluginApplied()
    }

    def "can execute a task from included plugin build"() {
        given:
        def pluginBuild = pluginBuild("build-logic")

        when:
        settingsFile << """
            pluginManagement {
                includeBuild("$pluginBuild")
            }
        """

        then:
        succeeds(":$pluginBuild:jar")
    }

    def "settings plugin from included build is used over published plugin when no version is specified"() {
        given:
        def repoDeclaration = """
            repositories {
                maven {
                    url("${mavenRepo.uri}")
                }
            }
        """
        def pluginBuild = pluginBuild("build-logic")
        publishSettingsPlugin(pluginBuild.settingsPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${pluginBuild.buildName}")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}")
            }
        """

        then:
        succeeds()
        pluginBuild.assertSettingsPluginApplied()
    }

    def "settings plugin from included build is used over published plugin when version specified is found in repository"() {
        given:
        def repoDeclaration = """
            repositories {
                maven {
                    url("${mavenRepo.uri}")
                }
            }
        """
        def pluginBuild = pluginBuild("build-logic")
        publishSettingsPlugin(pluginBuild.settingsPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${pluginBuild.buildName}")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}") version "1.0"
            }
        """

        then:
        succeeds()
        pluginBuild.assertSettingsPluginApplied()
    }

    def "settings plugin from included build is used over published plugin when version specified is not found in repository"() {
        given:
        def repoDeclaration = """
            repositories {
                maven {
                    url("${mavenRepo.uri}")
                }
            }
        """
        def pluginBuild = pluginBuild("build-logic")
        publishSettingsPlugin(pluginBuild.settingsPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${pluginBuild.buildName}")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}") version "2.0"
            }
        """

        then:
        succeeds()
        pluginBuild.assertSettingsPluginApplied()
    }

    def "project plugin from included build is used over published plugin when no version is specified"() {
        given:
        def repoDeclaration = """
            repositories {
                maven {
                    url("${mavenRepo.uri}")
                }
            }
        """
        def pluginBuild = pluginBuild("build-logic")
        publishProjectPlugin(pluginBuild.projectPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${pluginBuild.buildName}")
            }
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}")
            }
        """

        then:
        succeeds()
        pluginBuild.assertProjectPluginApplied()
    }

    def "project plugin from included build is used over published plugin when version specified is found in repository"() {
        given:
        def repoDeclaration = """
            repositories {
                maven {
                    url("${mavenRepo.uri}")
                }
            }
        """
        def pluginBuild = pluginBuild("build-logic")
        publishProjectPlugin(pluginBuild.projectPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${pluginBuild.buildName}")
            }
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}") version "1.0"
            }
        """

        then:
        succeeds()
        pluginBuild.assertProjectPluginApplied()
    }

    def "project plugin from included build is used over published plugin when version specified is not found in repository"() {
        given:
        def repoDeclaration = """
            repositories {
                maven {
                    url("${mavenRepo.uri}")
                }
            }
        """
        def pluginBuild = pluginBuild("build-logic")
        publishProjectPlugin(pluginBuild.projectPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${pluginBuild.buildName}")
            }
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}") version "2.0"
            }
        """

        then:
        succeeds()
        pluginBuild.assertProjectPluginApplied()
    }

    def "regular included build can not contribute settings plugins"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        settingsFile << """
            plugins {
                id("${pluginBuild.settingsPluginId}")
            }
            includeBuild("${pluginBuild.buildName}")
        """

        when:
        fails()

        then:
        failureDescriptionContains("Plugin [id: '${pluginBuild.settingsPluginId}'] was not found in any of the following sources:")
    }

    def "included plugin build is not visible as library component"() {
        def fixture = new ResolveFailureTestFixture(buildFile)

        given:
        def build = pluginAndLibraryBuild("included-build")
        settingsFile << """
            pluginManagement {
                includeBuild("${build.buildName}")
            }
        """

        when:
        buildFile << """
            configurations.create("conf") {
                canBeConsumed = false
            }
            dependencies {
                conf("${build.group}:${build.buildName}")
            }
        """
        fixture.prepare("conf")

        then:
        fails("checkDeps")
        fixture.assertFailurePresent(failure)
        failureCauseContains("Cannot resolve external dependency com.example:included-build")
    }

    def "a build can be included both as a plugin build and as regular build and can contribute both plugins and library components"() {
        given:
        def build = pluginAndLibraryBuild("included-build")
        settingsFile << """
            pluginManagement {
                includeBuild("${build.buildName}")
            }
            includeBuild("${build.buildName}")
        """

        when:
        buildFile << """
            plugins {
                id("java-library")
                id("${build.projectPluginId}")
            }
            dependencies {
                implementation("${build.group}:${build.buildName}")
            }
        """
        file("src/main/java/Foo.java") << """
            class Foo { Bar newBar() { return new Bar(); }}
        """

        then:
        succeeds("build")
        executed(":${build.buildName}:compileJava")
        executed(":compileJava")
        build.assertProjectPluginApplied()
    }

    def "Including a build as both plugin build and regular build does not lead to an error in the presence of include cycles"() {
        given:
        def commonsPluginBuild = pluginBuild("commons-plugin-build", dsl == 'Kotlin')
        def mainPluginBuild = pluginBuild("main-plugin-build")

        commonsPluginBuild.settingsFile.text = """
            pluginManagement {
                includeBuild("../${mainPluginBuild.buildName}")
            }
        """
        commonsPluginBuild.projectPluginFile.text = """
            plugins {
                id("groovy-gradle-plugin")
            }
        """

        mainPluginBuild.settingsFile.text = """
            pluginManagement {
                includeBuild("../${commonsPluginBuild.buildName}")
            }
        """
        mainPluginBuild.buildFile.text = """
           plugins {
                id("${commonsPluginBuild.projectPluginId}")
            }
        """

        settingsFile << """
            pluginManagement {
                includeBuild("${mainPluginBuild.buildName}")
            }
            includeBuild("${mainPluginBuild.buildName}")
        """

        when:
        buildFile << """
            plugins {
                id("${mainPluginBuild.projectPluginId}")
            }
        """

        then:
        succeeds("help")

        where:
        dsl << ['Groovy', 'Kotlin']
    }

    def "a build can be included both as a plugin build and as regular build and can contribute both settings plugins and library components"() {
        given:
        def build = pluginAndLibraryBuild("included-build")
        settingsFile << """
            pluginManagement {
                includeBuild("${build.buildName}")
            }
            plugins {
                id("${build.settingsPluginId}")
            }
            includeBuild("${build.buildName}")
        """

        when:
        buildFile << """
            plugins {
                id("java-library")
                id("${build.projectPluginId}")
            }
            dependencies {
                implementation("${build.group}:${build.buildName}")
            }
        """
        file("src/main/java/Foo.java") << """
            class Foo { Bar newBar() { return new Bar(); }}
        """

        then:
        succeeds("build")
        executed(":${build.buildName}:compileJava")
        executed(":compileJava")
        build.assertSettingsPluginApplied()
        build.assertProjectPluginApplied()
    }

    def "library build included in plugin build can be used in settings plugin when such settings plugin is included in another build"() {
        given:
        def libraryBuild = pluginAndLibraryBuild("library")
        def pluginBuild = pluginBuild("plugin")

        pluginBuild.settingsFile << """
            includeBuild("../${libraryBuild.buildName}")
        """
        pluginBuild.buildFile << """
            dependencies {
                implementation("${libraryBuild.group}:${libraryBuild.buildName}")
            }
        """

        when:
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}")
            }
        """

        then:
        succeeds()
        pluginBuild.assertSettingsPluginApplied()
    }

    def "library build included in plugin build can be used in project plugin when such project plugin is included in another build"() {
        given:
        def libraryBuild = pluginAndLibraryBuild("library")
        def pluginBuild = pluginBuild("plugin")

        pluginBuild.settingsFile << """
            includeBuild("../${libraryBuild.buildName}")
        """
        pluginBuild.buildFile << """
            dependencies {
                implementation("${libraryBuild.group}:${libraryBuild.buildName}")
            }
        """

        when:
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}")
            }
        """

        then:
        succeeds()
        pluginBuild.assertProjectPluginApplied()
    }

    def "a build that applies an included settings plugin can be included in another build"() {
        given:
        def settingsPluginBuild = pluginBuild("settings")
        def lib1 = pluginAndLibraryBuild("lib1")

        when:
        lib1.settingsFile.setText("""
            pluginManagement {
                includeBuild("../${settingsPluginBuild.buildName}")
            }
            plugins {
                id("${settingsPluginBuild.settingsPluginId}")
            }
            rootProject.name="${lib1.buildName}"
        """)
        settingsFile << """
            includeBuild("${lib1.buildName}")
        """

        then:
        succeeds()
        settingsPluginBuild.assertSettingsPluginApplied()
    }

    def "a build can depend on included library build that applies a project plugin that comes from an included settings plugin and depends on another included build"() {
        given:
        def pluginLibraryBuild = pluginAndLibraryBuild("plugin-lib")
        def projectPluginBuild = pluginBuild("project-plugin")
        projectPluginBuild.settingsFile << """
            includeBuild("../${pluginLibraryBuild.buildName}")
        """
        projectPluginBuild.buildFile << """
            dependencies {
                implementation("${pluginLibraryBuild.group}:${pluginLibraryBuild.buildName}")
            }
        """
        def settingsPluginBuild = pluginBuild("settings-plugin")
        settingsPluginBuild.settingsPluginFile.text = """
            pluginManagement {
                includeBuild("../${projectPluginBuild.buildName}")
            }
        """ + settingsPluginBuild.settingsPluginFile.text

        def projectLibrary = pluginAndLibraryBuild("project-lib")
        projectLibrary.settingsFile.setText("""
            pluginManagement {
                includeBuild("../${settingsPluginBuild.buildName}")
            }
            plugins {
                id("${settingsPluginBuild.settingsPluginId}")
            }
            rootProject.name="${projectLibrary.buildName}"
        """)
        projectLibrary.buildFile.setText("""
            plugins {
                id("java-library")
                id("${projectPluginBuild.projectPluginId}")
            }
            group = "${projectLibrary.group}"
        """)

        when:
        settingsFile << """
            includeBuild("${projectLibrary.buildName}")
        """
        buildFile << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation("${projectLibrary.group}:${projectLibrary.buildName}")
            }
        """

        then:
        succeeds("check")
        settingsPluginBuild.assertSettingsPluginApplied()
        projectPluginBuild.assertProjectPluginApplied()
    }

    def "plugin builds can include each other without consequences"() {
        given:
        def settingsPluginBuild = pluginBuild("settings-plugin", dsl == 'Kotlin')
        def buildLogicBuild = pluginBuild("build-logic", dsl == 'Kotlin')
        def mainBuild = createDir('main')

        executer.inDirectory(mainBuild)

        settingsPluginBuild.settingsPluginFile.text = """
            pluginManagement {
                includeBuild("../${buildLogicBuild.buildName}")
            }

            println("settings plugin applied in \${rootDir.name}")
        """

        buildLogicBuild.settingsFile.text = """
            pluginManagement {
                includeBuild("../${settingsPluginBuild.buildName}")
            }
            plugins {
                id("${settingsPluginBuild.settingsPluginId}")
            }
        """

        // make sure both settings and project plugins are still found despite the include cycle
        mainBuild.file('settings.gradle') << """
            pluginManagement {
                includeBuild("../${settingsPluginBuild.buildName}")
            }
            plugins {
                id("${settingsPluginBuild.settingsPluginId}")
            }
        """
        mainBuild.file('build.gradle') << """
            plugins {
                id("${buildLogicBuild.projectPluginId}")
            }
        """

        when:
        succeeds()

        then:
        outputContains("settings plugin applied in build-logic")
        outputContains("settings plugin applied in main")
        buildLogicBuild.assertProjectPluginApplied()

        where:
        dsl << ['Groovy', 'Kotlin']
    }
}
