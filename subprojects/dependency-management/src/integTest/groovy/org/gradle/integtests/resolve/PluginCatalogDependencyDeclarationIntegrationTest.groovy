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
import org.gradle.test.fixtures.dsl.GradleDsl

class PluginCatalogDependencyDeclarationIntegrationTest extends AbstractIntegrationSpec {

    static final String JS = 'org.jetbrains.kotlin.js'
    static final String JVM = 'org.jetbrains.kotlin.jvm'
    static final String SCRIPTING = 'org.jetbrains.kotlin.plugin.scripting'
    static final String VERSION = '1.8.0'
    static final String STRICT_VERSION = '[1.0, 2.0['

    def "understands plugin dependency notations from version catalog"() {
        when:
        settingsFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        plugin('js', "$JS").version {
                            strictly "$STRICT_VERSION"
                            prefer "$VERSION"
                        }
                        plugin('jvm', "$JVM").version("$VERSION")
                        plugin('scripting', "$SCRIPTING").version("$VERSION")
                    }
                }
            }
        """

        buildScript("""
import org.gradle.api.internal.artifacts.dependencies.*

// buildscript
buildscript {
    ${mavenCentralRepository()}
    dependencies {
        classpath(plugin(libs.plugins.js))
        classpath(plugin(libs.plugins.jvm))
    }
}

plugins {
    id 'java'
    id 'jvm-test-suite'
}

// main dependency block
configurations {
    conf
}

dependencies {
    conf(plugin(libs.plugins.js))
    conf(plugin(libs.plugins.jvm))
    conf(plugin(libs.plugins.scripting))
}

// test-suite dependencies
testing {
    suites {
        test {
            dependencies {
                implementation(plugin(libs.plugins.js))
                implementation(plugin(libs.plugins.jvm))
                implementation(plugin(libs.plugins.scripting))
            }
        }
    }
}

def checkPluginCoordinates(dependenciesSet, group, version) {
    String name = group + '.gradle.plugin';
    dependenciesSet.find { it instanceof ExternalDependency && it.group == group && it.name == name && it.version == version}
}

def checkPluginCoordinates(dependenciesSet, group, preferredVersion, strictVersion) {
    String name = group + '.gradle.plugin'
    dependenciesSet.find { it instanceof ExternalDependency && it.group == group && it.name == name && it.versionConstraint.preferredVersion == preferredVersion && it.versionConstraint.strictVersion == strictVersion }
}

task checkDeps {
    doLast {
        // buildscript
        def buildscriptDeps = buildscript.configurations.classpath.incoming.dependencies
        checkPluginCoordinates(buildscriptDeps, "$JS", "$VERSION", "$STRICT_VERSION")
        checkPluginCoordinates(buildscriptDeps, "$JVM", "$VERSION")

        // main dependency block
        def mainDeps = configurations.conf.incoming.dependencies
        checkPluginCoordinates(mainDeps, "$JS", "$VERSION", "$STRICT_VERSION")
        checkPluginCoordinates(mainDeps, "$JVM", "$VERSION")
        checkPluginCoordinates(mainDeps, "$SCRIPTING", "$VERSION")

        // test-suite dependencies
        def testDeps = configurations.testImplementation.incoming.dependencies
        checkPluginCoordinates(testDeps, "$JS", "$VERSION", "$STRICT_VERSION")
        checkPluginCoordinates(testDeps, "$JVM", "$VERSION")
        checkPluginCoordinates(testDeps, "$SCRIPTING", "$VERSION")
    }
}
""")
        then:
        succeeds 'checkDeps'
    }

    def "understands plugin dependency notations from version catalog in kotlin"() {
        when:

        settingsKotlinFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        plugin("js", "$JS").version {
                            strictly("$STRICT_VERSION")
                            prefer("$VERSION")
                        }
                        plugin("jvm", "$JVM").version("$VERSION")
                        plugin("scripting", "$SCRIPTING").version("$VERSION")
                    }
                }
            }
        """

        buildKotlinFile <<  """
            import org.gradle.api.internal.artifacts.dependencies.*

            // buildscript
            buildscript {
                repositories {
                    ${mavenCentralRepository(GradleDsl.KOTLIN)}
                }
                dependencies {
                    classpath(plugin(libs.plugins.js))
                    classpath(plugin(libs.plugins.jvm))
                }
            }

            plugins {
                java
                `jvm-test-suite`
            }

            // main dependency block
            configurations {
                create("conf")
            }

            dependencies {
                "conf"(plugin(libs.plugins.js))
                "conf"(plugin(libs.plugins.jvm))
                "conf"(plugin(libs.plugins.scripting))
            }

            // test-suite dependencies
            testing {
                suites {
                    val test by getting(JvmTestSuite::class) {
                        dependencies {
                            implementation(plugin(libs.plugins.js))
                            implementation(plugin(libs.plugins.jvm))
                            implementation(plugin(libs.plugins.scripting))
                        }
                    }
                }
            }

            tasks.register("checkDeps") {
                doLast {
                    fun checkPluginCoordinates(dependenciesSet : DependencySet, group : String, version : String) {
                        val name = group.plus(".gradle.plugin")
                        dependenciesSet.single { it is ExternalDependency && it.group == group && it.name == name && it.version == version }
                    }

                    fun checkPluginCoordinates(dependenciesSet : DependencySet, group : String, preferredVersion : String, strictVersion : String) {
                        val name = group.plus(".gradle.plugin")
                        dependenciesSet.single { it is ExternalDependency && it.group == group && it.name == name && it.versionConstraint.preferredVersion == preferredVersion && it.versionConstraint.strictVersion == strictVersion }
                    }

                    // buildscript
                    val buildscriptDeps = buildscript.configurations.getByName("classpath").incoming.dependencies
                    checkPluginCoordinates(buildscriptDeps, "$JS", "$VERSION", "$STRICT_VERSION")
                    checkPluginCoordinates(buildscriptDeps, "$JVM", "$VERSION")

                    // main dependency block
                    val mainDeps = configurations.get("conf").incoming.dependencies
                    checkPluginCoordinates(mainDeps, "$JS", "$VERSION", "$STRICT_VERSION")
                    checkPluginCoordinates(mainDeps, "$JVM", "$VERSION")
                    checkPluginCoordinates(mainDeps, "$SCRIPTING", "$VERSION")

                    // test-suite dependencies
                    val testDeps = configurations.get("testImplementation").incoming.dependencies
                    checkPluginCoordinates(testDeps, "$JS", "$VERSION", "$STRICT_VERSION")
                    checkPluginCoordinates(testDeps, "$JVM", "$VERSION")
                    checkPluginCoordinates(testDeps, "$SCRIPTING", "$VERSION")
                }
            }
"""
        then:
        succeeds 'checkDeps'
    }
}
