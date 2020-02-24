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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithInstantExecution
import org.gradle.test.fixtures.archive.JarTestFixture

@UnsupportedWithInstantExecution(because = "software model")
class CustomJarBinarySpecSubtypeIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            plugins {
                id 'jvm-component'
            }
        """
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
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
        new JarTestFixture(file("build/jars/sampleLib/jar/sampleLib.jar")).isManifestPresentAndFirstEntry()
        new JarTestFixture(file("build/jars/sampleLib/customJar/sampleLib.jar")).isManifestPresentAndFirstEntry()
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
                def jarBinaries = []
                def customBinaries = []
            }

            class BinaryNameCollectorRules extends RuleSource {
                @Model
                Results results() { new Results() }

                @Mutate
                void printJarBinaries(Results results, @Path("binaries") ModelMap<JarBinarySpec> jarBinaries) {
                    for (JarBinarySpec jarBinary : jarBinaries) {
                        results.jarBinaries.add jarBinary.name
                    }
                }

                @Mutate
                void printCustomBinaries(Results results, @Path("binaries") ModelMap<CustomChildJarBinarySpec> customBinaries) {
                    for (CustomChildJarBinarySpec customBinary : customBinaries) {
                        results.customBinaries.add customBinary.name
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
                        def results = \$.results
                        assert results.jarBinaries == ["customJar", "jar"]
                        assert results.customBinaries == ["customJar"]
                    }
                }
            }
        """

        expect:
        succeeds "assemble", "validate"
        new JarTestFixture(file("build/jars/sampleLib/jar/sampleLib.jar")).isManifestPresentAndFirstEntry()
        new JarTestFixture(file("build/jars/sampleLib/customJar/sampleLib.jar")).isManifestPresentAndFirstEntry()
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
        succeeds "sampleLibCustomJar"
        new JarTestFixture(file("build/jars/sampleLib/customJar/sampleLib.jar")).isManifestPresentAndFirstEntry()
    }

    // TODO:LPTR There is a deeper breakage here, as creating Jar binaries in the top-level binaries container would result in an NPE anyway,
    // as those binaries would have no component associated with them.
    @NotYetImplemented
    def "managed JarBinarySpec subtype cannot be created via BinaryContainer"() {
        given:
        buildFile << """
            ${registerCustomJarBinaryType()}

            model {
                binaries {
                    customJar(CustomJarBinarySpec)
                }
            }
        """

        expect:
        def ex = fails "customJar"
        ex.assertHasCause "Cannot create a CustomJarBinarySpec because this type is not known to this container. Known types are: JarBinarySpec"
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
        fails "components"
        failure.assertHasCause """Type IllegalJarBinarySpec is not a valid managed type:
- Method sayHello(java.lang.String) is not a valid managed type method: it must have an implementation"""
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
                @ComponentType
                void customJarBinary(TypeBuilder<${binaryType}> builder) {
                }

                @Finalize
                void setPlatformForBinaries(BinaryContainer binaries) {
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
