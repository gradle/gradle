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
import org.spockframework.runtime.extension.builtin.PreconditionContext
import spock.lang.IgnoreIf

@PolyglotDslTest
class WorkingWithFilesIntegrationTest extends AbstractIntegrationSpec implements SoftwareTypeFixture, PolyglotTestFixture {

    def setup() {
        file("gradle.properties") << """
            org.gradle.kotlin.dsl.dcl=true
        """
    }

    @IgnoreIf(
        value = { PreconditionContext it -> it.data.name == "Directory and RegularFile Java Bean properties" && it.data.setDefaults },
        reason = """
            e: file://.../settings.gradle.kts:13:21: Function invocation 'dir(...)' expected
            e: file://.../settings.gradle.kts:13:21: Unresolved reference. None of the following candidates is applicable because of receiver type mismatch:
                public inline fun SourceSetOutput.dir(dir: Any, vararg options: Pair<String, Any?>): Unit defined in org.gradle.kotlin.dsl
            e: file://.../settings.gradle.kts:14:21: Function invocation 'file(...)' expected
            e: file://.../settings.gradle.kts:14:21: None of the following functions can be called with the arguments supplied:
                public open fun file(path: Any): File defined in Settings_gradle
                public open fun file(path: Any, validation: PathValidation): File defined in Settings_gradle
        """ // TODO: this happens only in KTS and only in settings/defaults; need to clarify if this is ok or needs fixing
        // TODO: I think that inside the `defaults` block we should prohibit access to random Settings crap
    )
    def 'set #name (set defaults: #setDefaults) (set values: #setValues)'() {
        given:
        withSoftwareTypePlugins(
            extensionClassContent,
            getProjectPluginThatRegistersItsOwnExtension(true, "extension", null),
            settingsPluginThatRegistersSoftwareType
        ).prepareToExecute()

        settingsFile() << getPluginsFromIncludedBuild(setDefaults)

        buildFileForProject("a") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(setValues)
        buildFileForProject("b") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(setValues)

        when:
        run("printTestSoftwareTypeExtensionConfiguration")


        then:
        def expectedNamePrefix = setValues ? "some" : "default"
        assertThatDeclaredValuesAreSetProperly("a", expectedNamePrefix)
        assertThatDeclaredValuesAreSetProperly("b", expectedNamePrefix)

        where:
        extensionClassContent                           | name                                                  | setDefaults | setValues
        withFileSystemLocationProperties                | "DirectoryProperty & RegularFileProperty"             | false       | true
        withFileSystemLocationProperties                | "DirectoryProperty & RegularFileProperty"             | true        | false
        withFileSystemLocationProperties                | "DirectoryProperty & RegularFileProperty"             | true        | true
        withPropertiesOfFileSystemLocations             | "Property<Directory> & Property<RegularFile>"         | false       | true
        withPropertiesOfFileSystemLocations             | "Property<Directory> & Property<RegularFile>"         | true        | false
        withPropertiesOfFileSystemLocations             | "Property<Directory> & Property<RegularFile>"         | true        | true
        withJavaBeanPropertiesOfFileSystemLocations     | "Directory and RegularFile Java Bean properties"      | false       | true
        withJavaBeanPropertiesOfFileSystemLocations     | "Directory and RegularFile Java Bean properties"      | true        | false
        withJavaBeanPropertiesOfFileSystemLocations     | "Directory and RegularFile Java Bean properties"      | true        | true
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

        settingsFile() << getPluginsFromIncludedBuild(setDefaults)

        buildFileForProject("a") << getDeclarativeScriptThatConfiguresOnlyTestSoftwareType(true)
        buildFileForProject("b") << ""

        when:
        fails(":printTestSoftwareTypeExtensionConfiguration")

        then:
        failureCauseContains("Failed to interpret the declarative DSL file '${(setDefaults ? settingsFile() : buildFileForProject("a")).path}':")
        failureCauseContains("Failures in resolution:")
        failureCauseContains("assignment to property '$propName' with read-only type '$name'")

        where:
        extensionClassContent                           | name              | propName  | setDefaults
        withReadOnlyDirectoryProperty                   | "Directory"       | "dir"     | true
        withReadOnlyDirectoryProperty                   | "Directory"       | "dir"     | false
        withReadOnlyRegularFileProperty                 | "RegularFile"     | "file"    | true
        withReadOnlyRegularFileProperty                 | "RegularFile"     | "file"    | false
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

    private static String getPluginsFromIncludedBuild(boolean setDefaults) {
        def defaultsBlock = setDefaults ? """
            defaults {
                testSoftwareType {
                    dir = layout.projectDirectory.dir("defaultDir")
                    file = layout.settingsDirectory.file("defaultFile")
                }
            }
        """ : ""
        return """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-type")
            }
            include("a")
            include("b")
            $defaultsBlock
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
