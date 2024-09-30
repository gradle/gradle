/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.NativePlatformsTestFixture
import org.gradle.nativeplatform.fixtures.app.ExeWithLibraryUsingLibraryHelloWorldApp
import org.gradle.nativeplatform.fixtures.app.HelloWorldApp
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@Requires(UnitTestPreconditions.CanInstallExecutable)
class BinaryFlavorsIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    static final DEFAULT = HelloWorldApp.HELLO_WORLD
    static final FRENCH = HelloWorldApp.HELLO_WORLD_FRENCH

    def helloWorldApp = new ExeWithLibraryUsingLibraryHelloWorldApp()

    def "setup"() {
        settingsFile << "rootProject.name = 'test'"

        buildFile << """
apply plugin: "cpp"
model {
    flavors {
        english
        french
        german
    }
    components {
        greetings(NativeLibrarySpec) {
            binaries.all {
                if (!org.gradle.internal.os.OperatingSystem.current().isWindows()) {
                    cppCompiler.args("-fPIC");
                }
            }
        }
        hello(NativeLibrarySpec) {
            binaries.all {
                lib library: 'greetings', linkage: 'static'
            }
        }
        main(NativeExecutableSpec) {
            binaries.all {
                lib library: 'hello'
            }
        }
    }
}
"""

        helloWorldApp.writeSources(file("src/main"), file("src/hello"), file("src/greetings"))
    }

    def "can configure components for a single flavor"() {
        given:
        buildFile << """
model {
    binaries {
        all {
            if (flavor == flavors.french) {
                cppCompiler.define "FRENCH"
            }
        }
    }
    components {
        main { targetFlavors "french" }
        hello { targetFlavors "french" }
        greetings { targetFlavors "french" }
    }
}
"""
        when:
        succeeds "installMainExecutable"

        then:
        installation("build/install/main").exec().out == FRENCH + " " + FRENCH
    }

    def "builds executable for each defined flavor when not configured for component"() {
        when:
        succeeds "installMainEnglishExecutable", "installMainFrenchExecutable", "installMainGermanExecutable"

        then:
        installation("build/install/main/english").assertInstalled()
        installation("build/install/main/french").assertInstalled()
        installation("build/install/main/german").assertInstalled()
    }

    def "executable with flavors depends on library with matching flavors"() {
        when:
        buildFile << """
model {
    components {
        main {
            targetFlavors "english", "french"
            binaries.all {
                if (flavor == flavors.french) {
                    cppCompiler.define "FRENCH"
                }
            }
        }
        withType(NativeLibrarySpec) {
            targetFlavors "english", "french"
            binaries.all {
                if (flavor == flavors.french) {
                    cppCompiler.define "FRENCH"
                }
            }
        }
    }
}
"""

        and:
        succeeds "installMainEnglishExecutable", "installMainFrenchExecutable"

        then:
        installation("build/install/main/english").exec().out == DEFAULT + " " + DEFAULT
        installation("build/install/main/french").exec().out == FRENCH + " " + FRENCH
    }

    def "build fails when library has no matching flavour"() {
        when:
        buildFile << """
apply plugin: "cpp"
model {
    components {
        hello {
            targetFlavors "english", "french"
        }
        main {
            targetFlavors "english", "german"
            binaries.all {
                lib library: 'hello'
            }
        }
    }
}
"""

        then:
        fails "mainGermanExecutable"
        failure.assertHasDescription("Could not determine the dependencies of task ':linkMainGermanExecutable'.")
        failure.assertHasCause("No shared library binary available for library 'hello' with [flavor: 'german', platform: '${NativePlatformsTestFixture.defaultPlatformName}', buildType: 'debug']")
    }

    def "fails with reasonable error message when trying to target an unknown flavor"() {
        when:
        buildFile << """
model {
    components {
        main { targetFlavors "unknown" }
    }
}
"""

        and:
        fails "mainExecutable"

        then:
        failure.assertHasCause("Exception thrown while executing model rule: NativeComponentModelPlugin.Rules#createBinaries")
        failure.assertHasCause("Invalid Flavor: 'unknown'")
    }
}
