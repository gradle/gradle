/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.declarativedsl

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.features.internal.TestScenarioFixture
import org.gradle.features.internal.builders.JavaBeanStyle
import org.gradle.features.internal.builders.TypeShape
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.plugin.PluginBuilder

@PolyglotDslTest
@SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy DSL is not supported for declarative configuration")
class WorkingWithFilesIntegrationTest extends AbstractIntegrationSpec implements TestScenarioFixture, PolyglotTestFixture {

    def setup() {
        file("gradle.properties") << """
            org.gradle.kotlin.dsl.dcl=true
        """
    }

    def 'set single value: #name (set defaults: #setDefaults) (set values: #setValues)'() {
        given:
        scenario.buildScenario(this).prepareToExecute()

        def defaultsConfig = """
            defaults {
                testProjectType {
                    dir = layout.projectDirectory.dir("defaultDir")
                    file = layout.settingsDirectory.file("defaultFile")
                }
            }
        """.stripIndent()
        settingsFile() << getSettingsFileContent(setDefaults ? defaultsConfig : "")

        def projectTypeConfig = """
            dir = layout.projectDirectory.dir("someDir")
            file = layout.settingsDirectory.file("someFile")
        """.stripIndent()
        buildFileForProject("a") << getProjectFileContent(setValues ? projectTypeConfig : "") << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl
        buildFileForProject("b") << getProjectFileContent(setValues ? projectTypeConfig : "") << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        def expectedNamePrefix = setValues ? "some" : "default"

        when:
        run("a:printTestProjectTypeDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly("a", expectedNamePrefix)

        when:
        run("b:printTestProjectTypeDefinitionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly("b", expectedNamePrefix)

        where:
        scenario                           | name                                             | setDefaults | setValues
        fileSystemLocationProperties()     | "DirectoryProperty & RegularFileProperty"        | false       | true
        fileSystemLocationProperties()     | "DirectoryProperty & RegularFileProperty"        | true        | false
        fileSystemLocationProperties()     | "DirectoryProperty & RegularFileProperty"        | true        | true
        propertiesOfFileSystemLocations()  | "Property<Directory> & Property<RegularFile>"    | false       | true
        propertiesOfFileSystemLocations()  | "Property<Directory> & Property<RegularFile>"    | true        | false
        propertiesOfFileSystemLocations()  | "Property<Directory> & Property<RegularFile>"    | true        | true
        javaBeanFileSystemLocations()      | "Directory and RegularFile Java Bean properties" | false       | true
        javaBeanFileSystemLocations()      | "Directory and RegularFile Java Bean properties" | true        | false
        javaBeanFileSystemLocations()      | "Directory and RegularFile Java Bean properties" | true        | true
    }

    def 'set multi value: #name (set defaults: #setDefaults) (set values: #setValues)'() {
        given:
        fileSystemLocationListProperties().buildScenario(this).prepareToExecute()

        def defaultsConfig = """
            defaults {
                testProjectType {
                    dirs = listOf(layout.projectDirectory.dir("defaultDir1"), layout.projectDirectory.dir("defaultDir2"))
                    files = listOf(layout.settingsDirectory.file("defaultFile1"), layout.settingsDirectory.file("defaultFile2"))
                }
            }
        """.stripIndent()
        settingsFile() << getSettingsFileContent(setDefaults ? defaultsConfig : "")

        def projectTypeConfig = """
            dirs = listOf(layout.projectDirectory.dir("someDir1"), layout.projectDirectory.dir("someDir2"))
            files = listOf(layout.settingsDirectory.file("someFile1"), layout.settingsDirectory.file("someFile2"))
        """.stripIndent()
        buildFileForProject("a") << getProjectFileContent(setValues ? projectTypeConfig : "") << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl
        buildFileForProject("b") << getProjectFileContent(setValues ? projectTypeConfig : "") << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        def expectedNamePrefix = setValues ? "some" : "default"

        when:
        run("a:printTestProjectTypeDefinitionConfiguration")

        then:
        assertThatDeclaredListValuesAreSetProperly("a", expectedNamePrefix)

        when:
        run("b:printTestProjectTypeDefinitionConfiguration")

        then:
        assertThatDeclaredListValuesAreSetProperly("b", expectedNamePrefix)

        where:
        name                                                  | setDefaults | setValues
        "ListProperty<Directory> & ListProperty<RegularFile>" | false       | true
        "ListProperty<Directory> & ListProperty<RegularFile>" | true        | false
        "ListProperty<Directory> & ListProperty<RegularFile>" | true        | true
    }

    @SkipDsl(dsl = GradleDsl.KOTLIN, because = "Test is specific to the Declarative DSL")
    def "using a read-only property by mistake gives a helpful error message for #name (set defaults: #setDefaults)"() {
        given:
        scenario.buildScenario(this).prepareToExecute()

        def defaultsConfig = """
            defaults {
                testProjectType {
                    dir = layout.projectDirectory.dir("defaultDir")
                    file = layout.settingsDirectory.file("defaultFile")
                }
            }
        """.stripIndent()
        settingsFile() << getSettingsFileContent(setDefaults ? defaultsConfig : "")

        def projectTypeConfig = """
            dir = listOf(layout.projectDirectory.dir("someDir1"), layout.projectDirectory.dir("someDir2"))
            file = listOf(layout.settingsDirectory.file("someFile1"), layout.settingsDirectory.file("someFile2"))
        """.stripIndent()
        buildFileForProject("a") << getProjectFileContent(projectTypeConfig) << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl
        buildFileForProject("b") << ""

        when:
        fails(":printTestProjectTypeDefinitionConfiguration")

        then:
        failureCauseContains("Failed to interpret the declarative DSL file '${(setDefaults ? settingsFile() : buildFileForProject("a")).path}':")
        failureCauseContains("Failures in resolution:")
        failureCauseContains("assignment to property '$propName' with read-only type '$name'")

        where:
        scenario                       | name          | propName | setDefaults
        readOnlyDirectoryProperty()    | "Directory"   | "dir"    | true
        readOnlyDirectoryProperty()    | "Directory"   | "dir"    | false
        readOnlyRegularFileProperty()  | "RegularFile" | "file"   | true
        readOnlyRegularFileProperty()  | "RegularFile" | "file"   | false
    }

    @SkipDsl(dsl = GradleDsl.DECLARATIVE, because = "The situation is prohibited in Declarative DSL via other means")
    def "using project layout inside the `defaults` block, but outside of software definition sub-blocks throws"() {
        given:
        settingsFile() << """
            defaults {
                println("layout = \${layout}")
            }
        """

        when:
        fails("help")

        then:
        if (GradleDsl.KOTLIN == currentDsl()) {
            failure.assertHasDescription("ProjectLayout should be referenced only inside of project type default configuration blocks")
        } else if (GradleDsl.GROOVY == currentDsl()) {
            failure.assertHasCause("ProjectLayout should be referenced only inside of project type default configuration blocks")
        } else {
            throw new RuntimeException("Shouldn't happen")
        }
    }

    // --- Scenario callbacks ---

    /**
     * A callback that builds a test scenario given a {@link TestScenarioFixture}.
     * Used in {@code where:} blocks to defer scenario construction until each iteration is executing in a non-static context.
     */
    private interface TestScenarioCallback {
        PluginBuilder buildScenario(TestScenarioFixture fixture)
    }

    private static TestScenarioCallback fileSystemLocationProperties() {
        return { TestScenarioFixture fixture ->
            fixture.testScenario {
                projectType("testProjectType") {
                    definition {
                        property "dir", DirectoryProperty
                        property "file", RegularFileProperty
                        buildModel {
                            property "dir", DirectoryProperty
                            property "file", RegularFileProperty
                            mapping """
                                model.getDir().set(definition.getDir());
                                model.getFile().set(definition.getFile());
                            """
                        }
                    }
                    plugin {
                        unsafeDefinition()
                    }
                }
            }
        }
    }

    private static TestScenarioCallback propertiesOfFileSystemLocations() {
        return { TestScenarioFixture fixture ->
            fixture.testScenario {
                projectType("testProjectType") {
                    definition {
                        shape TypeShape.ABSTRACT_CLASS
                        property "dir", Directory
                        property "file", RegularFile
                        buildModel {
                            property "dir", DirectoryProperty
                            property "file", RegularFileProperty
                            mapping """
                                model.getDir().set(definition.getDir());
                                model.getFile().set(definition.getFile());
                            """
                        }
                    }
                    plugin {
                        unsafeDefinition()
                    }
                }
            }
        }
    }

    private static TestScenarioCallback javaBeanFileSystemLocations() {
        return { TestScenarioFixture fixture ->
            fixture.testScenario {
                projectType("testProjectType") {
                    definition {
                        shape TypeShape.ABSTRACT_CLASS
                        javaBeanProperty("dir", Directory) {
                            shape JavaBeanStyle.CONCRETE
                        }
                        javaBeanProperty("file", RegularFile) {
                            shape JavaBeanStyle.CONCRETE
                        }
                        buildModel {
                            property "dir", DirectoryProperty
                            property "file", RegularFileProperty
                            mapping """
                                model.getDir().set(definition.getDir());
                                model.getFile().set(definition.getFile());
                            """
                        }
                    }
                    plugin {
                        unsafeDefinition()
                    }
                }
            }
        }
    }

    private static TestScenarioCallback readOnlyDirectoryProperty() {
        return { TestScenarioFixture fixture ->
            fixture.testScenario {
                projectType("testProjectType") {
                    definition {
                        readOnlyProperty "dir", Directory
                        property "file", RegularFileProperty
                        buildModel {
                            property "dir", DirectoryProperty
                            property "file", RegularFileProperty
                            mapping """
                                model.getDir().set(definition.getDir());
                                model.getFile().set(definition.getFile());
                            """
                        }
                    }
                }
            }
        }
    }

    private static TestScenarioCallback readOnlyRegularFileProperty() {
        return { TestScenarioFixture fixture ->
            fixture.testScenario {
                projectType("testProjectType") {
                    definition {
                        property "dir", DirectoryProperty
                        readOnlyProperty "file", RegularFile
                        buildModel {
                            property "dir", DirectoryProperty
                            property "file", RegularFileProperty
                            mapping """
                                model.getDir().set(definition.getDir());
                                model.getFile().set(definition.getFile());
                            """
                        }
                    }
                }
            }
        }
    }

    private static TestScenarioCallback fileSystemLocationListProperties() {
        return { TestScenarioFixture fixture ->
            fixture.testScenario {
                projectType("testProjectType") {
                    definition {
                        listProperty "dirs", Directory
                        listProperty "files", RegularFile
                        buildModel {
                            listProperty "dirs", Directory
                            listProperty "files", RegularFile
                            mapping """
                                model.getDirs().set(definition.getDirs());
                                model.getFiles().set(definition.getFiles());
                            """
                        }
                    }
                    plugin {
                        unsafeDefinition()
                    }
                }
            }
        }
    }

    // --- Script content helpers ---

    private static String getSettingsFileContent(String defaultsConfig) {
        return """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-ecosystem")
            }
            include("a")
            include("b")
            $defaultsConfig
        """
    }

    private static String getProjectFileContent(String projectTypeConfig) {
        return """
            testProjectType {
                $projectTypeConfig
            }
        """.stripIndent()
    }

    // --- Assertion helpers ---

    private void assertThatDeclaredValuesAreSetProperly(String project, String namePrefix) {
        def dirPrefix = testDirectory.file("$project/${namePrefix}").path
        def filePrefix = testDirectory.file("${namePrefix}").path
        outputContains("definition dir = ${dirPrefix}Dir")
        outputContains("definition file = ${filePrefix}File")
    }

    private void assertThatDeclaredListValuesAreSetProperly(String project, String namePrefix) {
        def dirPrefix = testDirectory.file("$project/${namePrefix}").path
        def filePrefix = testDirectory.file("${namePrefix}").path
        outputContains("definition dirs = [${dirPrefix}Dir1, ${dirPrefix}Dir2]")
        outputContains("definition files = [${filePrefix}File1, ${filePrefix}File2]")
    }
}
