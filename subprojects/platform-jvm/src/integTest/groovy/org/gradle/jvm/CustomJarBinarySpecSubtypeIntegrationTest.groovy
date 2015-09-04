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

package org.gradle.jvm
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture

class CustomJarBinarySpecSubtypeIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            plugins {
                id 'jvm-component'
            }
        """
    }

    def "can create a Jar from a managed JarBinarySpec subtype"() {
        given:
        buildFile << """
            ${registerCustomJarBinaryType()}

            model {
                components {
                    sampleLib(JvmLibrarySpec) {
                        binaries {
                            customJar(CustomJarBinarySpec) { binary ->
                                binary.value = "12"
                                assert binary.value == "12"
                            }
                        }
                    }
                }
            }
        """

        expect:
        succeeds "assemble"
        new JarTestFixture(file("build/jars/sampleLibJar/sampleLib.jar")).isManifestPresentAndFirstEntry()
        new JarTestFixture(file("build/jars/customJar/sampleLib.jar")).isManifestPresentAndFirstEntry()
    }

    def "managed JarBinarySpec subtypes can have further subtypes"() {
        given:
        buildFile << """
            ${registerCustomJarBinaryType()}

            @Managed
            interface CustomParentJarBinarySpec extends CustomJarBinarySpec {
                String getParentValue()
                void setParentValue(String value)
            }

            @Managed
            interface CustomChildJarBinarySpec extends CustomParentJarBinarySpec {
                String getChildValue()
                void setChildValue(String value)
            }

            ${registerBinaryType("CustomChildJarBinarySpec")}

            class Results {
                static def jarBinaries = []
                static def customBinaries = []
            }

            class BinaryNameCollectorRules extends RuleSource {
                @Finalize
                void printJarBinaries(ModelMap<JarBinarySpec> jarBinaries) {
                    for (JarBinarySpec jarBinary : jarBinaries) {
                        Results.jarBinaries.add jarBinary.name
                    }
                }

                @Finalize
                void printCustomBinaries(ModelMap<CustomChildJarBinarySpec> customBinaries) {
                    for (CustomChildJarBinarySpec customBinary : customBinaries) {
                        Results.customBinaries.add customBinary.name
                    }
                }
            }

            apply plugin: BinaryNameCollectorRules

            model {
                components {
                    sampleLib(JvmLibrarySpec) {
                        binaries {
                            customJar(CustomChildJarBinarySpec) { binary ->
                                binary.value = "12"
                                assert binary.value == "12"

                                binary.parentValue = "Lajos"
                                assert binary.parentValue == "Lajos"

                                binary.childValue = "Tibor"
                                assert binary.childValue == "Tibor"
                            }
                        }
                    }
                }
                tasks {
                    create("validate") {
                        dependsOn "assemble"
                        assert Results.jarBinaries ==    ["customJar", "sampleLibJar"]
                        assert Results.customBinaries == ["customJar"]
                    }
                }
            }
        """

        expect:
        succeeds "assemble", "validate"
        new JarTestFixture(file("build/jars/sampleLibJar/sampleLib.jar")).isManifestPresentAndFirstEntry()
        new JarTestFixture(file("build/jars/customJar/sampleLib.jar")).isManifestPresentAndFirstEntry()
    }


    def "managed JarBinarySpec subtypes can have @Unmanaged properties"() {
        given:
        buildFile << """
            @Managed
            interface CustomChildJarBinarySpec extends JarBinarySpec {
                @Unmanaged
                InputStream getThing()
                void setThing(InputStream thing)
            }

            ${registerBinaryType("CustomChildJarBinarySpec")}

            model {
                components {
                    sampleLib(JvmLibrarySpec) {
                        binaries {
                            customJar(CustomChildJarBinarySpec) { binary ->
                                def stream = new ByteArrayInputStream(new byte[0])
                                binary.thing = stream
                                assert binary.thing == stream
                            }
                        }
                    }
                }
            }
        """

        expect:
        succeeds "customJar"
        new JarTestFixture(file("build/jars/customJar/sampleLib.jar")).isManifestPresentAndFirstEntry()
    }

    def "managed JarBinarySpec subtype cannot be created via BinaryContainer"() {
        given:
        buildFile << """
            ${registerCustomJarBinaryType()}

            binaries {
                customJar(CustomJarBinarySpec)
            }
        """

        expect:
        def ex = fails "customJar"
        ex.assertHasCause "Cannot create a CustomJarBinarySpec because this type is not known to this container. Known types are: (None)"
    }

    def "illegal managed subtype yields error at rule execution time"() {
        given:
        buildFile << """
            @Managed
            interface IllegalJarBinarySpec extends JarBinarySpec {
                void sayHello(String person)
            }

            ${registerBinaryType("IllegalJarBinarySpec")}

            model {
                components {
                    sampleLib(JvmLibrarySpec) {
                        binaries {
                            illegalJar(IllegalJarBinarySpec)
                        }
                    }
                }
            }
        """

        expect:
        def ex = fails "components"
        ex.assertHasCause "Invalid managed model type IllegalJarBinarySpec: only paired getter/setter methods are supported (invalid methods: void IllegalJarBinarySpec#sayHello(java.lang.String))."
    }

    def registerCustomJarBinaryType() {
        return """
            @Managed
            interface CustomJarBinarySpec extends JarBinarySpec {
                String getValue()
                void setValue(String value)
            }

            ${registerBinaryType("CustomJarBinarySpec")}
        """
    }

    def registerBinaryType(String binaryType) {
        return """
            import org.gradle.jvm.platform.internal.DefaultJavaPlatform

            class ${binaryType}Rules extends RuleSource {
                @BinaryType
                void customJarBinary(BinaryTypeBuilder<${binaryType}> builder) {
                }

                @Finalize
                void setPlatformForBinaries(ModelMap<BinarySpec> binaries) {
                    def platform = DefaultJavaPlatform.current()
                    binaries.withType(${binaryType}).beforeEach { binary ->
                        binary.targetPlatform = platform
                    }
                }
            }

            apply plugin: ${binaryType}Rules
        """
    }
}
