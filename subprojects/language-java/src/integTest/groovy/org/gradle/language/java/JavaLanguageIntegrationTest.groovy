/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.java

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.integtests.language.AbstractJvmLanguageIntegrationTest
import org.gradle.jvm.platform.internal.DefaultJavaPlatform
import org.gradle.language.fixtures.BadJavaComponent
import org.gradle.language.fixtures.TestJavaComponent
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class JavaLanguageIntegrationTest extends AbstractJvmLanguageIntegrationTest {
    TestJvmComponent app = new TestJavaComponent()

    def "reports failure to compile bad java sources"() {
        when:
        def badApp = new BadJavaComponent()
        badApp.sources*.writeToDir(file("src/myLib/java"))

        and:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec)
                }
            }
        """
        then:
        fails "assemble"

        and:
        badApp.compilerErrors.each {
            assert failure.assertHasErrorOutput(it)
        }
    }

    def "target should produce in the correct bytecode"() {
        when:
        app.sources*.writeToDir(file("src/myLib/java"))

        and:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        targetPlatform "java7"
                    }
                }
            }
        """
        then:
        succeeds "assemble"

        and:
        jarFile("build/jars/myLib/jar/myLib.jar").javaVersion == JavaVersion.VERSION_1_7
        jarFile("build/jars/myLib/jar/myLib.jar").hasDescendants(app.sources*.classFile.fullPath as String[])
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    def "multiple targets should produce in the correct bytecode"() {
        when:
        app.writeSources(file("src/myLib"))

        and:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        targetPlatform "java7"
                        targetPlatform "java8"
                        targetPlatform "java9"
                    }
                }
            }
        """
        then:
        succeeds "assemble"

        and:
        jarFile("build/jars/myLib/java7Jar/myLib.jar").javaVersion == JavaVersion.VERSION_1_7
        jarFile("build/jars/myLib/java8Jar/myLib.jar").javaVersion == JavaVersion.VERSION_1_8
        jarFile("build/jars/myLib/java9Jar/myLib.jar").javaVersion == JavaVersion.VERSION_1_9
    }

    def "erroneous target should produce reasonable error message"() {
        def badName = "bornYesterday"

        when:
        def badApp = new BadJavaComponent()
        badApp.sources*.writeToDir(file("src/myLib/java"))

        and:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        targetPlatform "$badName"
                    }
                }
            }
        """
        then:
        fails "assemble"

        and:
        failure.assertHasCause("Invalid JavaPlatform: $badName")
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "builds all buildable and skips non-buildable platforms when assembling"() {
        def current = DefaultJavaPlatform.current()
        when:
        app.sources*.writeToDir(file("src/myLib/java"))

        and:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        targetPlatform "${current.name}"
                        targetPlatform "java9"
                    }
                }
            }
        """
        then:
        succeeds "assemble"

        and:
        jarFile("build/jars/myLib/${current.name}Jar/myLib.jar").javaVersion == current.targetCompatibility
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "too high JDK target should produce reasonable error message"() {
        when:
        app.sources*.writeToDir(file("src/myLib/java"))

        and:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        targetPlatform "java9"
                    }
                }
            }
        """
        then:
        fails "myLibJar"

        and:
        failure.assertHasCause("Could not target platform: 'Java SE 9' using tool chain: " +
            "'JDK ${JavaVersion.current().majorVersion} (${JavaVersion.current()})'")
    }
}
