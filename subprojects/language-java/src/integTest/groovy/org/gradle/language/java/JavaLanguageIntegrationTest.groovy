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
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@LeaksFileHandles
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
            assert errorOutput.contains(it)
        }
    }

    @Requires(TestPrecondition.JDK6_OR_LATER)
    def "target should produce in the correct bytecode"() {
        when:
        app.sources*.writeToDir(file("src/myLib/java"))

        and:
        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec) {
                targetPlatform "java6"
            }
        }
    }
"""
        then:
        succeeds "assemble"

        and:
        jarFile("build/jars/myLibJar/myLib.jar").getJavaVersion() == JavaVersion.VERSION_1_6
        and:
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(app.sources*.classFile.fullPath as String[])
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
                targetPlatform "java5"
                targetPlatform "java6"
                targetPlatform "java7"
                targetPlatform "java8"
            }
        }
    }
"""
        then:
        succeeds "assemble"

        and:
        jarFile("build/jars/java5MyLibJar/myLib.jar").javaVersion == JavaVersion.VERSION_1_5
        and:
        jarFile("build/jars/java6MyLibJar/myLib.jar").javaVersion == JavaVersion.VERSION_1_6
        and:
        jarFile("build/jars/java7MyLibJar/myLib.jar").javaVersion == JavaVersion.VERSION_1_7
        and:
        jarFile("build/jars/java8MyLibJar/myLib.jar").javaVersion == JavaVersion.VERSION_1_8
    }

    def "erroneous target should produce reasonable error message"() {
        String badName = "bornYesterday";
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
        assert failure.assertHasCause("Invalid JavaPlatform: $badName")
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
        jarFile("build/jars/${current.name}MyLibJar/myLib.jar").javaVersion == current.targetCompatibility
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
        assert failure.assertHasCause("Could not target platform: 'Java SE 9' using tool chain: 'JDK ${JavaVersion.current().majorVersion} (${JavaVersion.current()})'")
    }
}
