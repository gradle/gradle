/*
 * Copyright 2023 the original author or authors.
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

class PluginCatalogDependencyDeclarationCompositeBuildIntegrationTest extends AbstractIntegrationSpec {

    static final String PLUGIN = 'com.acme.my.plugin'
    static final String VERSION = '1.0'

    def "understands plugin dependency notations in composite build"() {
        withDummyPlugin(true)
        executer.inDirectory(file("my-plugin"))
        run 'publishAllPublicationsToBuildRepository'
        def repoLoc = file("my-plugin/build/repo").toURI().toURL().toString()

        when:

        settingsFile << """
            pluginManagement {
                repositories {
                    maven { url = "$repoLoc" }
                }
                includeBuild('my-plugin')
            }

            rootProject.name = 'test-plugin'
        """

        buildFile << """
            ${dependenciesBlock.replace('%repoloc%', repoLoc)}

            def checkPluginCoordinates(dependenciesSet, group, version) {
                String name = group + '.gradle.plugin';
                dependenciesSet.find { it instanceof ExternalDependency && it.group == group && it.name == name && it.version == version}
            }

            $incomingDependencies

            task checkIncomingDependencies {
                doLast {
                    checkPluginCoordinates(deps, "$PLUGIN", "$VERSION")
                }
            }
        """

        then:
        succeeds 'checkIncomingDependencies'

        where:
        id                  | dependenciesBlock | incomingDependencies
        "buildscript block" | """
                buildscript {
                    repositories {
                        maven {
                            url "%repoloc%"
                        }
                    }
                    dependencies {
                        classpath(plugin("$PLUGIN", "$VERSION"))
                    }
                }
            """ | """
            def deps = buildscript.configurations.classpath.incoming.dependencies
        """

        "main dependencies block" | """
                configurations {
                    conf
                }

                dependencies {
                    conf(plugin("$PLUGIN", "$VERSION"))

                }
            """ | """
            def deps = configurations.conf.incoming.dependencies
        """

        "test-suite dependencies block" | """
                plugins {
                    id 'jvm-test-suite'
                }

                testing {
                    suites {
                        test(JvmTestSuite) {
                            dependencies {
                                implementation(plugin("$PLUGIN", "$VERSION"))
                            }
                        }
                    }
                }
            """ | """
            def deps = configurations.testImplementation.incoming.dependencies
        """
    }

    def "understands plugin dependency notations in composite build from version catalog"() {
        withDummyPlugin(true)
        executer.inDirectory(file("my-plugin"))
        run 'publishAllPublicationsToBuildRepository'
        def repoLoc = file("my-plugin/build/repo").toURI().toURL().toString()

        when:

        settingsFile << """
            pluginManagement {
                repositories {
                    maven { url = "$repoLoc" }
                }
                includeBuild('my-plugin')
            }

            rootProject.name = 'test-plugin'

            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        plugin('greeting', "$PLUGIN").version("$VERSION")
                    }
                }
            }
        """

        buildFile << """
            ${dependenciesBlock.replace('%repoloc%', repoLoc)}

            def checkPluginCoordinates(dependenciesSet, group, version) {
                String name = group + '.gradle.plugin';
                dependenciesSet.find { it instanceof ExternalDependency && it.group == group && it.name == name && it.version == version}
            }

            $incomingDependencies

            task checkIncomingDependencies {
                doLast {
                    checkPluginCoordinates(deps, "$PLUGIN", "$VERSION")
                }
            }
        """

        then:
        succeeds 'checkIncomingDependencies'

        where:
        id                  | dependenciesBlock | incomingDependencies
        "buildscript block" | """
                buildscript {
                    repositories {
                        maven {
                            url "%repoloc%"
                        }
                    }
                    dependencies {
                        classpath(plugin(libs.plugins.greeting))
                    }
                }
            """ | """
            def deps = buildscript.configurations.classpath.incoming.dependencies
        """

        "main dependencies block" | """
                configurations {
                    conf
                }

                dependencies {
                    conf(plugin(libs.plugins.greeting))

                }
            """ | """
            def deps = configurations.conf.incoming.dependencies
        """

        "test-suite dependencies block" | """
                plugins {
                    id 'jvm-test-suite'
                }

                testing {
                    suites {
                        test(JvmTestSuite) {
                            dependencies {
                                implementation(plugin(libs.plugins.greeting))
                            }
                        }
                    }
                }
            """ | """
            def deps = configurations.testImplementation.incoming.dependencies
        """
    }

    private void withDummyPlugin(boolean withPublication = false) {
        def publishPlugin = withPublication ? "id 'maven-publish'" : ''
        def publishConfig = ''
        if (withPublication) {
            publishConfig = '''
                publishing {
                    repositories {
                       maven {
                           name 'build'
                           url "\$buildDir/repo"
                       }
                    }
                }
            '''
        }
        file('my-plugin/settings.gradle') << '''
            rootProject.name = 'my-plugin'
        '''
        file('my-plugin/build.gradle') << """
            plugins {
                id 'java-gradle-plugin'
                $publishPlugin
            }

            group = 'com.acme'
            version = "$VERSION"

            gradlePlugin {
                plugins {
                    greeting {
                        id = "$PLUGIN"
                        implementationClass = 'com.acme.MyPlugin'
                    }
                }
            }

            $publishConfig
        """

        file('my-plugin/src/main/java/com/acme/MyPlugin.java') << """
            package com.acme;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class MyPlugin implements Plugin<Project> {
                public void apply(Project p) {
                }
            }
        """
    }
}
