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

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.platform.base.ApplicationSpec
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.GeneralComponentSpec
import org.gradle.platform.base.LibrarySpec
import org.gradle.platform.base.SourceComponentSpec

@UnsupportedWithConfigurationCache(because = "software model")
class CustomComponentIntegrationTest extends AbstractIntegrationSpec {
    def "can declare custom managed #componentSpecType"() {
        buildFile << """
            @Managed
            interface SampleComponentSpec extends $componentSpecType {
                String getPublicData()
                void setPublicData(String publicData)
            }

            class RegisterComponentRules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleComponentSpec> builder) {
                }
            }
            apply plugin: RegisterComponentRules

            model {
                components {
                    sampleLib(SampleComponentSpec) {
                        publicData = "public"
                    }
                }
            }

            class ValidateTaskRules extends RuleSource {
                @Mutate
                void createValidateTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                    tasks.create("validate") {
                        assert components*.name == ["sampleLib"]
                        assert components.withType(ModelElement)*.name == ["sampleLib"]
                        assert components.withType(ComponentSpec)*.name == ["sampleLib"]
                        assert components.withType($componentSpecType)*.name == ["sampleLib"]
                        assert components.withType(SampleComponentSpec)*.name == ["sampleLib"]
                        assert components*.publicData == ["public"]
                    }
                }
            }
            apply plugin: ValidateTaskRules
        """

        expect:
        succeeds "validate"

        where:
        componentSpecType << [ComponentSpec, SourceComponentSpec, GeneralComponentSpec, LibrarySpec, ApplicationSpec]*.simpleName
    }

    def "presents a public view for custom managed ApplicationSpec"() {
        buildFile << """
            @Managed
            interface SampleComponentSpec extends ApplicationSpec {
                String getPublicData()
                void setPublicData(String publicData)
            }

            class RegisterComponentRules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleComponentSpec> builder) {
                }
            }
            apply plugin: RegisterComponentRules

            model {
                components {
                    sampleLib(SampleComponentSpec) {
                        assert it instanceof SampleComponentSpec
                        assert it.name == 'sampleLib'
                        assert it.displayName == "SampleComponentSpec 'sampleLib'"
                        assert it.toString() == "SampleComponentSpec 'sampleLib'"
                        publicData = "public"
                    }
                    sampleLib {
                        assert it instanceof SampleComponentSpec
                        assert it.name == 'sampleLib'
                        assert it.displayName == "SampleComponentSpec 'sampleLib'"
                        assert it.toString() == "SampleComponentSpec 'sampleLib'"
                        publicData = "modified"
                    }
                }
            }
        """

        expect:
        succeeds "model"
    }

    def "can view a component as a ModelElement"() {
        buildFile << """
            @Managed
            interface SampleComponentSpec extends ApplicationSpec {
                String getPublicData()
                void setPublicData(String publicData)
            }

            class RegisterComponentRules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleComponentSpec> builder) {
                }
                @Mutate
                void tasks(ModelMap<Task> tasks, @Path("components.sampleLib") ModelElement lib) {
                    tasks.create("test") {
                        doLast {
                            assert lib.name == "sampleLib"
                            assert lib.displayName == "SampleComponentSpec 'sampleLib'"
                            assert lib.toString() == lib.displayName
                        }
                    }
                }
            }
            apply plugin: RegisterComponentRules

            model {
                components {
                    sampleLib(SampleComponentSpec)
                }
            }
        """

        expect:
        succeeds "test"
    }

    def "can add binaries to custom managed #componentSpecType"() {
        buildFile << """
            @Managed
            interface SampleComponentSpec extends $componentSpecType {
            }

            class RegisterComponentRules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleComponentSpec> builder) {
                }
            }
            apply plugin: RegisterComponentRules

            model {
                components {
                    sampleLib(SampleComponentSpec) {
                        binaries {
                            jar(BinarySpec)
                        }
                    }
                }
            }

            class ValidateTaskRules extends RuleSource {
                @Mutate
                void createValidateTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                    tasks.create("validate") {
                        assert components*.binaries*.values().flatten()*.name == ["jar"]
                    }
                }
            }
            apply plugin: ValidateTaskRules
        """

        expect:
        succeeds "validate"

        where:
        componentSpecType << [GeneralComponentSpec, LibrarySpec, ApplicationSpec]*.simpleName
    }

    def "can add sources to custom managed #componentSpecType"() {
        buildFile << """
            @Managed
            interface SampleComponentSpec extends $componentSpecType {
            }

            class RegisterComponentRules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleComponentSpec> builder) {
                }
            }
            apply plugin: RegisterComponentRules

            model {
                components {
                    sampleLib(SampleComponentSpec) {
                        sources {
                            java(LanguageSourceSet)
                        }
                    }
                }
            }

            class ValidateTaskRules extends RuleSource {
                @Mutate
                void createValidateTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                    tasks.create("validate") {
                        assert components*.sources*.values().flatten()*.name == ["java"]
                    }
                }
            }
            apply plugin: ValidateTaskRules
        """

        expect:
        succeeds "validate"

        where:
        componentSpecType << [SourceComponentSpec, GeneralComponentSpec, LibrarySpec, ApplicationSpec]*.simpleName
    }

