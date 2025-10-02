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

import org.gradle.api.internal.plugins.BuildModel
import org.gradle.api.internal.plugins.Definition
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.internal.declarativedsl.settings.ProjectTypeFixture
import org.gradle.test.fixtures.dsl.GradleDsl

@PolyglotDslTest
class WorkingWithFilesIntegrationTest extends AbstractIntegrationSpec implements ProjectTypeFixture, PolyglotTestFixture {

    def setup() {
        file("gradle.properties") << """
            org.gradle.kotlin.dsl.dcl=true
        """
    }

    def 'set single value: #name (set defaults: #setDefaults) (set values: #setValues)'() {
        given:
        def projectType = new ProjectTypePluginClassBuilder().withoutConventions()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        withProjectTypePlugins(
            definition as ProjectTypeDefinitionClassBuilder,
            projectType,
            settingsBuilder
        ).prepareToExecute()

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

        when:
        run("printTestProjectTypeDefinitionConfiguration")

        then:
        def expectedNamePrefix = setValues ? "some" : "default"
        assertThatDeclaredValuesAreSetProperly("a", expectedNamePrefix)
        assertThatDeclaredValuesAreSetProperly("b", expectedNamePrefix)

        where:
        definition                                                  | name                                             | setDefaults | setValues
        new DefinitionWithFileSystemLocationProperties()            | "DirectoryProperty & RegularFileProperty"        | false       | true
        new DefinitionWithFileSystemLocationProperties()            | "DirectoryProperty & RegularFileProperty"        | true        | false
        new DefinitionWithFileSystemLocationProperties()            | "DirectoryProperty & RegularFileProperty"        | true        | true
        new DefinitionWithPropertiesOfFileSystemLocations()         | "Property<Directory> & Property<RegularFile>"    | false       | true
        new DefinitionWithPropertiesOfFileSystemLocations()         | "Property<Directory> & Property<RegularFile>"    | true        | false
        new DefinitionWithPropertiesOfFileSystemLocations()         | "Property<Directory> & Property<RegularFile>"    | true        | true
        new DefinitionWithJavaBeanPropertiesOfFileSystemLocations() | "Directory and RegularFile Java Bean properties" | false       | true
        new DefinitionWithJavaBeanPropertiesOfFileSystemLocations() | "Directory and RegularFile Java Bean properties" | true        | false
        new DefinitionWithJavaBeanPropertiesOfFileSystemLocations() | "Directory and RegularFile Java Bean properties" | true        | true
    }

    @SkipDsl(dsl = GradleDsl.GROOVY, because = "Groovy doesn't have the `listOf(...)` function")
    def 'set multi value: #name (set defaults: #setDefaults) (set values: #setValues)'() {
        given:
        def projectType = new ProjectTypePluginClassBuilder().withoutConventions()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        withProjectTypePlugins(
            definition as ProjectTypeDefinitionClassBuilder,
            projectType,
            settingsBuilder
        ).prepareToExecute()

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

        when:
        run("printTestProjectTypeDefinitionConfiguration")

        then:
        def expectedNamePrefix = setValues ? "some" : "default"
        assertThatDeclaredListValuesAreSetProperly("a", expectedNamePrefix)
        assertThatDeclaredListValuesAreSetProperly("b", expectedNamePrefix)

        where:
        definition                                           | name                                                  | setDefaults | setValues
        new DefinitionWithFileSystemLocationListProperties() | "ListProperty<Directory> & ListProperty<RegularFile>" | false       | true
        new DefinitionWithFileSystemLocationListProperties() | "ListProperty<Directory> & ListProperty<RegularFile>" | true        | false
        new DefinitionWithFileSystemLocationListProperties() | "ListProperty<Directory> & ListProperty<RegularFile>" | true        | true
    }

