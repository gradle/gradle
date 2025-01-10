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
import org.gradle.internal.declarativedsl.settings.SoftwareTypeFixture
import spock.lang.Ignore

class WorkingWithFilesIntegrationTest extends AbstractIntegrationSpec implements SoftwareTypeFixture {

    def 'can set DirectoryProperty and RegularFileProperty'() {
        given:
        withSoftwareTypePlugins(
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
            """,
            getProjectPluginThatRegistersItsOwnExtension(true, "extension", null),
            settingsPluginThatRegistersSoftwareType
        ).prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("build.gradle.dcl") << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
    }

    @Ignore("Please use the ObjectFactory.fileProperty() method to create a property of type RegularFile.") // TODO
    def 'can set Property<Directory> and Property<RegularFile> - abstract getter'() {  // TODO: rename/remove?
        given:
        withSoftwareTypePlugins(
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
                    @Restricted
                    public abstract Property<Directory> getDir();

                    @Restricted
                    public abstract Property<RegularFile> getFile();

                    @Override
                    public String toString() {
                        return (getDir().isPresent() ? "dir = " + getDir().getOrNull() + "\\\\n" : "") +
                            (getFile().isPresent() ? "file = " + getFile().getOrNull() + "\\\\n" : "");
                    }
                }
            """,
            getProjectPluginThatRegistersItsOwnExtension(true, "extension", null),
            settingsPluginThatRegistersSoftwareType
        ).prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("build.gradle.dcl") << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
    }

    @Ignore("annotation type not applicable to this kind of declaration (@Restricted)") // TODO
    def 'can set Property<Directory> and Property<RegularFile> - explicit property creation'() {  // TODO: rename/remove?
        given:
        withSoftwareTypePlugins(
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
                    @Restricted
                    public Property<Directory> dir;

                    @Restricted
                    public Property<RegularFile> file;

                    @Inject
                    public TestSoftwareTypeExtension(ObjectFactory objects) {
                        dir = objects.directoryProperty();
                        file = objects.fileProperty();
                    }

                    @Override
                    public String toString() {
                        return (dir.isPresent() ? "dir = " + dir.getOrNull() + "\\\\n" : "") +
                            (file.isPresent() ? "file = " + file.getOrNull() + "\\\\n" : "");
                    }
                }
            """,
            getProjectPluginThatRegistersItsOwnExtension(true, "extension", null),
            settingsPluginThatRegistersSoftwareType
        ).prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("build.gradle.dcl") << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
    }

    def 'can set Directory and RegularFile Java Bean properties'() {
        given:
        withSoftwareTypePlugins(
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
            """,
            getProjectPluginThatRegistersItsOwnExtension(true, "extension", null),
            settingsPluginThatRegistersSoftwareType
        ).prepareToExecute()

        file("settings.gradle.dcl") << pluginsFromIncludedBuild

        file("build.gradle.dcl") << declarativeScriptThatConfiguresOnlyTestSoftwareType

        when:
        run(":printTestSoftwareTypeExtensionConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()
    }

    static String getPluginsFromIncludedBuild() {
        return """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-type")
            }
        """
    }

    static String getDeclarativeScriptThatConfiguresOnlyTestSoftwareType() {
        return """
            testSoftwareType {
                dir = layout.projectDirectory.dir("someDir")
                file = layout.settingsDirectory.file("someFile")
            }
        """
    }

    void assertThatDeclaredValuesAreSetProperly() {
        outputContains("dir = ${testDirectory.file("someDir").path}\nfile = ${testDirectory.file("someFile").path}")
    }

}
