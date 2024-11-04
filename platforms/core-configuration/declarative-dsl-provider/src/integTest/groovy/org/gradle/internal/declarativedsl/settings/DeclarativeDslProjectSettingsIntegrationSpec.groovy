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

package org.gradle.internal.declarativedsl.settings

import org.gradle.api.internal.plugins.software.RegistersSoftwareTypes
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.hamcrest.CoreMatchers.*

class DeclarativeDslProjectSettingsIntegrationSpec extends AbstractIntegrationSpec {

    def "can interpret the settings file with the declarative DSL"() {
        given:
        file("settings.gradle.dcl") << """
            pluginManagement {
                includeBuild("pluginIncluded")
                repositories {
                    mavenCentral()
                    google()
                }
            }

            rootProject.name = "test-value"
            include(":a")
            include(":b")

            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                    google()
                }
            }
        """
        buildFile << "println('name = ' + rootProject.name)"
        file("a/build.gradle") << ""
        file("b/build.gradle") << ""
        file("pluginIncluded/settings.gradle.dcl") << "rootProject.name = \"pluginIncluded\""

        expect:
        succeeds(":help", ":a:help", ":b:help")
        outputContains("name = test-value")
    }

    def 'schema is written during settings interpretation'() {
        given:
        file("settings.gradle.dcl") << """
            rootProject.name = "test"
        """

        when:
        run(":help")

        then:
        def schemaFile = file(".gradle/declarative-schema/settings.dcl.schema")
        schemaFile.isFile() && schemaFile.text != ""
    }

    def 'reports #kind errors in settings'() {
        given:
        file("settings.gradle.dcl") << """
            rootProject.name = "test"
            $code
        """

        when:
        def failure = fails(":help")

        then:
        failure.assertHasErrorOutput(expectedMessage)

        where:
        kind               | code                  | expectedMessage
        "syntax"           | "..."                 | "3:13: parsing error: Expecting an element"
        "language feature" | "@A dependencies { }" | "3:13: unsupported language feature: AnnotationUsage"
        "semantic"         | "x = 1"               | "3:13: unresolved reference 'x'"
    }

    def 'reports illegal order of settings blocks on #order'() {
        given:
        file("settings.gradle.dcl") << content

        when:
        def failure = fails(":projects")

        then:
        failure.assertHasErrorOutput(errorMessage)

        where:
        order                                | content                                          | errorMessage
        'statement before plugin management' | 'rootProject.name = "foo"\npluginManagement { }' | "1:1: illegal content before 'pluginManagement', which can only appear as the first element in the file"
        'plugins before plugin management'   | 'plugins { }\npluginManagement { }'              | "1:1: illegal content before 'pluginManagement', which can only appear as the first element in the file"
        'statement before plugins'           | 'rootProject.name = "foo"\nplugins { }'          | "1:1: illegal content before 'plugins', which can only be preceded by 'pluginManagement"
    }

    def 'reports duplicate #kind blocks in settings'() {
        given:
        file("settings.gradle.dcl") << content

        when:
        def failure = fails(":projects")

        then:
        failure.assertHasErrorOutput(errorMessage)

        where:
        kind               | content                                                                             | errorMessage
        'plugins'          | 'pluginManagement { }\nplugins { }\nrootProject.name = "foo"\nplugins { }'          | "4:1: duplicate 'plugins'"
        'pluginManagement' | 'pluginManagement { }\nplugins { }\nrootProject.name = "foo"\npluginManagement { }' | "4:1: duplicate 'pluginManagement'"
    }

    def 'supports correct order of blocks in setttings file if there is #order'() {
        given:
        file("settings.gradle.dcl") << content

        expect:
        succeeds(":projects")
        outputContains("Root project 'test-project'")

        where:
        order                                     | content
        'a plugins block but no pluginManagement' | 'plugins { }\nrootProject.name = "test-project"'
        'a pluginManagement block but no plugins' | 'pluginManagement { }\nrootProject.name = "test-project"'
        'no special blocks'                       | 'rootProject.name = "test-project"'
    }

    def 'can apply settings plugins'() {
        given:
        file("included-settings-plugin/build.gradle") << """
            plugins {
                id('java-gradle-plugin')
            }
            gradlePlugin {
                plugins {
                    create("settingsPlugin") {
                        id = "com.example.restricted.settings"
                        implementationClass = "com.example.restricted.RestrictedSettingsPlugin"
                    }
                }
            }
        """

        file("included-settings-plugin/src/main/java/com/example/restricted/Extension.java") << """
            package com.example.restricted;

            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.api.provider.Property;

            import javax.inject.Inject;

            @Restricted
            public abstract class Extension {
                @Restricted
                public abstract Property<String> getId();
            }
        """

        file("included-settings-plugin/src/main/java/com/example/restricted/RestrictedSettingsPlugin.java") << """
            package com.example.restricted;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;

            public class RestrictedSettingsPlugin implements Plugin<Settings> {
                @Override
                public void apply(Settings target) {
                    Extension restricted = target.getExtensions().create("restricted", Extension.class);
                    target.getGradle().settingsEvaluated(settings -> {
                        System.out.println("id = " + restricted.getId().get());
                    });
                }
            }
        """

        file("settings.gradle.dcl") << """
            pluginManagement {
                includeBuild("included-settings-plugin")
            }

            plugins {
                id("com.example.restricted.settings")
            }

            restricted {
                id = "test"
            }
        """

        expect:
        succeeds("help")
        outputContains("id = test")
    }

