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
import org.gradle.integtests.fixtures.UnsupportedWithInstantExecution

@UnsupportedWithInstantExecution(because = "software model")
class CustomBinaryInternalViewsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        buildFile << """
            apply plugin: "jvm-component"

            interface SampleBinarySpec extends BinarySpec {
                String getPublicData()
                void setPublicData(String publicData)
            }

            interface SampleBinarySpecInternal extends BinarySpec {
                String getInternalData()
                void setInternalData(String internalData)
            }

            interface BareInternal {
                String getBareData()
                void setBareData(String bareData)
            }

            class DefaultSampleBinarySpec extends BaseBinarySpec implements SampleBinarySpec, SampleBinarySpecInternal, BareInternal {
                String internalData
                String publicData
                String bareData
            }
        """
    }

    def setupRegistration() {
        buildFile << """
            class RegisterBinaryRules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleBinarySpec> builder) {
                    builder.defaultImplementation(DefaultSampleBinarySpec)
                    builder.internalView(SampleBinarySpecInternal)
                    builder.internalView(BareInternal)
                }
            }
            apply plugin: RegisterBinaryRules
        """
    }

    def setupModel() {
        buildFile << """
            model {
                components {
                    sampleLib(JvmLibrarySpec) {
                        binaries {
                            sampleBin(SampleBinarySpec)
                        }
                    }
                }
            }
        """
    }

    def setupValidateTask() {
        buildFile << """
        class ValidateTaskRules extends RuleSource {
            @Mutate
            void createValidateTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                tasks.create("validate") {
                    def binaries = components.sampleLib.binaries
                    assert binaries*.name == ["jar", "sampleBin"]
                    assert binaries.withType(BinarySpec)*.name == ["jar", "sampleBin"]
                    assert binaries.withType(JvmBinarySpec)*.name == ["jar"]
                    assert binaries.withType(SampleBinarySpec)*.name == ["sampleBin"]
                    assert binaries.withType(SampleBinarySpecInternal)*.name == ["sampleBin"]
                    assert binaries.withType(BareInternal)*.name == ["sampleBin"]
                }
            }
        }
        apply plugin: ValidateTaskRules
        """
    }

    def "can target top-level internal view with rules"() {
        setupRegistration()
        setupModel()

        buildFile << """

        class Rules extends RuleSource {
            @Finalize
            void mutateInternal(@Path("binaries") ModelMap<SampleBinarySpecInternal> sampleBins) {
                sampleBins.all { sampleBin ->
                    sampleBin.internalData = "internal"
                }
            }

            @Finalize
            void mutatePublic(@Path("binaries") ModelMap<SampleBinarySpec> sampleBins) {
                sampleBins.all { sampleBin ->
                    sampleBin.publicData = "public"
                }
                sampleBins.withType(BareInternal) { sampleBin ->
                    bareData = "bare"
                }
            }

            @Mutate
            void createValidateTask(ModelMap<Task> tasks, @Path("binaries") ModelMap<SampleBinarySpecInternal> sampleLibs) {
                tasks.create("validate") {
                    assert sampleLibs.size() == 1
                    sampleLibs.each { sampleLib ->
                        assert sampleLib.internalData == "internal"
                        assert sampleLib.publicData == "public"
                        assert sampleLib.bareData == "bare"
                    }
                }
            }
        }
        apply plugin: Rules
        """
        expect:
        succeeds "validate"
    }

    def "can target component's binaries via withType()"() {
        setupRegistration()
        setupModel()

        buildFile << """

        class Rules extends RuleSource {
            @Mutate
            void mutateInternal(ComponentSpecContainer libs) {
                libs.all { lib ->
                    lib.binaries.withType(SampleBinarySpecInternal) { sampleBin ->
                        sampleBin.internalData = "internal"
                    }
                }
            }

            @Mutate
            void mutatePublic(ComponentSpecContainer libs) {
                libs.all { lib ->
                    lib.binaries.withType(SampleBinarySpec) { sampleBin ->
                        sampleBin.publicData = "public"
                    }
                }
            }

            @Mutate
            void createValidateTask(ModelMap<Task> tasks, @Path("binaries") ModelMap<SampleBinarySpecInternal> sampleLibs) {
                tasks.create("validate") {
                    assert sampleLibs.size() == 1
                    sampleLibs.each { sampleLib ->
                        assert sampleLib.internalData == "internal"
                        assert sampleLib.publicData == "public"
                    }
                }
            }
        }
        apply plugin: Rules
        """
        expect:
        succeeds "validate"
    }

    def "can filter for custom internal view with BinarySpecContainer.withType()"() {
        setupRegistration()
        setupModel()
        setupValidateTask()

        expect:
        succeeds "validate"
    }

    def "can register internal view and default implementation separately"() {
        buildFile << """
            class RegisterBinaryRules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleBinarySpec> builder) {
                    builder.defaultImplementation(DefaultSampleBinarySpec)
                }

                @ComponentType
                void registerInternalView(TypeBuilder<SampleBinarySpec> builder) {
                    builder.internalView(SampleBinarySpecInternal)
                    builder.internalView(BareInternal)
                }
            }
            apply plugin: RegisterBinaryRules
        """

        setupModel()
        setupValidateTask()

        expect:
        succeeds "validate"
    }

    def "fails when wrong internal view is registered separately"() {
        buildFile << """
            interface NotImplementedInternalView extends BinarySpec {}

            class RegisterBinaryRules extends RuleSource {
                @ComponentType
                void registerBinary(TypeBuilder<SampleBinarySpec> builder) {
                    builder.defaultImplementation(DefaultSampleBinarySpec)
                }

                @ComponentType
                void registerInternalView(TypeBuilder<SampleBinarySpec> builder) {
                    builder.internalView(NotImplementedInternalView)
                }
            }
            apply plugin: RegisterBinaryRules
        """

        setupModel()

        expect:
        def failure = fails("components")
        failure.assertHasCause "Factory registration for 'SampleBinarySpec' is invalid because the implementation type 'DefaultSampleBinarySpec' does not implement internal view 'NotImplementedInternalView', implementation type was registered by RegisterBinaryRules#registerBinary(TypeBuilder<SampleBinarySpec>), internal view was registered by RegisterBinaryRules#registerInternalView(TypeBuilder<SampleBinarySpec>)"
    }

    def "can register managed internal view for JarBinarySpec"() {
        buildFile << """
            @Managed
            interface ManagedInternalView {
                String getInternalData()
                void setInternalData(String internalData)
            }

            @Managed
            interface ManagedJarBinarySpecInternal extends JarBinarySpec {
                String getInternalJarData()
                void setInternalJarData(String internalJarData)
            }

            class Rules extends RuleSource {
                @ComponentType
                void registerBinary(TypeBuilder<JarBinarySpec> builder) {
                    builder.internalView(ManagedInternalView)
                    builder.internalView(ManagedJarBinarySpecInternal)
                }

                @Mutate
                void createValidateTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                    tasks.create("validate") {
                        doLast {
                            assert components.size() == 1
                            components.each { component ->
                                def internals = component.binaries.withType(ManagedInternalView)
                                assert internals.size() == 1
                                internals.each { binary ->
                                    assert binary instanceof JarBinarySpec
                                    assert !(binary instanceof ManagedJarBinarySpecInternal)
                                    assert binary.name == "jar"
                                    assert binary.internalData == "internal"
                                }

                                def internalJars = component.binaries.withType(ManagedJarBinarySpecInternal)
                                assert internalJars.size() == 1
                                internalJars.each { binary ->
                                    assert binary instanceof JarBinarySpec
                                    assert !(binary instanceof ManagedInternalView)
                                    assert binary.name == "jar"
                                    assert binary.internalJarData == "internalJar"
                                }
                            }
                        }
                    }
                }
            }
            apply plugin: Rules

            model {
                components {
                    sampleLib(JvmLibrarySpec) {
                        binaries.withType(ManagedInternalView) { binary ->
                            binary.internalData = "internal"
                        }
                        binaries.withType(ManagedJarBinarySpecInternal) { binary ->
                            binary.internalJarData = "internalJar"
                        }
                    }
                }
            }
        """

        expect:
        succeeds "validate"
    }
}
