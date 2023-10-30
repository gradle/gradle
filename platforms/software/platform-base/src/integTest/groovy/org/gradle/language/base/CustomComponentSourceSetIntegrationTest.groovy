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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class CustomComponentSourceSetIntegrationTest extends AbstractIntegrationSpec {

    def "setup"() {
        buildFile << """
    @Managed interface SampleLibrary extends GeneralComponentSpec {}
    @Managed interface SampleBinary extends BinarySpec {}
    @Managed interface LibrarySourceSet extends LanguageSourceSet {}

    class MyBinaryDeclarationModel implements Plugin<Project> {
        void apply(final Project project) {}

        static class ComponentModel extends RuleSource {
            @ComponentType
            void registerLibrary(TypeBuilder<SampleLibrary> builder) {}

            @ComponentType
            void registerBinary(TypeBuilder<SampleBinary> builder) {}

            @ComponentType
            void registerSourceSet(TypeBuilder<LibrarySourceSet> builder) {
            }
        }
    }

    apply plugin:MyBinaryDeclarationModel
"""
    }

    def "source order is retained"() {
        buildFile << '''
class Dump extends RuleSource {
    @Mutate
    void tasks(ModelMap<Task> tasks, BinaryContainer binaries) {
        tasks.create("verify") {
            doLast {
                binaries.each { binary ->
                    println "Binary sources: ${binary.sources.values()}"
                    println "Binary inputs: ${binary.inputs}"
                }
            }
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
'''
        expect:
        succeeds "verify"
        output.contains """Binary sources: [Library source 'sampleLib:bin:binaryA', Library source 'sampleLib:bin:binaryB', Library source 'sampleLib:bin:binaryC', Library source 'sampleLib:bin:binaryD']"""
        output.contains """Binary inputs: [Library source 'sampleLib:compA', Library source 'sampleLib:compB', Library source 'sampleLib:compC', Library source 'sampleLib:compD', Library source 'sampleLib:bin:binaryA', Library source 'sampleLib:bin:binaryB', Library source 'sampleLib:bin:binaryC', Library source 'sampleLib:bin:binaryD']"""
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
                    }
                }
            }
        }
        sampleLib {
            binaries.bin.sources {
                main(LibrarySourceSet) {
                    source.srcDir "src/main/lib"
                }
            }
        }
    }
}
"""
        when:
        fails("components")

        then:
        failure.assertHasCause("Exception thrown while executing model rule: sampleLib { ... } @ build.gradle line 37, column 9")
        failure.assertHasCause("Cannot create 'components.sampleLib.binaries.bin.sources.main' using creation rule 'sampleLib { ... } @ build.gradle line 37, column 9 > create(main)' as the rule 'sampleLib(SampleLibrary) { ... } @ build.gradle line 26, column 9 > create(bin) > create(main)' is already registered to create this model element.")
    }

    def "user can attach unmanaged internal views to custom unmanaged `LanguageSourceSet`"() {
        given:
        buildFile << """
            interface HaxeSourceSet extends LanguageSourceSet {
                String getPublicData()
                void setPublicData(String data)
            }
            interface HaxeSourceSetInternal {
                String getInternalData()
                void setInternalData(String data)
            }
            class DefaultHaxeSourceSet extends BaseLanguageSourceSet implements HaxeSourceSet, HaxeSourceSetInternal {
                String publicData
                String internalData
            }

            class HaxeRules extends RuleSource {
                @ComponentType
                void registerHaxeLanguageSourceSetType(TypeBuilder<HaxeSourceSet> builder) {
                    builder.defaultImplementation(DefaultHaxeSourceSet)
                    builder.internalView(HaxeSourceSetInternal)
                }
            }
            apply plugin: HaxeRules

            model {
                components {
                    sampleLib(SampleLibrary) {
                        sources {
                            haxe(HaxeSourceSet) {
                                publicData = "public"
                            }
                        }
                    }
                }
            }

            class TestRules extends RuleSource {
                @Defaults
                void useInternalView(@Path("components.sampleLib.sources.haxe") HaxeSourceSetInternal lss) {
                    lss.setInternalData("internal")
                }
            }
            apply plugin: TestRules

            class ValidateTaskRules extends RuleSource {
                @Mutate
                void createValidateTask(ModelMap<Task> tasks, @Path("components.sampleLib.sources") ModelMap<LanguageSourceSet> sources) {
                    tasks.create("validate") {
                        assert sources.haxe != null
                        assert sources.haxe.publicData == "public"
                        assert sources.haxe.internalData == "internal"
                    }
                }
            }
            apply plugin: ValidateTaskRules
        """

        expect:
        succeeds "validate"
    }

    def "fails on creation when model type extends `LanguageSourceSet` without a default implementation"() {
        given:
        buildFile << """
            interface HaxeSourceSet extends LanguageSourceSet {}
            class HaxeRules extends RuleSource {
                @ComponentType
                void registerHaxeLanguageSourceSetType(TypeBuilder<HaxeSourceSet> builder) {
                }
            }
            apply plugin: HaxeRules

            model {
                components {
                    myComponent(GeneralComponentSpec) {
                        sources {
                            haxe(HaxeSourceSet)
                        }
                    }
                }
            }
        """

        when:
        fails "model"

        then:
        failure.assertHasCause("Cannot create a 'HaxeSourceSet' because this type does not have an implementation registered.")
    }

    def "user can declare and use a custom managed LanguageSourceSet"() {
        given:
        buildFile << """
            @Managed interface CustomManagedLSS extends LanguageSourceSet {
                String getSomeProperty()
                void setSomeProperty(String value)
            }
            class CustomManagedLSSPlugin extends RuleSource {
                @ComponentType
                void registerCustomManagedLSSType(TypeBuilder<CustomManagedLSS> builder) {
                }
            }
            apply plugin: CustomManagedLSSPlugin

            class TestRules extends RuleSource {
                @Defaults
                void useInternalView(@Path("components.sampleLib.sources.managed") CustomManagedLSS lss) {
                    lss.setSomeProperty("default value")
                }
            }
            apply plugin: TestRules

            model {
                components {
                    sampleLib(SampleLibrary) {
                        sources {
                            managed(CustomManagedLSS) {
                                someProperty = "some value"
                            }
                        }
                    }
                }
            }

            class ValidateTaskRules extends RuleSource {
                @Mutate
                void createValidateTask(ModelMap<Task> tasks, @Path("components.sampleLib.sources") ModelMap<LanguageSourceSet> sources) {
                    tasks.create("validate") {
                        assert sources.managed != null
                        assert sources.managed.someProperty == "some value"
                    }
                }
            }
            apply plugin: ValidateTaskRules
        """

        expect:
        succeeds "validate"
    }

    def "user can declare custom managed LanguageSourceSet based on custom LanguageSourceSet component"() {
        given:
        buildFile << """
            @Managed interface ChildCustomManagedLSS extends LanguageSourceSet {}
            class ChildCustomManagedLSSPlugin extends RuleSource {
                @ComponentType
                void registerChildCustomManagedLSSPType(TypeBuilder<ChildCustomManagedLSS> builder) {
                }
            }
            apply plugin: ChildCustomManagedLSSPlugin

            model {
                components {
                    sampleLib(SampleLibrary) {
                        sources {
                            childcustom(ChildCustomManagedLSS) {}
                        }
                    }
                }
            }

            class ValidateTaskRules extends RuleSource {
                @Mutate
                void createValidateTask(ModelMap<Task> tasks, @Path("components.sampleLib.sources") ModelMap<LanguageSourceSet> sources) {
                    tasks.create("validate") {
                        assert sources.childcustom != null
                    }
                }
            }
            apply plugin: ValidateTaskRules
        """

        expect:
        succeeds "validate"
    }

    def "user can target managed internal views of a custom managed LanguageSourceSet with rules"() {
        given:
        buildFile << """
            @Managed interface CustomManagedLSS extends LanguageSourceSet {}
            @Managed interface CustomManagedLSSInternal extends CustomManagedLSS {
                String getInternal()
                void setInternal(String internal)
            }
            class CustomManagedLSSPlugin extends RuleSource {
                @ComponentType
                void registerCustomManagedLSSType(TypeBuilder<CustomManagedLSS> builder) {
                    builder.internalView(CustomManagedLSSInternal)
                }
            }
            apply plugin: CustomManagedLSSPlugin

            class TestRules extends RuleSource {
                @Defaults
                void useInternalView(@Path("components.sampleLib.sources.managed") CustomManagedLSSInternal lss) {
                    lss.setInternal("internal value")
                }
            }
            apply plugin: TestRules

            model {
                components {
                    sampleLib(SampleLibrary) {
                        sources {
                            managed(CustomManagedLSS) {}
                        }
                    }
                }
            }

            class ValidateTaskRules extends RuleSource {
                @Mutate
                void createValidateTask(ModelMap<Task> tasks, @Path("components.sampleLib.sources") ModelMap<CustomManagedLSSInternal> sources) {
                    tasks.create("validate") {
                        assert sources.managed != null
                        assert sources.managed.internal == "internal value"
                    }
                }
            }
            apply plugin: ValidateTaskRules
        """

        expect:
        succeeds "validate"
    }
}
