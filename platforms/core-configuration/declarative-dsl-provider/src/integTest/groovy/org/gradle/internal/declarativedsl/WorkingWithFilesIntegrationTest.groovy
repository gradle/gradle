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
        def projectType = new ProjectTypePluginClassBuilder(definition as ProjectTypeDefinitionClassBuilder).withoutConventions().withUnsafeDefinition()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        withProjectType(
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
        def projectType = new ProjectTypePluginClassBuilder(definition as ProjectTypeDefinitionClassBuilder).withoutConventions().withUnsafeDefinition()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        withProjectType(
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
        def projectType = new ProjectTypePluginClassBuilder(definition as ProjectTypeDefinitionClassBuilder).withoutConventions()
        def settingsBuilder = new SettingsPluginClassBuilder()
            .registersProjectType(projectType.projectTypePluginClassName)

        withProjectType(
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
        String getPublicTypeClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.file.DirectoryProperty;
                import org.gradle.api.file.RegularFileProperty;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public abstract class ${publicTypeClassName} implements ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {
                    @Restricted
                    public abstract DirectoryProperty getDir();

                    @Restricted
                    public abstract RegularFileProperty getFile();

                    public interface ModelType extends BuildModel {
                        DirectoryProperty getDir();
                        RegularFileProperty getFile();
                        default public String propertyValues() {
                            return "modelDir = " + getDir().getOrNull() + ", modelFile = " + getFile().getOrNull();
                        }
                    }
                }
            """
        }

        @Override
        String getBuildModelMapping() {
            return """
                model.getDir().set(definition.getDir());
                model.getFile().set(definition.getFile());
            """
        }

        @Override
        String displayDefinitionPropertyValues() {
            return """
                ${displayProperty("definition", "dir", "definition.getDir().getAsFile().getOrNull()")}
                ${displayProperty("definition", "file", "definition.getFile().getAsFile().getOrNull()")}
            """
        }

        @Override
        String displayModelPropertyValues() {
            return """
                ${displayProperty("model", "dir", "model.getDir().getAsFile().getOrNull()")}
                ${displayProperty("model", "file", "model.getFile().getAsFile().getOrNull()")}
            """
        }
    }

    private static class DefinitionWithFileSystemLocationListProperties extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getPublicTypeClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.provider.ListProperty;
                import org.gradle.api.file.Directory;
                import org.gradle.api.file.RegularFile;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public abstract class ${publicTypeClassName} implements ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {
                    @Restricted
                    public abstract ListProperty<Directory> getDirs();

                    @Restricted
                    public abstract ListProperty<RegularFile> getFiles();

                    public interface ModelType extends BuildModel {
                        ListProperty<Directory> getDirs();
                        ListProperty<RegularFile> getFiles();
                    }
                }
            """
        }

        @Override
        String getBuildModelMapping() {
            return """
                model.getDirs().set(definition.getDirs());
                model.getFiles().set(definition.getFiles());
            """
        }

        @Override
        String displayDefinitionPropertyValues() {
            return """
                ${displayProperty("definition", "dirs", "definition.getDirs().getOrNull()")}
                ${displayProperty("definition", "files", "definition.getFiles().getOrNull()")}
            """
        }

        @Override
        String displayModelPropertyValues() {
            return """
                ${displayProperty("model", "dirs", "model.getDirs().getOrNull()")}
                ${displayProperty("model", "files", "model.getFiles().getOrNull()")}
            """
        }
    }

    private static class DefinitionWithReadOnlyProperties extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getPublicTypeClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.file.Directory;
                import org.gradle.api.file.RegularFileProperty;
                import org.gradle.api.file.DirectoryProperty;
                import org.gradle.api.provider.Property;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public interface ${publicTypeClassName} extends ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {
                    @Restricted
                    Directory getDir();

                    @Restricted
                    RegularFileProperty getFile();

                    public interface ModelType extends BuildModel {
                        DirectoryProperty getDir();
                        RegularFileProperty getFile();
                    }
                }
            """
        }

        @Override
        String getBuildModelMapping() {
            return """
                model.getDir().set(definition.getDir());
                model.getFile().set(definition.getFile());
            """
        }

        @Override
        String displayDefinitionPropertyValues() {
            return """
                ${displayProperty("definition", "dir", "definition.getDir().getAsFile()")}
                ${displayProperty("definition", "file", "definition.getFile().getAsFile().getOrNull()")}
            """
        }

        @Override
        String displayModelPropertyValues() {
            return """
                ${displayProperty("model", "dir", "model.getDir().getAsFile().getOrNull()")}
                ${displayProperty("model", "file", "model.getFile().getAsFile().getOrNull()")}
            """
        }
    }

    private static class DefinitionWithReadOnlyRegularFileProperty extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getPublicTypeClassContent() {
            return """
                package org.gradle.test;

                import org.gradle.declarative.dsl.model.annotations.Restricted;

                import org.gradle.api.file.RegularFileProperty;
                import org.gradle.api.file.DirectoryProperty;
                import org.gradle.api.file.RegularFile;
                import org.gradle.api.provider.Property;
                import ${Definition.class.name};
                import ${BuildModel.class.name};

                @Restricted
                public interface ${publicTypeClassName} extends ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {
                    @Restricted
                    DirectoryProperty getDir();

                    @Restricted
                    RegularFile getFile();

                    public interface ModelType extends BuildModel {
                        DirectoryProperty getDir();
                        RegularFileProperty getFile();
                    }
                }
            """
        }

        @Override
        String getBuildModelMapping() {
            return """
                model.getDir().set(definition.getDir());
                model.getFile().set(definition.getFile());
            """
        }

        @Override
        String displayDefinitionPropertyValues() {
            return """
                ${displayProperty("definition", "dir", "definition.getDir().getAsFile().getOrNull()")}
                ${displayProperty("definition", "file", "definition.getFile().getAsFile()")}
            """
        }

        @Override
        String displayModelPropertyValues() {
            return """
                ${displayProperty("model", "dir", "model.getDir().getAsFile().getOrNull()")}
                ${displayProperty("model", "file", "model.getFile().getAsFile().getOrNull()")}
            """
        }
    }

    private static class DefinitionWithPropertiesOfFileSystemLocations extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getPublicTypeClassContent() {
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
                public abstract class ${publicTypeClassName} implements ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {

                    private final Property<Directory> dir;
                    private final Property<RegularFile> file;

                    @Inject
                    public ${publicTypeClassName}(ObjectFactory objects) {
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

                    public interface ModelType extends BuildModel {
                        DirectoryProperty getDir();
                        RegularFileProperty getFile();
                        default public String propertyValues() {
                            return "modelDir = " + getDir().getOrNull() + ", modelFile = " + getFile().getOrNull();
                        }
                    }
                }
            """
        }

        @Override
        String getBuildModelMapping() {
            return """
                model.getDir().set(definition.getDir());
                model.getFile().set(definition.getFile());
            """
        }

        @Override
        String displayDefinitionPropertyValues() {
            return """
                ${displayProperty("definition", "dir", "definition.getDir().get().getAsFile()")}
                ${displayProperty("definition", "file", "definition.getFile().get().getAsFile()")}
            """
        }

        @Override
        String displayModelPropertyValues() {
            return """
                ${displayProperty("model", "dir", "model.getDir().get().getAsFile()")}
                ${displayProperty("model", "file", "model.getFile().get().getAsFile()")}
            """
        }
    }

    private static class DefinitionWithJavaBeanPropertiesOfFileSystemLocations extends ProjectTypeDefinitionClassBuilder {
        @Override
        String getPublicTypeClassContent() {
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
                public abstract class ${publicTypeClassName} implements ${Definition.class.simpleName}<${publicTypeClassName}.ModelType> {

                    private Directory dir;
                    private RegularFile file;

                    @Inject
                    public ${publicTypeClassName}() {
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

                    public String propertyValues() {
                        return "dir = " + dir + ", file = " + file;
                    }

                    public interface ModelType extends BuildModel {
                        DirectoryProperty getDir();
                        RegularFileProperty getFile();
                        default public String propertyValues() {
                            return "modelDir = " + getDir().getOrNull() + ", modelFile = " + getFile().getOrNull();
                        }
                    }
                }
            """
        }

        @Override
        String getBuildModelMapping() {
            return """
                model.getDir().set(definition.getDir());
                model.getFile().set(definition.getFile());
            """
        }

        @Override
        String displayDefinitionPropertyValues() {
            return """
                ${displayProperty("definition", "dir", "definition.getDir().getAsFile()")}
                ${displayProperty("definition", "file", "definition.getFile().getAsFile()")}
            """
        }

        @Override
        String displayModelPropertyValues() {
            return """
                ${displayProperty("model", "dir", "model.getDir().getAsFile().getOrNull()")}
                ${displayProperty("model", "file", "model.getFile().getAsFile().getOrNull()")}
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
        outputContains("$project: definition dir = ${dirPrefix}Dir")
        outputContains("$project: definition file = ${filePrefix}File")
    }

    private void assertThatDeclaredListValuesAreSetProperly(String project, String namePrefix) {
        def dirPrefix = testDirectory.file("$project/${namePrefix}").path
        def filePrefix = testDirectory.file("${namePrefix}").path
        outputContains("$project: definition dirs = [${dirPrefix}Dir1, ${dirPrefix}Dir2]")
        outputContains("$project: definition files = [${filePrefix}File1, ${filePrefix}File2]")
    }
}
