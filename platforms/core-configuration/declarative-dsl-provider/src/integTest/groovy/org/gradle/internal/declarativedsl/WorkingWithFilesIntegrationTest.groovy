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
import org.gradle.internal.declarativedsl.settings.SoftwareTypeFixture

@PolyglotDslTest
class WorkingWithFilesIntegrationTest extends AbstractIntegrationSpec implements SoftwareTypeFixture, PolyglotTestFixture {

    def setup() {
        file("gradle.properties") << """
            org.gradle.kotlin.dsl.dcl=true
        """
    }

    def 'set #name (overwrite defaults: #overwriteDefaults)'() {
        given:
        withSoftwareTypePlugins(
            extensionClassContent,
            getProjectPluginThatRegistersItsOwnExtension(true, "extension", null),
            settingsPluginThatRegistersSoftwareType
        ).prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFileForProject("a") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(overwriteDefaults)
        buildFileForProject("b") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(overwriteDefaults)

        when:
        run("printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly("a", expectedNamePrefix)
        assertThatDeclaredValuesAreSetProperly("b", expectedNamePrefix)

        where:
        extensionClassContent                           | name                                                  | overwriteDefaults     | expectedNamePrefix
        withFileSystemLocationProperties                | "DirectoryProperty & RegularFileProperty"             | true                  | "some"
        withFileSystemLocationProperties                | "DirectoryProperty & RegularFileProperty"             | false                 | "default"
        withPropertiesOfFileSystemLocations             | "Property<Directory> & Property<RegularFile>"         | true                  | "some"
        withPropertiesOfFileSystemLocations             | "Property<Directory> & Property<RegularFile>"         | false                 | "default"
        withJavaBeanPropertiesOfFileSystemLocations     | "Directory and RegularFile Java Bean properties"      | true                  | "some"
        withJavaBeanPropertiesOfFileSystemLocations     | "Directory and RegularFile Java Bean properties"      | false                 | "default"
    }

    def "using a read-only property by mistake gives a helpful error message for #name"() {
        given:
        withSoftwareTypePlugins(
            extensionClassContent,
            getProjectPluginThatRegistersItsOwnExtension(true, "extension", null),
            settingsPluginThatRegistersSoftwareType
        ).prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("build.gradle.dcl") << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        failureCauseContains("Failed to interpret the declarative DSL file '${file("build.gradle.dcl").path}':")
        failureCauseContains("Failures in resolution:")
        failureCauseContains("assignment to property '$propName' with read-only type '$name'")

        where:
        extensionClassContent                           | name              | propName
        withReadOnlyDirectoryProperty                   | "Directory"       | "dir"
        withReadOnlyRegularFileProperty                 | "RegularFile"     | "file"
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
                        return (getDir().isPresent() ? "dir = " + getDir().getOrNull() + "\\n" : "") +
                            (getFile().isPresent() ? "file = " + getFile().getOrNull() + "\\n" : "");
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
                        return (dir.isPresent() ? "dir = " + dir.getOrNull() + "\\n" : "") +
                            (file.isPresent() ? "file = " + file.getOrNull() + "\\n" : "");
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
                abstract class TestSoftwareTypeExtension {

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
                        return "dir = " + dir + "\\nfile = " + file;
                    }
                }
            """
    }

    private static String getPluginsFromIncludedBuild() {
        return """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-type")
            }
            include("a")
            include("b")
            defaults {
                testSoftwareType {
                    dir = layout.projectDirectory.dir("defaultDir")
                    file = layout.settingsDirectory.file("defaultFile")
                }
            }
        """
    }

    private static String getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(boolean overwriteDefaults) {
        if (overwriteDefaults) {
            return """
                testSoftwareType {
                    dir = layout.projectDirectory.dir("someDir")
                    file = layout.settingsDirectory.file("someFile")
                }
            """.stripIndent()
        } else {
            return """
                testSoftwareType {
                }
            """.stripIndent()
        }
    }

    private void assertThatDeclaredValuesAreSetProperly(String project, String namePrefix) {
        outputContains("$project:\ndir = ${testDirectory.file("$project/${namePrefix}Dir").path}\nfile = ${testDirectory.file("${namePrefix}File").path}")
    }
}