    def "presents a public view for custom unmanaged ComponentSpec"() {
        buildFile << """
            interface UnmanagedComponentSpec extends ComponentSpec {
                String getUnmanagedData()
                void setUnmanagedData(String unmanagedData)
            }

            class DefaultUnmanagedComponentSpec extends BaseComponentSpec implements UnmanagedComponentSpec {
                String unmanagedData
            }

            class RegisterComponentRules extends RuleSource {
                @ComponentType
                void registerUnmanaged(TypeBuilder<UnmanagedComponentSpec> builder) {
                    builder.defaultImplementation(DefaultUnmanagedComponentSpec)
                }
            }
            apply plugin: RegisterComponentRules

            model {
                components {
                    sampleLib(UnmanagedComponentSpec) {
                        assert it instanceof UnmanagedComponentSpec
                        assert it.name == "sampleLib"
                        assert it.displayName == "UnmanagedComponentSpec 'sampleLib'"
                        assert it.toString() == "UnmanagedComponentSpec 'sampleLib'"
                        unmanagedData = "unmanaged"
                    }
                    sampleLib {
                        assert it instanceof UnmanagedComponentSpec
                        assert it.name == "sampleLib"
                        assert it.displayName == "UnmanagedComponentSpec 'sampleLib'"
                        assert it.toString() == "UnmanagedComponentSpec 'sampleLib'"
                        unmanagedData = "modified"
                    }
                }
            }
        """

        expect:
        succeeds "model"
    }

    def "can declare custom managed component based on custom unmanaged component"() {
        buildFile << """
            interface UnmanagedComponentSpec extends ComponentSpec {
                String getUnmanagedData()
                void setUnmanagedData(String unmanagedData)
            }

            class DefaultUnmanagedComponentSpec extends BaseComponentSpec implements UnmanagedComponentSpec {
                String unmanagedData
            }

            @Managed
            interface ManagedComponentSpec extends UnmanagedComponentSpec {
                String getManagedData()
                void setManagedData(String managedData)
            }
        """

        buildFile << declareManagedExtendingUnmanaged()

        buildFile << """
            class MutateComponentRules extends RuleSource {
                @Mutate
                void mutateUnmanaged(ModelMap<UnmanagedComponentSpec> components) {
                    components.all { component ->
                        component.unmanagedData = "unmanaged"
                    }
                }

                @Mutate
                void mutateManaged(ModelMap<ManagedComponentSpec> components) {
                    components.all { component ->
                        component.managedData = "managed"
                    }
                }
            }
            apply plugin: MutateComponentRules

            class ValidateTaskRules extends RuleSource {
                @Mutate
                void createValidateTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                    tasks.create("validate") {
                        assert components*.name == ["managed", "unmanaged"]
                        assert components.withType(ComponentSpec)*.name == ["managed", "unmanaged"]
                        assert components.withType(UnmanagedComponentSpec)*.name == ["managed", "unmanaged"]
                        assert components.withType(ManagedComponentSpec)*.name == ["managed"]
                        assert components.withType(UnmanagedComponentSpec)*.unmanagedData == ["unmanaged", "unmanaged"]
                        assert components.withType(ManagedComponentSpec)*.managedData == ["managed"]
                        assert components.withType(ManagedComponentSpec)*.unmanagedData == ["unmanaged"]
                    }
                }
            }
            apply plugin: ValidateTaskRules
        """
        expect:
        succeeds "validate"
    }

