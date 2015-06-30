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

package org.gradle.language.base

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CustomComponentSourceSetIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        buildFile << """
interface SampleBinary extends BinarySpec {}

interface LibrarySourceSet extends LanguageSourceSet {}

class DefaultLibrarySourceSet extends BaseLanguageSourceSet implements LibrarySourceSet { }

class DefaultSampleBinary extends BaseBinarySpec implements SampleBinary {}

interface SampleLibrary extends ComponentSpec {}

class DefaultSampleLibrary extends BaseComponentSpec implements SampleLibrary {}

    class MyBinaryDeclarationModel implements Plugin<Project> {
        void apply(final Project project) {}

        static class ComponentModel extends RuleSource {
            @ComponentType
            void register(ComponentTypeBuilder<SampleLibrary> builder) {
                builder.defaultImplementation(DefaultSampleLibrary)
            }

            @BinaryType
            void register(BinaryTypeBuilder<SampleBinary> builder) {
                builder.defaultImplementation(DefaultSampleBinary)
            }

            @LanguageType
            void registerSourceSet(LanguageTypeBuilder<LibrarySourceSet> builder) {
                builder.setLanguageName("librarySource")
                builder.defaultImplementation(DefaultLibrarySourceSet)
            }
        }
    }

    apply plugin:MyBinaryDeclarationModel
"""
    }

    def "source order is retained"() {
        buildFile << """
class Dump extends RuleSource {

    @Validate
    void checkBinaries(BinaryContainer binaries) {
        binaries.each { binary ->
            println "Binary sources: \${binary.sources.values()}"
            println "Binary inputs: \${binary.inputs}"
        }
    }
}

apply plugin: Dump

model {
    components {
        sampleLib(SampleLibrary) {
            sources {
                compA(LibrarySourceSet) {
                    source.srcDir "src/main/comp-a"
                }
                compB(LibrarySourceSet) {
                    source.srcDir "src/main/comp-b"
                }
            }
            binaries {
                bin(SampleBinary) {
                    sources {
                        binaryA(LibrarySourceSet) {
                            source.srcDir "src/main/binary-a"
                        }
                        binaryB(LibrarySourceSet) {
                            source.srcDir "src/main/binary-b"
                        }
                    }
                }
            }
            sources {
                compC(LibrarySourceSet) {
                    source.srcDir "src/main/comp-c"
                }
            }
            binaries {
                bin {
                    sources {
                        binaryC(LibrarySourceSet) {
                            source.srcDir "src/main/binary-c"
                        }
                    }
                }
            }
        }
    }
    components {
        sampleLib {
            sources {
                compD(LibrarySourceSet) {
                    source.srcDir "src/main/comp-d"
                }
            }
            binaries {
                bin {
                    sources {
                        binaryD(LibrarySourceSet) {
                            source.srcDir "src/main/binary-d"
                        }
                    }
                }
            }
        }
    }
}
"""
        expect:
        succeeds "components"
        output.contains """Binary sources: [DefaultLibrarySourceSet 'sampleLib:binaryA', DefaultLibrarySourceSet 'sampleLib:binaryB', DefaultLibrarySourceSet 'sampleLib:binaryC', DefaultLibrarySourceSet 'sampleLib:binaryD']"""
        output.contains """Binary inputs: [DefaultLibrarySourceSet 'sampleLib:compA', DefaultLibrarySourceSet 'sampleLib:compB', DefaultLibrarySourceSet 'sampleLib:compC', DefaultLibrarySourceSet 'sampleLib:compD', DefaultLibrarySourceSet 'sampleLib:binaryA', DefaultLibrarySourceSet 'sampleLib:binaryB', DefaultLibrarySourceSet 'sampleLib:binaryC', DefaultLibrarySourceSet 'sampleLib:binaryD']"""
    }

    def "fail when multiple source sets are registered with the same name"() {
        buildFile << """
model {
    components {
        sampleLib(SampleLibrary) {
            binaries {
                bin(SampleBinary) {
                    sources {
                        main(LibrarySourceSet) {
                            source.srcDir "src/main/lib"
                        }
                        main(LibrarySourceSet) {
                            source.srcDir "src/main/lib"
                        }
                    }
                }
            }
        }
    }
}
"""
        when:
        def failure = fails("components")

        then:
        failure.assertHasCause "Entry with name already exists: main"
    }
}