    @SkipDsl(dsl = GradleDsl.KOTLIN, because = "Test is specific to the Declarative DSL")
    @SkipDsl(dsl = GradleDsl.GROOVY, because = "Test is specific to the Declarative DSL")
    def "using a read-only property by mistake gives a helpful error message for #name (set defaults: #setDefaults)"() {
        given:
        def projectType = new ProjectTypePluginClassBuilder().withoutConventions()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        withProjectTypePlugins(
            definition as ProjectTypeDefinitionClassBuilder,
            projectType,
            settingsBuilder
        ).prepareToExecute()

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
        definition                                      | name          | propName | setDefaults
        new DefinitionWithReadOnlyProperties()          | "Directory"   | "dir"    | true
        new DefinitionWithReadOnlyProperties()          | "Directory"   | "dir"    | false
        new DefinitionWithReadOnlyRegularFileProperty() | "RegularFile" | "file"   | true
        new DefinitionWithReadOnlyRegularFileProperty() | "RegularFile" | "file"   | false
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

    private static class DefinitionWithFileSystemLocationProperties extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.file.DirectoryProperty;
                import org.gradle.api.file.RegularFileProperty;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public abstract class ${implementationTypeClassName} implements ${Definition.class.simpleName}<${implementationTypeClassName}.ModelType> {
                    @Restricted
                    public abstract DirectoryProperty getDir();

                    @Restricted
                    public abstract RegularFileProperty getFile();

                    @Override
                    public String toString() {
                        return "dir = " + getDir().getOrNull() + ", file = " + getFile().getOrNull();
                    }

                    public interface ModelType extends BuildModel {
                        DirectoryProperty getDir();
                        RegularFileProperty getFile();
                    }
                }
            """
        }
    }

    private static class DefinitionWithFileSystemLocationListProperties extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.provider.ListProperty;
                import org.gradle.api.file.Directory;
                import org.gradle.api.file.RegularFile;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public abstract class ${implementationTypeClassName} implements ${Definition.class.simpleName}<${implementationTypeClassName}.ModelType> {
                    @Restricted
                    public abstract ListProperty<Directory> getDirs();

                    @Restricted
                    public abstract ListProperty<RegularFile> getFiles();

                    @Override
                    public String toString() {
                        return "dirs = " + getDirs().getOrNull() + ", files = " + getFiles().getOrNull();
                    }

                    public interface ModelType extends BuildModel {
                        ListProperty<Directory> getDirs();
                        ListProperty<RegularFile> getFiles();
                    }
                }
            """
        }
    }

    private static class DefinitionWithReadOnlyProperties extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.file.Directory;
                import org.gradle.api.file.RegularFileProperty;
                import org.gradle.api.provider.Property;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public interface ${implementationTypeClassName} extends ${Definition.class.simpleName}<${implementationTypeClassName}.ModelType> {
                    @Restricted
                    Directory getDir();

                    @Restricted
                    RegularFileProperty getFile();

                    public interface ModelType extends BuildModel {
                        Directory getDir();
                        RegularFileProperty getFile();
                    }
                }
            """
        }
    }

    private static class DefinitionWithReadOnlyRegularFileProperty extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.file.DirectoryProperty;
                import org.gradle.api.file.RegularFile;
                import org.gradle.api.provider.Property;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public interface ${implementationTypeClassName} extends ${Definition.class.simpleName}<${implementationTypeClassName}.ModelType> {
                    @Restricted
                    DirectoryProperty getDir();

                    @Restricted
                    RegularFile getFile();

                    public interface ModelType extends BuildModel {
                        DirectoryProperty getDir();
                        RegularFile getFile();
                    }
                }
            """
        }
    }

    private static class DefinitionWithPropertiesOfFileSystemLocations extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.file.Directory;
                import org.gradle.api.file.RegularFile;
                import org.gradle.api.file.DirectoryProperty;
                import org.gradle.api.file.RegularFileProperty;
                import org.gradle.api.model.ObjectFactory;
                import org.gradle.api.provider.Property;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                import javax.inject.Inject;

                @Restricted
                public abstract class ${implementationTypeClassName} implements ${Definition.class.simpleName}<${implementationTypeClassName}.ModelType> {

                    private final Property<Directory> dir;
                    private final Property<RegularFile> file;

                    @Inject
                    public ${implementationTypeClassName}(ObjectFactory objects) {
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

                    public interface ModelType extends BuildModel {
                        DirectoryProperty getDir();
                        RegularFileProperty getFile();
                    }
                }
            """
        }
    }

    private static class DefinitionWithJavaBeanPropertiesOfFileSystemLocations extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.file.Directory;
                import org.gradle.api.file.RegularFile;
                import org.gradle.api.file.DirectoryProperty;
                import org.gradle.api.file.RegularFileProperty;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                import javax.inject.Inject;

                @Restricted
                public abstract class ${implementationTypeClassName} implements ${Definition.class.simpleName}<${implementationTypeClassName}.ModelType> {

                    private Directory dir;
                    private RegularFile file;

                    @Inject
                    public ${implementationTypeClassName}() {
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

                    public interface ModelType extends BuildModel {
                        DirectoryProperty getDir();
                        RegularFileProperty getFile();
                    }
                }
            """
        }
    }

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
