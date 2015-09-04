/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture

class CustomComponentJarBinariesIntegrationTest extends AbstractIntegrationSpec {
    def "custom component defined by plugin is built from Java source using JVM component plugin" () {
        given:
        file("src/lib1/java/Lib1.java") << "public class Lib1 {}"
        file("src/lib1/resources/sample.properties") << "origin=lib1"

        file("src/lib2/java/Lib2.java") << "public class Lib2 {}"
        file("src/lib2/resources/sample.properties") << "origin=lib2"

        file("src/sampleLib/lib/Sample.java") << "public class Sample extends Lib1 {}"
        file("src/sampleLib/libResources/sample.properties") << "origin=sample"

        file("src/sampleLib/bin/Bin.java") << "public class Bin extends Lib2 {}"
        file("src/sampleLib/binResources/bin.properties") << "origin=bin"

        // These should not be included in the resulting JAR
        file("src/main/java/Java.java") << "public class Java {}"
        file("src/main/resources/java.properties") << "origin=java"

        buildFile << """
import org.gradle.jvm.platform.internal.DefaultJavaPlatform
import org.gradle.platform.base.internal.BinaryNamingSchemeBuilder

plugins {
    id 'jvm-component'
    id 'java-lang'
}

interface SampleLibrarySpec extends ComponentSpec {}

class DefaultSampleLibrarySpec extends BaseComponentSpec implements SampleLibrarySpec {}

class SampleLibraryRules extends RuleSource {
    @ComponentType
    void register(ComponentTypeBuilder<SampleLibrarySpec> builder) {
        builder.defaultImplementation(DefaultSampleLibrarySpec)
    }

    @ComponentBinaries
    public void createBinaries(ModelMap<JarBinarySpec> binaries, SampleLibrarySpec library,
                               BinaryNamingSchemeBuilder namingSchemeBuilder,
                               @Path("buildDir") File buildDir) {
        def platform = DefaultJavaPlatform.current()
        def binaryName = namingSchemeBuilder.withComponentName(library.name).withTypeString("jar").build().lifecycleTaskName
        binaries.create(binaryName) { binary ->
            binary.targetPlatform = platform
        }
    }
}

apply plugin: SampleLibraryRules

model {
    components {
        lib1(JvmLibrarySpec)
        lib2(JvmLibrarySpec)

        sampleLib(SampleLibrarySpec) {
            sources {
                lib(JavaSourceSet) {
                    dependencies {
                        library "lib1"
                    }
                }
                libResources(JvmResourceSet) {}
            }
            binaries {
                sampleLibJar {
                    sources {
                        bin(JavaSourceSet) {
                            source.srcDir "src/sampleLib/bin"
                            dependencies {
                                library "lib2"
                            }
                        }
                        binResources(JvmResourceSet) {
                            source.srcDir "src/sampleLib/binResources"
                        }
                    }
                }
            }
        }
    }
    tasks {
        create("validate") { task ->
            task.doLast {
                // Check for isolation of compiler source- and classpaths
                assert compileLib1JarLib1Java.source.files*.name == [ "Lib1.java" ]
                assert compileLib2JarLib2Java.source.files*.name == [ "Lib2.java" ]
                assert compileSampleLibJarSampleLibLib.source.files*.name == [ "Sample.java" ]
                assert compileSampleLibJarSampleLibBin.source.files*.name == [ "Bin.java" ]

                assert compileLib1JarLib1Java.classpath.files*.name == []
                assert compileLib2JarLib2Java.classpath.files*.name == []
                assert compileSampleLibJarSampleLibLib.classpath.files*.name == [ "lib1.jar" ]
                assert compileSampleLibJarSampleLibBin.classpath.files*.name == [ "lib2.jar" ]
            }
        }
    }
}
"""

        when:
        succeeds "sampleLibJar", "validate"

        then:
        executed ":lib1Jar", ":lib2Jar"
        new JarTestFixture(file("build/jars/sampleLibJar/sampleLib.jar")).hasDescendants(
            "Sample.class",
            "sample.properties",

            "Bin.class",
            "bin.properties"
        )
    }
}
