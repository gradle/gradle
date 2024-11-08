/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class PluginVariantResolveIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/13659")
    def "should report an incompatible Java version of a plugin properly (#id)"() {
        withDummyPlugin(true)
        executer.inDirectory(file("my-plugin"))
        run 'publishAllPublicationsToBuildRepository'
        def repoLoc = file("my-plugin/build/repo").toURI().toURL().toString()

        settingsFile << """
            pluginManagement {
                repositories {
                    maven { url = "$repoLoc" }
                }
            }

            rootProject.name = 'test'
        """

        buildFile << """
            ${pluginsBlock.replace('%repoloc%', repoLoc)}
        """

        when:
        fails ':help'

        then:
        failure.assertHasErrorOutput("Dependency requires at least JVM runtime version ${JavaVersion.VERSION_HIGHER.majorVersion}. This build uses a Java ${JavaVersion.current().majorVersion} JVM.")

        where:
        id                  | pluginsBlock
        "plugins block"     | """
            plugins {
                id 'com.acme.my.plugin' version '1.0'
            }
        """

        "buildscript block" | """
                buildscript {
                    repositories {
                        maven {
                            url "%repoloc%"
                        }
                    }
                    dependencies {
                        classpath 'com.acme:my-plugin:1.0'
                    }
                }
            """
    }

    @Issue("https://github.com/gradle/gradle/issues/13659")
    def "should report an incompatible Java version of a plugin properly (#id) using composite builds"() {
        settingsFile << """
            pluginManagement {
                includeBuild('my-plugin')
            }
            rootProject.name = 'test-plugin'

            includeBuild('my-plugin') {
                $substitution
            }
        """
        withDummyPlugin()
        buildFile << pluginsBlock

        when:
        fails ':help'

        then:
        failure.assertHasErrorOutput("Dependency requires at least JVM runtime version ${JavaVersion.VERSION_HIGHER.majorVersion}. This build uses a Java ${JavaVersion.current().majorVersion} JVM.")

        where:
        id                  | pluginsBlock       | substitution
        "plugins block"     | """
            plugins {
                id 'com.acme.my.plugin'
            }
        """     | ""

        "buildscript block" | """
                buildscript {
                    dependencies {
                        classpath 'com.acme:my-plugin:1.0'
                    }
                }
            """ | """dependencySubstitution {
                    substitute(module("com.acme:my-plugin")).with(project(":"))
                }"""

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
            version = '1.0'

            gradlePlugin {
                plugins {
                    greeting {
                        id = 'com.acme.my.plugin'
                        implementationClass = 'com.acme.MyPlugin'
                    }
                }
            }

            [configurations.apiElements, configurations.runtimeElements].each {
                // let's pretend it was built with a newer Java version
                it.attributes {
                    // This test is not Artic Code Vault safe
                    attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 2099)
                }
            }

            java {
               // make sure we add a sources variant like in the bug report
               // because this is this variant which causes the problem: because there's
               // a mismatch on the Java version for the other variants, this one ended up
               // being selected!
               withSourcesJar()
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
