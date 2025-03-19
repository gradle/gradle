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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.internal.declarativedsl.settings.SoftwareTypeFixture
import org.gradle.test.fixtures.dsl.GradleDsl

@PolyglotDslTest
class WorkingWithFilesIntegrationTest extends AbstractIntegrationSpec implements SoftwareTypeFixture, PolyglotTestFixture {

    def setup() {
        file("gradle.properties") << """
            org.gradle.kotlin.dsl.dcl=true
        """
    }

    def 'set single value: #name (set defaults: #setDefaults) (set values: #setValues)'() {
        given:
        withSoftwareTypePlugins(
            extensionClassContent,
            getProjectPluginThatRegistersItsOwnExtension(true, "extension", null),
            settingsPluginThatRegistersSoftwareType
        ).prepareToExecute()

        def defaultsConfig = """
            defaults {
                testSoftwareType {
                    dir = layout.projectDirectory.dir("defaultDir")
                    file = layout.settingsDirectory.file("defaultFile")
                }
            }
        """.stripIndent()
        settingsFile() << getSettingsFileContent(setDefaults ? defaultsConfig : "")

        def softwareTypeConfig = """
            dir = layout.projectDirectory.dir("someDir")
            file = layout.settingsDirectory.file("someFile")
        """.stripIndent()
        buildFileForProject("a") << getProjectFileContent(setValues ? softwareTypeConfig : "")
        buildFileForProject("b") << getProjectFileContent(setValues ? softwareTypeConfig : "")

        when:
        run("printTestSoftwareTypeExtensionConfiguration")

        then:
        def expectedNamePrefix = setValues ? "some" : "default"
        assertThatDeclaredValuesAreSetProperly("a", expectedNamePrefix)
        assertThatDeclaredValuesAreSetProperly("b", expectedNamePrefix)

        where:
        extensionClassContent                       | name                                             | setDefaults | setValues
        withFileSystemLocationProperties            | "DirectoryProperty & RegularFileProperty"        | false       | true
        withFileSystemLocationProperties            | "DirectoryProperty & RegularFileProperty"        | true        | false
        withFileSystemLocationProperties            | "DirectoryProperty & RegularFileProperty"        | true        | true
        withPropertiesOfFileSystemLocations         | "Property<Directory> & Property<RegularFile>"    | false       | true
        withPropertiesOfFileSystemLocations         | "Property<Directory> & Property<RegularFile>"    | true        | false
        withPropertiesOfFileSystemLocations         | "Property<Directory> & Property<RegularFile>"    | true        | true
        withJavaBeanPropertiesOfFileSystemLocations | "Directory and RegularFile Java Bean properties" | false       | true
        withJavaBeanPropertiesOfFileSystemLocations | "Directory and RegularFile Java Bean properties" | true        | false
        withJavaBeanPropertiesOfFileSystemLocations | "Directory and RegularFile Java Bean properties" | true        | true
    }

    @SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy doesn't have the `listOf(...)` function")
    def 'set multi value: #name (set defaults: #setDefaults) (set values: #setValues)'() {
        given:
        withSoftwareTypePlugins(
            extensionClassContent,
            getProjectPluginThatRegistersItsOwnExtension(true, "extension", null),
            settingsPluginThatRegistersSoftwareType
        ).prepareToExecute()

        def defaultsConfig = """
            defaults {
                testSoftwareType {
                    dirs = listOf(layout.projectDirectory.dir("defaultDir1"), layout.projectDirectory.dir("defaultDir2"))
                    files = listOf(layout.settingsDirectory.file("defaultFile1"), layout.settingsDirectory.file("defaultFile2"))
                }
            }
        """.stripIndent()
        settingsFile() << getSettingsFileContent(setDefaults ? defaultsConfig : "")

        def softwareTypeConfig = """
            dirs = listOf(layout.projectDirectory.dir("someDir1"), layout.projectDirectory.dir("someDir2"))
            files = listOf(layout.settingsDirectory.file("someFile1"), layout.settingsDirectory.file("someFile2"))
        """.stripIndent()
        buildFileForProject("a") << getProjectFileContent(setValues ? softwareTypeConfig : "")
        buildFileForProject("b") << getProjectFileContent(setValues ? softwareTypeConfig : "")

        when:
        run("printTestSoftwareTypeExtensionConfiguration")

        then:
        def expectedNamePrefix = setValues ? "some" : "default"
        assertThatDeclaredListValuesAreSetProperly("a", expectedNamePrefix)
        assertThatDeclaredListValuesAreSetProperly("b", expectedNamePrefix)

        where:
        extensionClassContent                | name                                                  | setDefaults | setValues
        withFileSystemLocationListProperties | "ListProperty<Directory> & ListProperty<RegularFile>" | false       | true
        withFileSystemLocationListProperties | "ListProperty<Directory> & ListProperty<RegularFile>" | true        | false
        withFileSystemLocationListProperties | "ListProperty<Directory> & ListProperty<RegularFile>" | true        | true
    }

