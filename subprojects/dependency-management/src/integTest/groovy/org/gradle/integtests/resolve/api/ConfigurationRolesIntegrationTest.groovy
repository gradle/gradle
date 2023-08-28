/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.extensions.FluidDependenciesResolveTest

@FluidDependenciesResolveTest
class ConfigurationRolesIntegrationTest extends AbstractIntegrationSpec {
    def "cannot resolve a configuration with role #role at execution time"() {
        given:
        buildFile << """

        configurations {
            internal {
                $code
            }
        }
        dependencies {
            internal files('foo.jar')
        }

        task checkState {
            def files = configurations.internal
            doLast {
                files.files
            }
        }

        """

        when:
        fails 'checkState'

        then:
        failure.assertHasCause("Resolving dependency configuration 'internal' is not allowed as it is defined as 'canBeResolved=false'.\nInstead, a resolvable ('canBeResolved=true') dependency configuration that extends 'internal' should be resolved.")

        where:
        role                      | code
        'consume or publish only' | 'canBeResolved = false'
        'dependency scope'        | 'canBeResolved = false; canBeConsumed = false'

    }

    def "cannot resolve a configuration with role #role at configuration time"() {
        given:
        buildFile << """

        configurations {
            internal {
                $code
            }
        }
        dependencies {
            internal files('foo.jar')
        }

        task checkState(dependsOn: configurations.internal.files) {
        }

        """

        when:
        fails 'checkState'

        then:
        failure.assertHasCause("Resolving dependency configuration 'internal' is not allowed as it is defined as 'canBeResolved=false'.\nInstead, a resolvable ('canBeResolved=true') dependency configuration that extends 'internal' should be resolved.")

        where:
        role                      | code
        'consume or publish only' | 'canBeResolved = false'
        'dependency scope'        | 'canBeResolved = false; canBeConsumed = false'
    }

    @ToBeFixedForConfigurationCache(because = "Uses Configuration API")
    def "cannot resolve a configuration with role #role using #method"() {
        given:
        buildFile << """

        configurations {
            internal {
                $role
            }
        }
        dependencies {
            internal files('foo.jar')
        }

        task checkState {
            doLast {
                configurations.internal.$method
            }
        }

        """

        when:
        if (method == 'getResolvedConfiguration()') {
            if (role == 'canBeResolved = false') {
                executer.expectDocumentedDeprecationWarning("""Calling configuration method 'getResolvedConfiguration()' is deprecated for configuration 'internal', which has permitted usage(s):
\tConsumable - this configuration can be selected by another project as a dependency
\tDeclarable - this configuration can have dependencies added to it
This method is only meant to be called on configurations which allow the (non-deprecated) usage(s): 'Resolvable'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configuration_usage""")
            } else {
                executer.expectDocumentedDeprecationWarning("""Calling configuration method 'getResolvedConfiguration()' is deprecated for configuration 'internal', which has permitted usage(s):
\tDeclarable - this configuration can have dependencies added to it
This method is only meant to be called on configurations which allow the (non-deprecated) usage(s): 'Resolvable'. This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_configuration_usage""")
            }
        }
        fails 'checkState'

        then:
        failure.assertHasCause("Resolving dependency configuration 'internal' is not allowed as it is defined as 'canBeResolved=false'.\nInstead, a resolvable ('canBeResolved=true') dependency configuration that extends 'internal' should be resolved.")

        where:
        [method, role] << [
            ['getResolvedConfiguration()', 'getBuildDependencies()', 'getIncoming().getFiles()', 'getIncoming().getResolutionResult()', 'getResolvedConfiguration()'],
            ['canBeResolved = false', 'canBeResolved = false; canBeConsumed = false']
        ].combinations()
    }

    def "cannot add a dependency on a configuration role #role"() {
        given:
        file('settings.gradle') << 'include "a", "b"'
        buildFile << """
        project(':a') {
            configurations {
                compile
            }
            dependencies {
                compile project(path: ':b', configuration: 'internal')
            }

            task check {
                def files = configurations.compile
                doLast { files.files }
            }
        }
        project(':b') {
            configurations {
                internal {
                    $code
                }
            }
        }

        """

        when:
        fails 'a:check'

        then:
        failure.assertHasCause "Selected configuration 'internal' on 'project :b' but it can't be used as a project dependency because it isn't intended for consumption by other components."

        where:
        role                    | code
        'query or resolve only' | 'canBeConsumed = false'
        'dependency scope'      | 'canBeResolved = false; canBeConsumed = false'
    }

    def "cannot depend on default configuration if it's not consumable (#role)"() {
        given:
        file('settings.gradle') << 'include "a", "b"'
        buildFile << """
        project(':a') {
            configurations {
                compile
            }
            dependencies {
                compile project(path: ':b')
            }

            task check {
                def files = configurations.compile
                doLast { files.files }
            }
        }
        project(':b') {
            configurations {
                'default' {
                    $code
                }
            }
        }

        """

        when:
        fails 'a:check'

        then:
        failure.assertHasCause "Selected configuration 'default' on 'project :b' but it can't be used as a project dependency because it isn't intended for consumption by other components."

        where:
        role                    | code
        'query or resolve only' | 'canBeConsumed = false'
        'dependency scope'      | 'canBeResolved = false; canBeConsumed = false'
    }