    def "reports reassigned value"() {
        given:
        file("settings.gradle.dcl") << """
        rootProject.name = "foo"
        rootProject.name = "bar"
        rootProject.name = "baz"
        include(":baz")""".stripIndent().trim()

        when:
        def failure = fails(":projects")

        then:
        failure.assertHasErrorOutput('2:1: Value reassigned in (this:(top-level-object)).rootProject.name := "bar"')
        failure.assertHasErrorOutput('3:1: Value reassigned in (this:(top-level-object)).rootProject.name := "baz"')
    }

    def "resolution failures are reported nicely"() {
        given:
        file("settings.gradle.dcl") << """
            rootProject.name = "test-value"

            dependencyResolutionManagement {
                repositoriesMode = RepositoriesMode.PREFER_PROJECT
            }
        """
        buildFile << "println('name = ' + rootProject.name)"

        expect:
        def result = runAndFail(":help")
        result.assertThatAllDescriptions(allOf(
            containsString("Failures in resolution:\n" +
                "    5:36: unresolved reference 'RepositoriesMode'\n" +
                "    5:53: unresolved reference 'PREFER_PROJECT'\n" +
                "    5:17: unresolved assigned value"),
            containsString("Failures in document checks:\n" +
                "    5:17: unsupported syntax (NamedReferenceWithExplicitReceiver)")
        ))
    }

    def "can reference a custom repository by URL in pluginManagement.repositories.maven"() {
        given: "a DCL-enabled plugin that is published to a file-based Maven repository"

        buildFile(
            """
            plugins {
                id("java-gradle-plugin")
                id("maven-publish")
            }
            group = "com.example"
            version = "1.0"

            repositories { mavenCentral() }
            gradlePlugin {
                plugins {
                    create("restrictedPlugin") {
                        id = "com.example.restricted"
                        implementationClass = "com.example.restricted.RestrictedPlugin"
                    }
                    create("ecosystemPlugin") {
                        id = "com.example.restricted.ecosystem"
                        implementationClass = "com.example.restricted.SoftwareTypeRegistrationPlugin"
                    }
                }
            }
            publishing {
                repositories {
                    maven {
                        url = uri(layout.buildDirectory.dir("repo"))
                    }
                }
            }
            """)

        javaFile("src/main/java/SoftwareTypeRegistrationPlugin.java",
            """
            package com.example.restricted;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.internal.SettingsInternal;
            import org.gradle.plugin.software.internal.SoftwareTypeRegistry;
            import ${RegistersSoftwareTypes.class.name};

            @RegistersSoftwareTypes({ RestrictedPlugin.class })
            abstract public class SoftwareTypeRegistrationPlugin implements Plugin<Settings> {
                @Override
                public void apply(Settings target) {
                }
            }
            """)

        javaFile("src/main/java/Extension.java",
            """
            package com.example.restricted;

            import org.gradle.declarative.dsl.model.annotations.Restricted;
            import org.gradle.api.provider.Property;

            public abstract class Extension {
                @Restricted
                public abstract Property<Integer> getX();
            }
            """)

        javaFile("src/main/java/RestrictedPlugin.java",
            """
            package com.example.restricted;

            import org.gradle.api.DefaultTask;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.provider.ListProperty;
            import org.gradle.api.provider.Property;
            import ${SoftwareType.class.name};

            public abstract class RestrictedPlugin implements Plugin<Project> {
                @SoftwareType(name = "restricted", modelPublicType = Extension.class)
                public abstract Extension getRestricted();

                @Override
                public void apply(Project target) {
                    target.getTasks().register("printX", DefaultTask.class, task -> {
                        task.doLast("print restricted extension content", t -> {
                            System.out.println("x = " + getRestricted().getX().get());
                        });
                    });
                }
            }
            """)

        succeeds("publish")

        when: "consuming the plugin with a DCL project referencing the repository by URL"
        buildFile.delete()
        settingsFile.delete()

        file("settings.gradle.dcl") <<
            """
            pluginManagement {
                repositories {
                    maven {
                        url = uri("build/repo")
                    }
                }
            }

            plugins {
                id("com.example.restricted.ecosystem").version("1.0")
            }

            dependencyResolutionManagement {
                repositories {
                    maven {
                        url = uri("build/repo")
                    }
                }
            }
            """

        file("build.gradle.dcl") <<
            """
            restricted {
                x = 123
            }
            """

        then:
        succeeds("printX")
        outputContains("x = 123")
    }
}
