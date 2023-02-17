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

class PluginCatalogDependencyNotationIntegrationSpec extends AbstractIntegrationSpec {

    static final String JS = 'org.jetbrains.kotlin.js'
    static final String JVM = 'org.jetbrains.kotlin.jvm'
    static final String SCRIPTING = 'org.jetbrains.kotlin.plugin.scripting'
    static final String PARCELIZE = 'org.jetbrains.kotlin.plugin.parcelize'
    static final String VERSION = '1.8.0'

    def "understands plugin dependency notations"() {
        when:
        buildScript("""
import org.gradle.api.internal.artifacts.dependencies.*

//buildscript
buildscript {
    ${mavenCentralRepository()}
    dependencies {
        classpath (plugin("$JS")) {
            version {
                prefer "$VERSION"
            }
        }
        classpath (plugin("$JVM", "$VERSION"))
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
    conf (plugin("$JS")) {
        version {
            prefer "$VERSION"
        }
        transitive = false
    }
     conf plugin("$JVM", "$VERSION")
     conf plugin("$SCRIPTING")
}

// test-suite dependencies
testing {
    suites {
        test {
            dependencies {
                implementation (plugin("$JS")) {
                    version {
                        prefer "$VERSION"
                    }
                    transitive = false
                }
                implementation plugin("$JVM", "$VERSION")
                implementation plugin("$SCRIPTING")
            }
        }
    }
}

def checkPluginCoordinates(dependenciesSet, group) {
    String name = group + '.gradle.plugin';
    dependenciesSet.find { it instanceof ExternalDependency && it.group == group && it.name == name }
}

def checkPluginCoordinates(dependenciesSet, group, version, checkIsTransitive) {
    String name = group + '.gradle.plugin'
    def configuredDep = dependenciesSet.find { it instanceof ExternalDependency && it.group == group && it.name == name && it.version == version }
    if (checkIsTransitive) {
        assert configuredDep.transitive == false;
    }
}

task checkDeps {
    doLast {
        // buildscript
        def buildscriptDeps = buildscript.configurations.classpath.incoming.dependencies
        checkPluginCoordinates(buildscriptDeps, "$JS", "$VERSION", false)
        checkPluginCoordinates(buildscriptDeps, "$JVM", "$VERSION", false)

        // main dependency block
        def mainDeps = configurations.conf.incoming.dependencies
        checkPluginCoordinates(mainDeps, "$JS", "$VERSION", true)
        checkPluginCoordinates(mainDeps, "$JVM", "$VERSION", false)
        checkPluginCoordinates(mainDeps, "$SCRIPTING")

        // test-suite dependencies
        def testDeps = configurations.testImplementation.incoming.dependencies
        checkPluginCoordinates(testDeps, "$JS", "$VERSION", true)
        checkPluginCoordinates(testDeps, "$JVM", "$VERSION", false)
        checkPluginCoordinates(testDeps, "$SCRIPTING")
    }
}
""")
        then:
        succeeds 'checkDeps'
    }

    def "understands plugin dependency notations in kotlin"() {
        when:
        buildKotlinFile << """
import org.gradle.api.internal.artifacts.dependencies.*

// buildscript
buildscript {
    repositories {
        ${mavenCentralRepository(GradleDsl.KOTLIN)}
    }
    dependencies {
        "classpath"(plugin("$JS")) {
            version {
                prefer("$VERSION")
            }
        }
        "classpath"(plugin("$JVM", "$VERSION"))
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
    "conf"(plugin("$JS")) {
        version {
            prefer("$VERSION")
        }
        isTransitive = false
    }
    "conf"(plugin(id = "$JVM", version = "$VERSION"))
    "conf"(plugin("$SCRIPTING", "$VERSION"))
    "conf"(plugin("$PARCELIZE"))
}

// test-suite dependencies
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            dependencies {
                implementation(plugin("$JS")) {
                    version {
                        prefer("$VERSION")
                    }
                    isTransitive = false
                }
                implementation(plugin("$JVM", "$VERSION"))
                implementation(plugin("$SCRIPTING"))
            }
        }
    }
}

tasks.register("checkDeps") {
    doLast {
        fun checkPluginCoordinates(dependenciesSet : DependencySet, group : String) {
            val name = group.plus(".gradle.plugin")
            dependenciesSet.single { it is ExternalDependency && it.group == group && it.name == name }
        }

        fun checkPluginCoordinates(dependenciesSet : DependencySet, group : String, version : String, checkIsTransitive : Boolean) {
            val name = group.plus(".gradle.plugin")
            val configuredDep =  dependenciesSet.single { it is ExternalDependency && it.group == group && it.name == name && it.version == version }
            if (checkIsTransitive) {
                configuredDep as ExternalDependency
                require(configuredDep.isTransitive == false)
            }
        }

        // buildscript
        val buildscriptDeps = buildscript.configurations.getByName("classpath").incoming.dependencies
        checkPluginCoordinates(buildscriptDeps, "$JS", "$VERSION", false)
        checkPluginCoordinates(buildscriptDeps, "$JVM", "$VERSION", false)

        // main dependency block
        val mainDeps = configurations.get("conf").incoming.dependencies
        checkPluginCoordinates(mainDeps, "$JS", "$VERSION", true)
        checkPluginCoordinates(mainDeps, "$JVM", "$VERSION", false)
        checkPluginCoordinates(mainDeps, "$SCRIPTING", "$VERSION", false)
        checkPluginCoordinates(mainDeps, "$PARCELIZE")

        // test-suite dependencies
        val testDeps = configurations.get("testImplementation").incoming.dependencies
        checkPluginCoordinates(testDeps, "$JS", "$VERSION", true)
        checkPluginCoordinates(testDeps, "$JVM", "$VERSION", false)
        checkPluginCoordinates(testDeps, "$SCRIPTING")
    }
}
"""
        then:
        succeeds 'checkDeps'
    }
}
