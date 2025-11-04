/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.util.internal.ToBeImplemented

/**
 * Tests edge cases of buildscript configuration resolution.
 *
 * <p>Tests should cover cases of initscript, settings, standalone, and project buildscript configurations.</p>
 */
class BuildscriptResolutionIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
    }

    def "buildscript classpath dependency on root project fails to resolve"() {
        file("foo.txt") << "foo"
        buildFile << """
            buildscript {
                configurations {
                    classpath {
                        outgoing {
                            artifact file('foo.txt')
                        }
                    }
                }

                dependencies {
                    classpath project(":")
                }
            }

            configurations {
                consumable("default")
            }

            assert buildscript.configurations.classpath.incoming.files*.name == ["foo.txt"]
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Could not resolve all dependencies for configuration 'classpath'.")
        failure.assertHasErrorOutput("No variants exist")
    }

    def "cannot declare project dependencies using path notation in buildscript block"() {
        buildFile << """
            buildscript {
                dependencies {
                    classpath(project(path: ":bar"))
                }
            }
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Project dependencies cannot be declared here.")
    }

    // This is not desired behavior.
    def "project buildscript classpath configuration can select another project"() {
        settingsFile << """
            include "first"
            include "other"
        """
        file("first/build.gradle") << """
            buildscript {
                dependencies {
                    classpath project(":other")
                }
            }
        """
        file("other/bar.txt") << "bar"
        file("other/build.gradle") << """
            configurations {
                other {
                    outgoing {
                        artifact file('bar.txt')
                    }
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "library"))
                    }
                }
            }
        """

        expect:
        succeeds("help")
    }

    def "project buildscript classpath configuration cannot select another project when the selected artifact is built by a task"() {
        settingsFile << """
            include "first"
            include "other"
        """
        file("first/build.gradle") << """
            buildscript {
                dependencies {
                    classpath project(":other")
                }
            }
        """
        file("bar.txt") << "bar"
        file("other/build.gradle") << """
            task myTask { }
            configurations {
                other {
                    outgoing {
                        artifact(file('bar.txt')) {
                            builtBy tasks.myTask
                        }
                    }
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "library"))
                    }
                }
            }
        """

        expect:
        fails("help")
        failure.assertHasCause("Script classpath dependencies must reside in a separate build from the script itself.")
    }

    def "project buildscript classpath configuration has unspecified identity"() {
        buildFile << """
            task resolve {
                def rootComponent = buildscript.configurations.classpath.incoming.resolutionResult.rootComponent
                doLast {
                    def root = rootComponent.get()
                    assert root.moduleVersion.group == "unspecified"
                    assert root.moduleVersion.name == "unspecified"
                    assert root.moduleVersion.version == "unspecified"
                    assert root.id instanceof RootComponentIdentifier
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    def "settings buildscript classpath configuration has unspecified identity"() {
        settingsFile << """
            def rootComponent = buildscript.configurations.classpath.incoming.resolutionResult.rootComponent
            def root = rootComponent.get()
            assert root.moduleVersion.group == "unspecified"
            assert root.moduleVersion.name == "unspecified"
            assert root.moduleVersion.version == "unspecified"
            assert root.id instanceof RootComponentIdentifier
        """

        expect:
        succeeds("help")
    }

    def "init buildscript classpath configuration has unspecified identity"() {
        initScriptFile << """
            def rootComponent = buildscript.configurations.classpath.incoming.resolutionResult.rootComponent
            def root = rootComponent.get()
            assert root.moduleVersion.group == "unspecified"
            assert root.moduleVersion.name == "unspecified"
            assert root.moduleVersion.version == "unspecified"
            assert root.id instanceof RootComponentIdentifier
        """

        when:
        executer.usingInitScript(initScriptFile)

        then:
        succeeds("help")
    }

    def "standalone buildscript classpath configuration has unspecified identity"() {
        file("foo.gradle") << """
            def rootComponent = buildscript.configurations.classpath.incoming.resolutionResult.rootComponent
            def root = rootComponent.get()
            assert root.moduleVersion.group == "unspecified"
            assert root.moduleVersion.name == "unspecified"
            assert root.moduleVersion.version == "unspecified"
            assert root.id instanceof RootComponentIdentifier
        """

        buildFile << """
            apply from: "foo.gradle"
        """

        expect:
        succeeds("help")
    }

    def "Adding configuration to project buildscript is forbidden"() {
        buildFile << """
            buildscript {
                configurations {
                    newconf
                }
            }
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Cannot mutate configuration container for buildscript of root project 'root' using create(String). Configurations cannot be added or removed from the buildscript configuration container.")
    }

    def "Adding configuration to settings buildscript is forbidden"() {
        settingsFile << """
            buildscript {
                configurations {
                    newconf
                }
            }
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Cannot mutate configuration container for settings file 'settings.gradle' using create(String). Configurations cannot be added or removed from the buildscript configuration container.")
    }

    def "Adding configuration to init buildscript is forbidden"() {
        initScriptFile << """
            buildscript {
                configurations {
                    newconf
                }
            }
        """

        when:
        executer.usingInitScript(initScriptFile)
        fails("help")

        then:
        failure.assertHasCause("Cannot mutate configuration container for initialization script 'init.gradle' using create(String). Configurations cannot be added or removed from the buildscript configuration container.")
    }

    def "Adding configuration to standalone buildscript is forbidden"() {
        file("foo.gradle") << """
            buildscript {
                configurations {
                    newconf
                }
            }
        """

        buildFile << """
            apply from: "foo.gradle"
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Cannot mutate configuration container for script 'foo.gradle' using create(String). Configurations cannot be added or removed from the buildscript configuration container.")
    }

    def "removing the classpath configuration from project buildscript is forbidden"() {
        buildFile << """
            buildscript {
                configurations {
                    remove(classpath)
                }
            }
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Cannot mutate configuration container for buildscript of root project 'root' using remove(Object). Configurations cannot be added or removed from the buildscript configuration container.")
    }

    def "removing the classpath configuration from settings buildscript is forbidden"() {
        settingsFile << """
            buildscript {
                configurations {
                    remove(classpath)
                }
            }
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Cannot mutate configuration container for settings file 'settings.gradle' using remove(Object). Configurations cannot be added or removed from the buildscript configuration container.")
    }

    def "removing the classpath configuration from init buildscript is forbidden"() {
        initScriptFile << """
            buildscript {
                configurations {
                    remove(classpath)
                }
            }
        """

        when:
        executer.usingInitScript(initScriptFile)
        fails("help")

        then:
        failure.assertHasCause("Cannot mutate configuration container for initialization script 'init.gradle' using remove(Object). Configurations cannot be added or removed from the buildscript configuration container.")
    }

    def "removing the classpath configuration from standalone buildscript is forbidden"() {
        file("foo.gradle") << """
            buildscript {
                configurations {
                    remove(classpath)
                }
            }
        """

        buildFile << """
            apply from: "foo.gradle"
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Cannot mutate configuration container for script 'foo.gradle' using remove(Object). Configurations cannot be added or removed from the buildscript configuration container.")
    }

    def "project buildscripts support detached configurations for resolving external dependencies"() {
        mavenRepo.module("org", "foo").publish()
        buildFile << """
            buildscript {
                ${mavenTestRepository()}
            }
            task resolve {
                def files = buildscript.configurations.detachedConfiguration(
                    buildscript.dependencies.create("org:foo:1.0")
                ).incoming.files
                doLast {
                    assert files.files*.name == ["foo-1.0.jar"]
                }
            }
        """

        expect:
        succeeds("resolve")
    }

    def "settings buildscripts support detached configurations for resolving external dependencies"() {
        mavenRepo.module("org", "foo").publish()
        settingsFile << """
            buildscript {
                ${mavenTestRepository()}
            }
            def files = buildscript.configurations.detachedConfiguration(
                buildscript.dependencies.create("org:foo:1.0")
            ).incoming.files
            assert files.files*.name == ["foo-1.0.jar"]
        """

        expect:
        succeeds("help")
    }

    def "init buildscripts support detached configurations for resolving external dependencies"() {
        mavenRepo.module("org", "foo").publish()
        initScriptFile << """
            buildscript {
                ${mavenTestRepository()}
            }
            def files = buildscript.configurations.detachedConfiguration(
                buildscript.dependencies.create("org:foo:1.0")
            ).incoming.files
            assert files.files*.name == ["foo-1.0.jar"]
        """

        when:
        executer.usingInitScript(initScriptFile)

        then:
        succeeds("help")
    }

    def "standalone buildscripts support detached configurations for resolving external dependencies"() {
        mavenRepo.module("org", "foo").publish()
        file("foo.gradle") << """
            buildscript {
                ${mavenTestRepository()}
            }
            def files = buildscript.configurations.detachedConfiguration(
                buildscript.dependencies.create("org:foo:1.0")
            ).incoming.files
            assert files.files*.name == ["foo-1.0.jar"]
        """

        buildFile << """
            apply from: "foo.gradle"
        """

        expect:
        succeeds("help")
    }

    @ToBeImplemented("See #30320, the final solution might be different and require this test to be updated")
    // This is not necessarily desired behavior, or important behavior at all.
    // The detached configuration is _not_ the project. It should not claim to be the project.
    // Ideally, this configuration would have an unspecified identity, similar to init, settings, and standalone scripts.
    def "project buildscripts detached configurations are identified as detached from root project's identity"() {
        mavenRepo.module("org", "foo").publish()
        buildFile << """
            buildscript {
                ${mavenTestRepository()}
            }

            version = "1.0"
            group = "foo"

            task resolve {
                def rootComponent = buildscript.configurations.detachedConfiguration(
                    buildscript.dependencies.create("org:foo:1.0")
                ).incoming.resolutionResult.rootComponent
                doLast {
                    def root = rootComponent.get()
                    assert root.moduleVersion.group == "foo"
                    assert root.moduleVersion.name == "root-detachedConfiguration1"
                    assert root.moduleVersion.version == "1.0"
                    assert root.id instanceof ModuleComponentIdentifier
                    assert root.id.group == "foo"
                    assert root.id.module == "root-detachedConfiguration1"
                    assert root.id.version == "1.0"
                }
            }
        """

        expect:
        fails("resolve")
    }

    @ToBeImplemented("See #30320, the final solution might be different and require this test to be updated")
    def "settings buildscripts detached configurations have unspecified identity"() {
        mavenRepo.module("org", "foo").publish()
        settingsFile << """
            buildscript {
                ${mavenTestRepository()}
            }
            def rootComponent = buildscript.configurations.detachedConfiguration(
                buildscript.dependencies.create("org:foo:1.0")
            ).incoming.resolutionResult.rootComponent
            def root = rootComponent.get()
            assert root.moduleVersion.group == "unspecified"
            assert root.moduleVersion.name == "unspecified-detachedConfiguration1"
            assert root.moduleVersion.version == "unspecified"
            assert root.id instanceof ModuleComponentIdentifier
            assert root.id.module == "unspecified-detachedConfiguration1"
            assert root.id.group == "unspecified"
            assert root.id.version == "unspecified"
        """

        expect:
        fails("help")
    }

    @ToBeImplemented("See #30320, the final solution might be different and require this test to be updated")
    def "init buildscripts detached configurations have unspecified identity"() {
        mavenRepo.module("org", "foo").publish()
        initScriptFile << """
            buildscript {
                ${mavenTestRepository()}
            }
            def rootComponent = buildscript.configurations.detachedConfiguration(
                buildscript.dependencies.create("org:foo:1.0")
            ).incoming.resolutionResult.rootComponent
            def root = rootComponent.get()
            assert root.moduleVersion.group == "unspecified"
            assert root.moduleVersion.name == "unspecified-detachedConfiguration1"
            assert root.moduleVersion.version == "unspecified"
            assert root.id instanceof ModuleComponentIdentifier
            assert root.id.module == "unspecified-detachedConfiguration1"
            assert root.id.group == "unspecified"
            assert root.id.version == "unspecified"
        """

        when:
        executer.usingInitScript(initScriptFile)

        then:
        fails("help")
    }

    @ToBeImplemented("See #30320, the final solution might be different and require this test to be updated")
    def "standalone buildscripts detached configurations have unspecified identity"() {
        mavenRepo.module("org", "foo").publish()
        file("foo.gradle") << """
            buildscript {
                ${mavenTestRepository()}
            }
            def rootComponent = buildscript.configurations.detachedConfiguration(
                buildscript.dependencies.create("org:foo:1.0")
            ).incoming.resolutionResult.rootComponent
            def root = rootComponent.get()
            assert root.moduleVersion.group == "unspecified"
            assert root.moduleVersion.name == "unspecified-detachedConfiguration1"
            assert root.moduleVersion.version == "unspecified"
            assert root.id instanceof ModuleComponentIdentifier
            assert root.id.module == "unspecified-detachedConfiguration1"
            assert root.id.group == "unspecified"
            assert root.id.version == "unspecified"
        """

        buildFile << """
            apply from: "foo.gradle"
        """

        expect:
        fails("help")
    }

    def "project buildscripts support detached configurations for resolving local dependencies"() {
        buildFile << """
            task resolve {
                def conf = buildscript.configurations.detachedConfiguration(
                    buildscript.dependencies.create(project(":other"))
                )
                conf.attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "foo"))
                }
                def files = conf.incoming.files
                doLast {
                    assert files.files*.name == ["foo.txt"]
                }
            }
        """
        settingsFile << """
            include "other"
        """
        file("other/build.gradle") << """
            configurations {
                consumable("foo") {
                    outgoing {
                        artifact file("foo.txt")
                    }
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "foo"))
                    }
                }
            }
        """

        expect:
        succeeds(":resolve")
    }

    def "standalone buildscripts support detached configurations for resolving local dependencies"() {
        mavenRepo.module("org", "foo").publish()
        file("foo.gradle") << """
            buildscript {
                ${mavenTestRepository()}
            }
            def files = buildscript.configurations.detachedConfiguration(
                buildscript.dependencies.create(project(":other"))
            ).incoming.files
            assert files.files*.name == ["foo.txt"]
        """
        settingsFile << """
            include "other"
        """
        file("other/build.gradle") << """
            configurations {
                consumable("foo") {
                    outgoing {
                        artifact file("foo.txt")
                    }
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "foo"))
                    }
                }
            }
        """

        buildFile << """
            apply from: "foo.gradle"
        """

        expect:
        succeeds("help")
    }

    def "creating a settings buildscript configuration is forbidden in Kotlin"() {
        mavenRepo.module("org", "foo").publish()
        settingsFile.delete()
        settingsKotlinFile << """
            buildscript {
                ${mavenTestRepository(GradleDsl.KOTLIN)}
                configurations {
                    create("myConfig")
                }
                dependencies {
                    "myConfig"("org:foo:1.0")
                }
            }

            val files = buildscript.configurations["myConfig"].files
            assert(files.map { it.name } == listOf("foo-1.0.jar"))
        """

        when:
        fails("help")

        then:
        failure.assertHasErrorOutput("Cannot mutate configuration container for settings file 'settings.gradle.kts' using create(String). Configurations cannot be added or removed from the buildscript configuration container.")
    }

    def "creating a detached settings buildscript configuration works in Kotlin"() {
        mavenRepo.module("org", "foo").publish()
        settingsFile.delete()
        settingsKotlinFile << """
            buildscript {
                ${mavenTestRepository(GradleDsl.KOTLIN)}
            }

            val myConfig = buildscript.configurations.detachedConfiguration(
                buildscript.dependencies.create("org:foo:1.0")
            )

            val files = myConfig.files
            assert(files.map { it.name } == listOf("foo-1.0.jar"))
        """

        expect:
        succeeds("help")
    }

    def "project buildscript resolution failure clearly indicates a project buildscript has failed"() {
        buildFile << """
            buildscript {
                dependencies {
                    classpath("does:not:exist")
                }
            }
        """

        expect:
        fails("help")
        failure.assertHasDescription("A problem occurred configuring root project 'root'.")
        failure.assertHasCause("Could not resolve all artifacts for configuration 'classpath'.")
    }

    @ToBeImplemented
    def "settings script resolution failure clearly indicates a project buildscript has failed"() {
        settingsFile << """
            buildscript {
                dependencies {
                    classpath("does:not:exist")
                }
            }
        """

        expect:
        fails("help")
        // TODO: The message does not mention settings anywhere. This should be improved.
        failure.assertHasDescription("Could not resolve all artifacts for configuration 'classpath'")
    }

    def "init script resolution failure clearly indicates a project buildscript has failed"() {
        initScriptFile << """
            buildscript {
                dependencies {
                    classpath("does:not:exist")
                }
            }

            // Force resolution. For some reason the classpath configuration is not resolved by itself.
            buildscript.configurations.classpath.files
        """

        when:
        executer.usingInitScript(initScriptFile)

        then:
        fails("help")
        failure.assertHasDescription("A problem occurred evaluating initialization script.")
        failure.assertHasCause("Could not resolve all files for configuration 'classpath'.")
    }

    @ToBeImplemented
    def "standalone script resolution failure clearly indicates a project buildscript has failed"() {
        file("foo.gradle") << """
            buildscript {
                dependencies {
                    classpath("does:not:exist")
                }
            }
        """
        buildFile << """
            apply from: 'foo.gradle'
        """

        expect:
        fails(":help")
        // TODO: The message does not mention the standalone script anywhere. This should be improved.
        failure.assertHasDescription("A problem occurred evaluating root project 'root'.")
        failure.assertHasCause("Could not resolve all artifacts for configuration 'classpath'.")
    }
}