    def "can declare internal views for both custom unmanaged and managed component"() {
        buildFile << """
            interface UnmanagedComponentSpec extends ComponentSpec {
            }

            interface UnmanagedComponentSpecInternal {
                String getUnmanagedInternalData()
                void setUnmanagedInternalData(String unmanagedData)
            }

            class DefaultUnmanagedComponentSpec extends BaseComponentSpec implements UnmanagedComponentSpec, UnmanagedComponentSpecInternal {
                String unmanagedInternalData
            }

            @Managed
            interface ManagedComponentSpec extends UnmanagedComponentSpec {
            }

            @Managed
            interface ManagedComponentSpecInternal {
                String getManagedInternalData()
                void setManagedInternalData(String managedData)
            }
        """
        buildFile << declareManagedExtendingUnmanaged()
        buildFile << """
            class RegisterComponentInternalViewRules extends RuleSource {
                @ComponentType
                void registerUnmanaged(TypeBuilder<UnmanagedComponentSpec> builder) {
                    builder.internalView(UnmanagedComponentSpecInternal)
                }

                @ComponentType
                void registerManaged(TypeBuilder<ManagedComponentSpec> builder) {
                    builder.internalView(ManagedComponentSpecInternal)
                }
            }
            apply plugin: RegisterComponentInternalViewRules

            class MutateComponentRules extends RuleSource {
                @Mutate
                void mutateUnmanaged(ModelMap<UnmanagedComponentSpec> components) {
                    components.withType(UnmanagedComponentSpecInternal) { component ->
                        component.unmanagedInternalData = "unmanaged"
                    }
                }

                @Mutate
                void mutateManaged(ModelMap<ManagedComponentSpec> components) {
                    components.withType(ManagedComponentSpecInternal) { component ->
                        component.managedInternalData = "managed"
                    }
                }
            }
            apply plugin: MutateComponentRules

            class ValidateTaskRules extends RuleSource {
                @Mutate
                void createValidateTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                    tasks.create("validate") {
                        assert components.withType(UnmanagedComponentSpecInternal)*.name == ["managed", "unmanaged"]
                        assert components.withType(ManagedComponentSpecInternal)*.name == ["managed"]
                        assert components.withType(UnmanagedComponentSpecInternal)*.unmanagedInternalData == ["unmanaged", "unmanaged"]
                        assert components.withType(ManagedComponentSpecInternal)*.managedInternalData == ["managed"]
                        assert components.withType(ManagedComponentSpecInternal)*.unmanagedInternalData == ["unmanaged"]
                    }
                }
            }
            apply plugin: ValidateTaskRules
        """
        expect:
        succeeds "validate"
    }

    def "public view of managed component does not expose any internal views or implementation"() {
        buildFile << """
            interface UnmanagedComponentSpec extends ComponentSpec {
                String getUnmanagedData()
                void setUnmanagedData(String value)
            }

            class DefaultUnmanagedComponentSpec extends BaseComponentSpec implements UnmanagedComponentSpec {
                String unmanagedData
            }

            @Managed
            interface SampleComponentSpec extends UnmanagedComponentSpec {
                String getPublicData()
                void setPublicData(String value)
            }

            @Managed
            interface InternalSampleSpec {
                String getInternalData()
                void setInternalData(String value)
            }

            class RegisterComponentRules extends RuleSource {
                @ComponentType
                void register1(TypeBuilder<UnmanagedComponentSpec> builder) {
                    builder.defaultImplementation(DefaultUnmanagedComponentSpec)
                }

                @ComponentType
                void register2(TypeBuilder<SampleComponentSpec> builder) {
                    builder.internalView(InternalSampleSpec)
                }
            }
            apply plugin: RegisterComponentRules

            model {
                components {
                    sample(SampleComponentSpec)
                }
            }

            class ValidateTaskRules extends RuleSource {
                @Validate
                void validateInternal(@Path('components.sample') InternalSampleSpec spec) {
//                    assert !(spec instanceof ComponentSpec)
//                    assert !(spec instanceof UnmanagedComponentSpec)
                    assert !(spec instanceof SampleComponentSpec)
                    assert !(spec instanceof DefaultUnmanagedComponentSpec)
                    spec.internalData
                    try {
                        spec.publicData
                        assert false
                    } catch(MissingPropertyException e) {
                        assert e.message == "No such property: publicData for class: InternalSampleSpec"
                    }
                }

                @Validate
                void validatePublic(@Path('components.sample') SampleComponentSpec spec) {
                    assert !(spec instanceof InternalSampleSpec)
                    assert !(spec instanceof DefaultUnmanagedComponentSpec)
                    spec.publicData
                    try {
                        spec.internalData
                        assert false
                    } catch (MissingPropertyException e) {
                        assert e.message == "No such property: internalData for class: SampleComponentSpec"
                    }
                }

                @Validate
                void validatePublic(@Path('components.sample') ComponentSpec spec) {
                    assert spec instanceof UnmanagedComponentSpec
                    assert spec instanceof SampleComponentSpec
                    assert !(spec instanceof DefaultUnmanagedComponentSpec)
                    assert !(spec instanceof InternalSampleSpec)
                    spec.publicData
                    try {
                        spec.internalData
                        assert false
                    } catch (MissingPropertyException e) {
                        assert e.message == "No such property: internalData for class: SampleComponentSpec"
                    }
                }

                @Validate
                void validatePublic(@Path('components.sample') Object spec) {
                    assert spec instanceof ComponentSpec
                    assert spec instanceof UnmanagedComponentSpec
                    assert spec instanceof SampleComponentSpec
                    assert !(spec instanceof DefaultUnmanagedComponentSpec)
                    assert !(spec instanceof InternalSampleSpec)
                    spec.publicData
                    try {
                        spec.internalData
                        assert false
                    } catch (MissingPropertyException e) {
                        assert e.message == "No such property: internalData for class: SampleComponentSpec"
                    }
                }

                @Mutate
                void createValidateTask(ModelMap<Task> tasks, ComponentSpecContainer components) {
                    tasks.create("validate") {
                        assert components*.name == ["sample"]
                        assert components.withType(Object)*.name == ["sample"]
                        assert components.withType(ComponentSpec)*.name == ["sample"]
                        assert components.withType(SampleComponentSpec)*.name == ["sample"]
                    }
                }
            }
            apply plugin: ValidateTaskRules
        """

        expect:
        succeeds "validate"
    }