    @SkipDsl(dsl = GradleDsl.KOTLIN, because = "Test is specific to the Declarative DSL")
    @SkipDsl(dsl = GradleDsl.GROOVY, because = "Test is specific to the Declarative DSL")
    def "using a read-only property by mistake gives a helpful error message for #name (set defaults: #setDefaults)"() {
        given:
        withSoftwareTypePlugins(
            extensionClassContent,
            getProjectPluginThatRegistersItsOwnExtension(true, "extension", null),
            settingsPluginThatRegistersSoftwareType
        ).prepareToExecute()

        def defaultsConfig = """
            defaults {
                testSoftwareType {
                    dir = layout.projectDirectory.dir("defaultDir")
                    file = layout.settingsDirectory.file("defaultFile")
                }
            }
        """.stripIndent()
        settingsFile() << getSettingsFileContent(setDefaults ? defaultsConfig : "")

        def softwareTypeConfig = """
            dir = listOf(layout.projectDirectory.dir("someDir1"), layout.projectDirectory.dir("someDir2"))
            file = listOf(layout.settingsDirectory.file("someFile1"), layout.settingsDirectory.file("someFile2"))
        """.stripIndent()
        buildFileForProject("a") << getProjectFileContent(softwareTypeConfig)
        buildFileForProject("b") << ""

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        failureCauseContains("Failed to interpret the declarative DSL file '${(setDefaults ? settingsFile() : buildFileForProject("a")).path}':")
        failureCauseContains("Failures in resolution:")
        failureCauseContains("assignment to property '$propName' with read-only type '$name'")

        where:
        extensionClassContent           | name          | propName | setDefaults
        withReadOnlyDirectoryProperty   | "Directory"   | "dir"    | true
        withReadOnlyDirectoryProperty   | "Directory"   | "dir"    | false
        withReadOnlyRegularFileProperty | "RegularFile" | "file"   | true
        withReadOnlyRegularFileProperty | "RegularFile" | "file"   | false
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
            failure.assertHasDescription("ProjectLayout should be referenced only inside of software type default configuration blocks")
        } else if (GradleDsl.GROOVY == currentDsl()) {
            failure.assertHasCause("ProjectLayout should be referenced only inside of software type default configuration blocks")
        } else {
            throw new RuntimeException("Shouldn't happen")
        }
    }

    private static String getWithFileSystemLocationProperties() {
        """
            package org.gradle.test;

            import org.gradle.declarative.dsl.model.annotations.Restricted;

            import org.gradle.api.file.DirectoryProperty;
            import org.gradle.api.file.RegularFileProperty;

            @Restricted
            public abstract class TestSoftwareTypeExtension {
                @Restricted
                public abstract DirectoryProperty getDir();

                @Restricted
                public abstract RegularFileProperty getFile();

                @Override
                public String toString() {
                    return "dir = " + getDir().getOrNull() + ", file = " + getFile().getOrNull();
                }
            }
        """
    }

    private static String getWithFileSystemLocationListProperties() {
        """
            package org.gradle.test;

            import org.gradle.declarative.dsl.model.annotations.Restricted;

            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.file.Directory;
            import org.gradle.api.file.RegularFile;

            @Restricted
            public abstract class TestSoftwareTypeExtension {
                @Restricted
                public abstract ListProperty<Directory> getDirs();

                @Restricted
                public abstract ListProperty<RegularFile> getFiles();

                @Override
                public String toString() {
                    return "dirs = " + getDirs().getOrNull() + ", files = " + getFiles().getOrNull();
                }
            }
        """
    }

    private static String getWithReadOnlyDirectoryProperty() {
        """
            package org.gradle.test;

            import org.gradle.declarative.dsl.model.annotations.Restricted;

            import org.gradle.api.file.Directory;
            import org.gradle.api.file.RegularFileProperty;
            import org.gradle.api.provider.Property;

            @Restricted
            public interface TestSoftwareTypeExtension {
                @Restricted
                Directory getDir();

                @Restricted
                RegularFileProperty getFile();
            }
        """
    }

    private static String getWithReadOnlyRegularFileProperty() {
        """
            package org.gradle.test;

            import org.gradle.declarative.dsl.model.annotations.Restricted;

            import org.gradle.api.file.DirectoryProperty;
            import org.gradle.api.file.RegularFile;
            import org.gradle.api.provider.Property;

            @Restricted
            public interface TestSoftwareTypeExtension {
                @Restricted
                DirectoryProperty getDir();

                @Restricted
                RegularFile getFile();
            }
        """
    }

    private static String getWithPropertiesOfFileSystemLocations() {
        """
            package org.gradle.test;

            import org.gradle.declarative.dsl.model.annotations.Restricted;

            import org.gradle.api.file.Directory;
            import org.gradle.api.file.RegularFile;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.Property;

            import javax.inject.Inject;

            @Restricted
            public abstract class TestSoftwareTypeExtension {

                private final Property<Directory> dir;
                private final Property<RegularFile> file;

                @Inject
                public TestSoftwareTypeExtension(ObjectFactory objects) {
                    dir = objects.directoryProperty();
                    file = objects.fileProperty();
                }

                @Restricted
                public Property<Directory> getDir() {
                    return dir;
                }

                @Restricted
                public Property<RegularFile> getFile() {
                    return file;
                }

                @Override
                public String toString() {
                    return "dir = " + getDir().getOrNull() + ", file = " + getFile().getOrNull();
                }
            }
        """
    }

    private static String getWithJavaBeanPropertiesOfFileSystemLocations() {
        """
            package org.gradle.test;

            import org.gradle.declarative.dsl.model.annotations.Restricted;

            import org.gradle.api.file.Directory;
            import org.gradle.api.file.RegularFile;

            import javax.inject.Inject;

            @Restricted
            public abstract class TestSoftwareTypeExtension {

                private Directory dir;
                private RegularFile file;

                @Inject
                public TestSoftwareTypeExtension() {
                }

                @Restricted
                public Directory getDir() {
                    return dir;
                }

                @Restricted
                public void setDir(Directory dir) {
                    this.dir = dir;
                }

                @Restricted
                public RegularFile getFile() {
                    return file;
                }

                @Restricted
                public void setFile(RegularFile file) {
                    this.file = file;
                }

                @Override
                public String toString() {
                    return "dir = " + dir + ", file = " + file;
                }
            }
        """
    }

    private static String getSettingsFileContent(String defaultsConfig) {
        return """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-type")
            }
            include("a")
            include("b")
            $defaultsConfig
        """
    }

    private static String getProjectFileContent(String softwareTypeConfig) {
        return """
            testSoftwareType {
                $softwareTypeConfig
            }
        """.stripIndent()
    }

    private void assertThatDeclaredValuesAreSetProperly(String project, String namePrefix) {
        def dirPrefix = testDirectory.file("$project/${namePrefix}").path
        def filePrefix = testDirectory.file("${namePrefix}").path
        outputContains("$project: dir = ${dirPrefix}Dir, file = ${filePrefix}File")
    }

    private void assertThatDeclaredListValuesAreSetProperly(String project, String namePrefix) {
        def dirPrefix = testDirectory.file("$project/${namePrefix}").path
        def filePrefix = testDirectory.file("${namePrefix}").path
        outputContains("$project: dirs = [${dirPrefix}Dir1, ${dirPrefix}Dir2], files = [${filePrefix}File1, ${filePrefix}File2]")
    }
}