    def "depending on consumable configuration that is deprecated for consumption emits warning"() {
        buildFile << """
            configurations {
                res {
                    canBeConsumed = false
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, "foo"))
                    }
                }
                migratingUnlocked("con", org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration.LEGACY_TO_RESOLVABLE_DEPENDENCY_SCOPE) {
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, "foo"))
                    }
                }
            }

            dependencies {
                res project
            }

            task resolve {
                def files = configurations.res.incoming.files
                doLast {
                    println files.files
                }
            }
        """

        expect:
        executer.expectDocumentedDeprecationWarning("The con configuration has been deprecated for consumption. This will fail with an error in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
        succeeds("resolve")
    }

    def "cannot create #first and #second configuration with the same name"() {
        buildFile << """
            configurations {
                $first("foo")
                $second("foo")
            }
        """

        when:
        fails "help"

        then:
        failureHasCause("Cannot add a configuration with name 'foo' as a configuration with that name already exists.")

        where:
        first             | second
        "consumable"      | "resolvable"
        "consumable"      | "dependencyScope"
        "resolvable"      | "consumable"
        "resolvable"      | "dependencyScope"
        "dependencyScope" | "consumable"
        "dependencyScope" | "resolvable"
    }

    def "withType works for factory methods before declaration in Groovy DSL"() {
        buildFile << """
            configurations {
                withType(ResolvableConfiguration) {
                    println "Resolvable: " + name
                }
                withType(ConsumableConfiguration) {
                    println "Consumable: " + name
                }
                withType(DependencyScopeConfiguration) {
                    println "Dependencies: " + name
                }
                resolvable("foo")
                consumable("bar")
                dependencyScope("baz")
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("""
            Resolvable: foo
            Consumable: bar
            Dependencies: baz
            """.stripIndent()
        )
    }

    def "withType works for factory methods after declaration in Groovy DSL"() {
        buildFile << """
            configurations {
                resolvable("foo")
                consumable("bar")
                dependencyScope("baz")
                withType(ResolvableConfiguration) {
                    println "Resolvable: " + name
                }
                withType(ConsumableConfiguration) {
                    println "Consumable: " + name
                }
                withType(DependencyScopeConfiguration) {
                    println "Dependencies: " + name
                }
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("""
            Resolvable: foo
            Consumable: bar
            Dependencies: baz
            """.stripIndent()
        )
    }

    def "withType works for factory methods before declaration in Kotlin DSL"() {
        buildKotlinFile << """
            configurations {
                withType<ResolvableConfiguration> {
                    println("Resolvable: " + name)
                }
                withType<ConsumableConfiguration> {
                    println("Consumable: " + name)
                }
                withType<DependencyScopeConfiguration> {
                    println("Dependencies: " + name)
                }
                resolvable("foo")
                consumable("bar")
                dependencyScope("baz")
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("""
            Resolvable: foo
            Consumable: bar
            Dependencies: baz
            """.stripIndent()
        )
    }

    def "withType works for factory methods after declaration in Kotlin DSL"() {
        buildKotlinFile << """
            configurations {
                resolvable("foo")
                consumable("bar")
                dependencyScope("baz")
                withType<ResolvableConfiguration> {
                    println("Resolvable: " + name)
                }
                withType<ConsumableConfiguration> {
                    println("Consumable: " + name)
                }
                withType<DependencyScopeConfiguration> {
                    println("Dependencies: " + name)
                }
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("""
            Resolvable: foo
            Consumable: bar
            Dependencies: baz
            """.stripIndent()
        )
    }

    def "withType works for factory methods in Java"() {
        file("buildSrc/src/main/java/MyPlugin.java") << """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.artifacts.Configuration;
            import org.gradle.api.artifacts.ResolvableConfiguration;
            import org.gradle.api.artifacts.ConsumableConfiguration;
            import org.gradle.api.artifacts.DependencyScopeConfiguration;

            public class MyPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.getConfigurations().withType(ResolvableConfiguration.class, configuration -> {
                        System.out.println("Resolvable: " + configuration.getName());
                    });
                    project.getConfigurations().withType(ConsumableConfiguration.class, configuration -> {
                        System.out.println("Consumable: " + configuration.getName());
                    });
                    project.getConfigurations().withType(DependencyScopeConfiguration.class, configuration -> {
                        System.out.println("Dependencies: " + configuration.getName());
                    });
                    project.getConfigurations().resolvable("foo");
                    project.getConfigurations().consumable("bar");
                    project.getConfigurations().dependencyScope("baz");
                }
            }
        """
        file("buildSrc/build.gradle") << """
            plugins {
                id("java-gradle-plugin")
            }
            gradlePlugin {
                plugins {
                    broken {
                        id = "my-plugin"
                        implementationClass = "MyPlugin"
                    }
                }
            }
        """
        buildFile << """
            plugins {
                id("my-plugin")
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("""
            Resolvable: foo
            Consumable: bar
            Dependencies: baz
            """.stripIndent()
        )
    }
}