    private static def declareManagedExtendingUnmanaged() {
        """
            class RegisterComponentRules extends RuleSource {
                @ComponentType
                void registerUnmanaged(TypeBuilder<UnmanagedComponentSpec> builder) {
                    builder.defaultImplementation(DefaultUnmanagedComponentSpec)
                }

                @ComponentType
                void registerManaged(TypeBuilder<ManagedComponentSpec> builder) {
                }
            }
            apply plugin: RegisterComponentRules

            model {
                components {
                    unmanaged(UnmanagedComponentSpec) {}
                    managed(ManagedComponentSpec) {}
                }
            }
        """
    }

    def "reports failure in @ComponentType rule"() {
        buildFile << """
            @Managed
            interface BrokenComponentSpec extends ComponentSpec {
            }

            class Broken extends RuleSource {
                @ComponentType
                void broken(TypeBuilder<BrokenComponentSpec> builder) {
                    throw new RuntimeException('broken')
                }
            }
            apply plugin: Broken
        """

        expect:
        fails "components"
        failure.assertHasCause("Exception thrown while executing model rule: Broken#broken")
        failure.assertHasCause("broken")
    }

    def "fails when @ComponentType registration is badly formed"() {
        buildFile << """
            @Managed
            interface BrokenComponentSpec extends ComponentSpec {
            }

            class Broken extends RuleSource {
                @ComponentType
                void broken(TypeBuilder<BrokenComponentSpec> builder) {
                    builder.internalView(String)
                }
            }
            apply plugin: Broken
        """

        expect:
        fails "components"
        failure.assertHasCause("Exception thrown while executing model rule: Broken#broken(TypeBuilder<BrokenComponentSpec>)")
        failure.assertHasCause("Broken#broken(TypeBuilder<BrokenComponentSpec>) is not a valid component model rule method.")
        failure.assertHasCause("Internal view java.lang.String must be an interface.")
    }

    def "reports badly formed @ComponentType rule"() {
        buildFile << """
            class Broken extends RuleSource {
                @ComponentType
                private void broken(TypeBuilder<?> builder) {
                }
            }
            apply plugin: Broken
        """

        expect:
        fails "help"
        failure.assertHasCause("Failed to apply plugin class 'Broken'")
        failure.assertHasCause("""Type Broken is not a valid rule source:
- Method broken(org.gradle.platform.base.TypeBuilder<?>) is not a valid rule method: A rule method cannot be private
- Method broken(org.gradle.platform.base.TypeBuilder<?>) is not a valid rule method: Type '?' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.).""")
    }

    @NotYetImplemented
    def "shows proper error message when accessing non-existent property 'binaries' of unmanaged custom ComponentSpec"() {
        buildFile << """
            interface SampleComponentSpec extends ComponentSpec {
                String getPublicData()
                void setPublicData(String publicData)
            }
            class DefaultSampleComponentSpec extends BaseComponentSpec implements SampleComponentSpec {
                String publicData
            }

            class RegisterComponentRules extends RuleSource {
                @ComponentType
                void register(TypeBuilder<SampleComponentSpec> builder) {
                    builder.defaultImplementation(DefaultSampleComponentSpec)
                }
            }
            apply plugin: RegisterComponentRules

            model {
                components {
                    sampleLib(SampleComponentSpec) {
                        binaries {}
                    }
                }
            }
        """

        expect:
        fails "model"
        failureHasCause "Could not find method binaries()"
    }

    def "can define subtype of `ApplicationBinarySpec`"() {
        buildFile << """
@Managed
interface TheApp extends ApplicationSpec {}
@Managed
interface TheAppBinary extends ApplicationBinarySpec {}

class MyRules extends RuleSource {

    @ComponentType
    void registerComponent(TypeBuilder<TheApp> builder) {}

    @ComponentType
    void registerBinary(TypeBuilder<TheAppBinary> builder) {}

    @ComponentBinaries
    void appBinaries(ModelMap<TheAppBinary> binaries, TheApp app) {
        binaries.create(app.name) {}
    }
}

apply plugin : MyRules

model {
    components {
        main(TheApp) {}
    }
}
        """

        expect:
        succeeds "components"
    }
}
