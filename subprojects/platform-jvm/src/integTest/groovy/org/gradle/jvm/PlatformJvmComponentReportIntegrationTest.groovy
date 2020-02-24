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
package org.gradle.jvm

import org.gradle.api.reporting.components.AbstractComponentReportIntegrationTest
import org.gradle.integtests.fixtures.UnsupportedWithInstantExecution
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@UnsupportedWithInstantExecution(because = "software model")
class PlatformJvmComponentReportIntegrationTest extends AbstractComponentReportIntegrationTest {

    def setup() {
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The java-lang plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The jvm-resources plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
    }

    def "shows details of Java library"() {
        given:
        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        someLib(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'library-only'
                        project 'project-only'
                        library 'some-library' project 'some-project'
                        module 'org.ow2.asm:asm:5.0.4'
                    }
                }
            }
        }
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches """
JVM library 'someLib'
---------------------

Source sets
    Java source 'someLib:java'
        srcDir: src/someLib/java
        dependencies:
            library 'library-only'
            project 'project-only'
            project 'some-project' library 'some-library'
    JVM resources 'someLib:resources'
        srcDir: src/someLib/resources

Binaries
    Jar 'someLib:jar'
        build using task: :someLibJar
        target platform: $currentJava
        tool chain: $currentJdk
        classes dir: build/classes/someLib/jar
        resources dir: build/resources/someLib/jar
        API Jar file: build/jars/someLib/jar/api/someLib.jar
        Jar file: build/jars/someLib/jar/someLib.jar
"""
    }

    def "shows details of jvm library with multiple targets"() {
        given:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    model {
        components {
            myLib(JvmLibrarySpec) {
                targetPlatform "java5"
                targetPlatform "java6"
                targetPlatform "java7"
            }
        }
    }
"""
        when:
        succeeds "components"

        then:
        outputMatches """
JVM library 'myLib'
-------------------

Source sets
    Java source 'myLib:java'
        srcDir: src/myLib/java
    JVM resources 'myLib:resources'
        srcDir: src/myLib/resources

Binaries
    Jar 'myLib:java5Jar'
        build using task: :myLibJava5Jar
        target platform: Java SE 5
        tool chain: $currentJdk
        classes dir: build/classes/myLib/java5Jar
        resources dir: build/resources/myLib/java5Jar
        API Jar file: build/jars/myLib/java5Jar/api/myLib.jar
        Jar file: build/jars/myLib/java5Jar/myLib.jar
    Jar 'myLib:java6Jar'
        build using task: :myLibJava6Jar
        target platform: Java SE 6
        tool chain: $currentJdk
        classes dir: build/classes/myLib/java6Jar
        resources dir: build/resources/myLib/java6Jar
        API Jar file: build/jars/myLib/java6Jar/api/myLib.jar
        Jar file: build/jars/myLib/java6Jar/myLib.jar
    Jar 'myLib:java7Jar'
        build using task: :myLibJava7Jar
        target platform: Java SE 7
        tool chain: $currentJdk
        classes dir: build/classes/myLib/java7Jar
        resources dir: build/resources/myLib/java7Jar
        API Jar file: build/jars/myLib/java7Jar/api/myLib.jar
        Jar file: build/jars/myLib/java7Jar/myLib.jar
"""
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "shows which jvm libraries are buildable"() {
        given:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    model {
        components {
            myLib(JvmLibrarySpec) {
                targetPlatform "java5"
                targetPlatform "java6"
                targetPlatform "java9"
            }
            myLib2(JvmLibrarySpec) {
                targetPlatform "java6"
                binaries.all { buildable = false }
            }
        }
    }
"""
        when:
        succeeds "components"

        then:
        outputMatches """
JVM library 'myLib'
-------------------

Source sets
    Java source 'myLib:java'
        srcDir: src/myLib/java
    JVM resources 'myLib:resources'
        srcDir: src/myLib/resources

Binaries
    Jar 'myLib:java5Jar'
        build using task: :myLibJava5Jar
        target platform: Java SE 5
        tool chain: $currentJdk
        classes dir: build/classes/myLib/java5Jar
        resources dir: build/resources/myLib/java5Jar
        API Jar file: build/jars/myLib/java5Jar/api/myLib.jar
        Jar file: build/jars/myLib/java5Jar/myLib.jar
    Jar 'myLib:java6Jar'
        build using task: :myLibJava6Jar
        target platform: Java SE 6
        tool chain: $currentJdk
        classes dir: build/classes/myLib/java6Jar
        resources dir: build/resources/myLib/java6Jar
        API Jar file: build/jars/myLib/java6Jar/api/myLib.jar
        Jar file: build/jars/myLib/java6Jar/myLib.jar
    Jar 'myLib:java9Jar' (not buildable)
        build using task: :myLibJava9Jar
        target platform: Java SE 9
        tool chain: $currentJdk
        classes dir: build/classes/myLib/java9Jar
        resources dir: build/resources/myLib/java9Jar
        API Jar file: build/jars/myLib/java9Jar/api/myLib.jar
        Jar file: build/jars/myLib/java9Jar/myLib.jar
        Could not target platform: 'Java SE 9' using tool chain: '${currentJdk}'.

JVM library 'myLib2'
--------------------

Source sets
    Java source 'myLib2:java'
        srcDir: src/myLib2/java
    JVM resources 'myLib2:resources'
        srcDir: src/myLib2/resources

Binaries
    Jar 'myLib2:jar' (not buildable)
        build using task: :myLib2Jar
        target platform: Java SE 6
        tool chain: $currentJdk
        classes dir: build/classes/myLib2/jar
        resources dir: build/resources/myLib2/jar
        API Jar file: build/jars/myLib2/jar/api/myLib2.jar
        Jar file: build/jars/myLib2/jar/myLib2.jar
        Disabled by user
"""
    }

    def "shows owned sources of a Jar binary"() {
        given:
        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        someLib(JvmLibrarySpec) {
            targetPlatform "java5"
            targetPlatform "java6"
            binaries {
                all {
                    if (targetPlatform.name == "java5") {
                        sources {
                            java2(JavaSourceSet) {
                                source.srcDir "src/main/java2"
                                dependencies {
                                    library 'some-library'
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches """
JVM library 'someLib'
---------------------

Source sets
    Java source 'someLib:java'
        srcDir: src/someLib/java
    JVM resources 'someLib:resources'
        srcDir: src/someLib/resources

Binaries
    Jar 'someLib:java5Jar'
        build using task: :someLibJava5Jar
        target platform: Java SE 5
        tool chain: $currentJdk
        classes dir: build/classes/someLib/java5Jar
        resources dir: build/resources/someLib/java5Jar
        API Jar file: build/jars/someLib/java5Jar/api/someLib.jar
        Jar file: build/jars/someLib/java5Jar/someLib.jar
        source sets:
            Java source 'someLib:java5Jar:java2'
                srcDir: src/main/java2
                dependencies:
                    library 'some-library'
    Jar 'someLib:java6Jar'
        build using task: :someLibJava6Jar
        target platform: Java SE 6
        tool chain: $currentJdk
        classes dir: build/classes/someLib/java6Jar
        resources dir: build/resources/someLib/java6Jar
        API Jar file: build/jars/someLib/java6Jar/api/someLib.jar
        Jar file: build/jars/someLib/java6Jar/someLib.jar
"""
    }

    def "shows details of Java library with custom variants"() {
        given:
        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
}

class BuildType implements Named {
    String name
}

@Managed
interface CustomJarBinarySpec extends JarBinarySpec {
    @Unmanaged @Variant
    BuildType getBuildType()
    void setBuildType(BuildType buildType)

    @Variant
    String getFlavor()
    void setFlavor(String flavor)
}

import org.gradle.jvm.platform.internal.DefaultJavaPlatform

class Rules extends RuleSource {
    @ComponentType
    void customJarBinary(TypeBuilder<CustomJarBinarySpec> builder) {
    }

    @Finalize
    void setPlatformForBinaries(BinaryContainer binaries) {
        def platform = DefaultJavaPlatform.current()
        binaries.withType(CustomJarBinarySpec).beforeEach { binary ->
            binary.targetPlatform = platform
        }
    }
}

apply plugin: Rules

model {
    components {
        someLib(JvmLibrarySpec) {
            binaries {
                customJar(CustomJarBinarySpec) { binary ->
                    binary.buildType = new BuildType(name: "debug")
                    binary.flavor = "free"
                }
            }
        }
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches """
JVM library 'someLib'
---------------------

Source sets
    Java source 'someLib:java'
        srcDir: src/someLib/java
    JVM resources 'someLib:resources'
        srcDir: src/someLib/resources

Binaries
    Jar 'someLib:customJar'
        build using task: :someLibCustomJar
        build type: debug
        flavor: free
        target platform: $currentJava
        tool chain: $currentJdk
        classes dir: build/classes/someLib/customJar
        resources dir: build/resources/someLib/customJar
        API Jar file: build/jars/someLib/customJar/api/someLib.jar
        Jar file: build/jars/someLib/customJar/someLib.jar
    Jar 'someLib:jar'
        build using task: :someLibJar
        target platform: $currentJava
        tool chain: $currentJdk
        classes dir: build/classes/someLib/jar
        resources dir: build/resources/someLib/jar
        API Jar file: build/jars/someLib/jar/api/someLib.jar
        Jar file: build/jars/someLib/jar/someLib.jar
"""
    }
}
