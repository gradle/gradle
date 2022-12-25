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

package org.gradle.platform.base.internal.registry

import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.Managed
import org.gradle.model.internal.core.ModelAction
import org.gradle.model.internal.core.ModelActionRole
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaExtractor
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.*
import org.gradle.platform.base.binary.BaseBinarySpec
import org.gradle.platform.base.component.internal.ComponentSpecFactory
import org.gradle.platform.base.plugins.BinaryBasePlugin

import java.lang.annotation.Annotation

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf

class BinaryTypeModelRuleExtractorTest extends AbstractAnnotationModelRuleExtractorTest {
    ComponentTypeModelRuleExtractor ruleHandler = new ComponentTypeModelRuleExtractor(new DefaultModelSchemaStore(DefaultModelSchemaExtractor.withDefaultStrategies()))

    @Override
    Class<? extends Annotation> getAnnotation() {
        return ComponentType
    }

    Class<?> ruleClass = Rules

    def "applies BinaryBasePlugin and creates binary type rule"() {
        def mockRegistry = Mock(ModelRegistry)

        when:
        def registration = extract(ruleDefinitionForMethod("validTypeRule"))

        then:
        registration.ruleDependencies == [BinaryBasePlugin]

        when:
        apply(registration, mockRegistry)

        then:
        1 * mockRegistry.configure(_, _) >> { ModelActionRole role, ModelAction action ->
            assert role == ModelActionRole.Mutate
            assert action.subject == ModelReference.of(ComponentSpecFactory)
        }
        0 * _
    }

    def "decent error message for rule declaration problem - #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod.method)

        when:
        extract(ruleMethod)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == """Type ${fullyQualifiedNameOf(ruleClass)} is not a valid rule source:
- Method ${ruleDescription} is not a valid rule method: ${expectedMessage}"""

        where:
        methodName       | expectedMessage                                                                                                 | descr
        "extraParameter" | "A method annotated with @ComponentType must have a single parameter of type ${TypeBuilder.name}."              | "additional rule parameter"
        "returnValue"    | "A method annotated with @ComponentType must have void return type."                                            | "method with return type"
        "noTypeParam"    | "Parameter of type ${TypeBuilder.name} must declare a type parameter."                                          | "missing type parameter"
        "notBinarySpec"  | "Type '${fullyQualifiedNameOf(NotBinarySpec)}' is not a subtype of '${ComponentSpec.name}'."                    | "type not extending BinarySpec"
        "wildcardType"   | "Type '?' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)."                                 | "wildcard type parameter"
        "extendsType"    | "Type '? extends ${BinarySpec.getName()}' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)." | "extends type parameter"
        "superType"      | "Type '? super ${BinarySpec.getName()}' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)."   | "super type parameter"
    }

    def "decent error message for rule behaviour problem - #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod)

        when:
        apply(ruleMethod)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == "${ruleDescription} is not a valid binary model rule method."
        ex.cause instanceof InvalidModelException
        ex.cause.message == expectedMessage

        where:
        methodName                        | expectedMessage                                                                                                                       | descr
        "implementationSetMultipleTimes"  | "Method annotated with @ComponentType cannot set default implementation multiple times."                                              | "implementation set multiple times"
        "implementationSetForManagedType" | "Method annotated with @ComponentType cannot set default implementation for managed type ${fullyQualifiedNameOf(ManagedBinarySpec)}." | "implementation set for managed type"
        "internalViewNotInterface"        | "Internal view ${NonInterfaceInternalView.name} must be an interface."                                                                | "non-interface internal view"
        "repeatedInternalView"            | "Internal view '${BinarySpecInternalView.name}' must not be specified multiple times."                                                | "internal view specified multiple times"
    }

    static interface SomeBinarySpec extends BinarySpec {}

    static class SomeBinarySpecImpl extends BaseBinarySpec implements SomeBinarySpec, BinarySpecInternalView, BareInternalView {}

    static class SomeBinarySpecOtherImpl extends SomeBinarySpecImpl {}

    static class NotImplementingCustomBinary extends BaseBinarySpec implements BinarySpec {}

    abstract static class NotExtendingBaseBinarySpec implements SomeBinarySpec {}

    static interface BinarySpecInternalView extends BinarySpec {}

    static interface BareInternalView {}

    abstract static class NonInterfaceInternalView implements BinarySpec {}

    static interface NotImplementedBinarySpecInternalView extends BinarySpec {}

    static interface NotBinarySpec {}

    static class NoDefaultConstructor extends BaseBinarySpec implements SomeBinarySpec {
        NoDefaultConstructor(String arg) {
        }
    }

    @Managed
    static abstract class ManagedBinarySpec implements BinarySpec {}

    static class Rules {
        @ComponentType
        static void validTypeRule(TypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(SomeBinarySpecImpl)
            builder.internalView(BinarySpecInternalView)
            builder.internalView(BareInternalView)
        }

        @ComponentType
        static void extraParameter(TypeBuilder<SomeBinarySpec> builder, String otherParam) {
        }

        @ComponentType
        static String returnValue(TypeBuilder<SomeBinarySpec> builder) {
        }

        @ComponentType
        static void noImplementationSetForUnmanagedBinarySpec(TypeBuilder<SomeBinarySpec> builder) {
        }

        @ComponentType
        static void implementationSetMultipleTimes(TypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(SomeBinarySpecImpl)
            builder.defaultImplementation(SomeBinarySpecOtherImpl)
        }

        @ComponentType
        static void implementationSetForManagedType(TypeBuilder<ManagedBinarySpec> builder) {
            builder.defaultImplementation(ManagedBinarySpec)
        }

        @ComponentType
        static void noTypeParam(TypeBuilder builder) {
        }

        @ComponentType
        static void wildcardType(TypeBuilder<?> builder) {
        }

        @ComponentType
        static void extendsType(TypeBuilder<? extends BinarySpec> builder) {
        }

        @ComponentType
        static void superType(TypeBuilder<? super BinarySpec> builder) {
        }

        @ComponentType
        static void notBinarySpec(TypeBuilder<NotBinarySpec> builder) {
        }

        @ComponentType
        static void notImplementingBinaryType(TypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(NotImplementingCustomBinary)
        }

        @ComponentType
        static void notExtendingDefaultSampleLibrary(TypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(NotExtendingBaseBinarySpec)
        }

        @ComponentType
        static void noDefaultConstructor(TypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(NoDefaultConstructor)
        }

        @ComponentType
        static void internalViewNotInterface(TypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(SomeBinarySpecImpl)
            builder.internalView(NonInterfaceInternalView)
        }

        @ComponentType
        static void notExtendingInternalView(TypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(SomeBinarySpecImpl)
            builder.internalView(NotImplementedBinarySpecInternalView)
        }

        @ComponentType
        static void repeatedInternalView(TypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(SomeBinarySpecImpl)
            builder.internalView(BinarySpecInternalView)
            builder.internalView(BinarySpecInternalView)
        }
    }
}
