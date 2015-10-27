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

import org.gradle.api.JavaVersion
import org.gradle.api.reporting.components.AbstractComponentReportIntegrationTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore

class PlatformJvmComponentReportIntegrationTest extends AbstractComponentReportIntegrationTest {
    private JavaVersion currentJvm = JavaVersion.current()
    private String currentJava = "Java SE " + currentJvm.majorVersion
    private String currentJdk = String.format("JDK %s (%s)", currentJvm.majorVersion, currentJvm);

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
        outputMatches output, """
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
        targetPlatform: $currentJava
        tool chain: $currentJdk
        Jar file: build/jars/someLibJar/someLib.jar
"""
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
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
        outputMatches output, """
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
        targetPlatform: Java SE 5
        tool chain: $currentJdk
        Jar file: build/jars/myLibJava5Jar/myLib.jar
    Jar 'myLib:java6Jar'
        build using task: :myLibJava6Jar
        targetPlatform: Java SE 6
        tool chain: $currentJdk
        Jar file: build/jars/myLibJava6Jar/myLib.jar
    Jar 'myLib:java7Jar'
        build using task: :myLibJava7Jar
        targetPlatform: Java SE 7
        tool chain: $currentJdk
        Jar file: build/jars/myLibJava7Jar/myLib.jar
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
        outputMatches output, """
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
        targetPlatform: Java SE 5
        tool chain: $currentJdk
        Jar file: build/jars/myLibJava5Jar/myLib.jar
    Jar 'myLib:java6Jar'
        build using task: :myLibJava6Jar
        targetPlatform: Java SE 6
        tool chain: $currentJdk
        Jar file: build/jars/myLibJava6Jar/myLib.jar
    Jar 'myLib:java9Jar' (not buildable)
        build using task: :myLibJava9Jar
        targetPlatform: Java SE 9
        tool chain: $currentJdk
        Jar file: build/jars/myLibJava9Jar/myLib.jar
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
        targetPlatform: Java SE 6
        tool chain: $currentJdk
        Jar file: build/jars/myLib2Jar/myLib2.jar
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
        outputMatches output, """
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
        targetPlatform: Java SE 5
        tool chain: $currentJdk
        Jar file: build/jars/someLibJava5Jar/someLib.jar
        source sets:
            Java source 'someLib:java2'
                srcDir: src/main/java2
                dependencies:
                    library 'some-library'
    Jar 'someLib:java6Jar'
        build using task: :someLibJava6Jar
        targetPlatform: Java SE 6
        tool chain: $currentJdk
        Jar file: build/jars/someLibJava6Jar/someLib.jar
"""
    }

    @Ignore
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
    @BinaryType
    void customJarBinary(BinaryTypeBuilder<CustomJarBinarySpec> builder) {
    }

    @Finalize
    void setPlatformForBinaries(ModelMap<BinarySpec> binaries) {
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
        outputMatches output, """
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
        buildType: debug
        flavor: free
        targetPlatform: $currentJava
        tool chain: $currentJdk
        Jar file: build/jars/someLibCustomJar/someLib.jar
    Jar 'someLib:jar'
        build using task: :someLibJar
        targetPlatform: $currentJava
        tool chain: $currentJdk
        Jar file: build/jars/someLibJar/someLib.jar
"""
    }
}
