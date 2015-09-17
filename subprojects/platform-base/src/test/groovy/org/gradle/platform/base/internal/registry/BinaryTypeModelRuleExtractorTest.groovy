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
import org.gradle.language.base.internal.model.BinarySpecFactoryRegistry
import org.gradle.language.base.internal.testinterfaces.NotBinarySpec
import org.gradle.language.base.internal.testinterfaces.SomeBinarySpec
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.Managed
import org.gradle.model.internal.core.*
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspectExtractor
import org.gradle.model.internal.manage.schema.extract.ModelSchemaExtractor
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.BinaryType
import org.gradle.platform.base.BinaryTypeBuilder
import org.gradle.platform.base.InvalidModelException
import org.gradle.platform.base.binary.BaseBinarySpec
import spock.lang.Unroll

import java.lang.annotation.Annotation

class BinaryTypeModelRuleExtractorTest extends AbstractAnnotationModelRuleExtractorTest {
    def aspectExtractor = new ModelSchemaAspectExtractor()
    BinaryTypeModelRuleExtractor ruleHandler = new BinaryTypeModelRuleExtractor(
        new DefaultModelSchemaStore(
            new ModelSchemaExtractor([
                new BinarySpecSpecializationSchemaExtractionStrategy(aspectExtractor)
            ], aspectExtractor)
        )
    )

    @Override
    Class<? extends Annotation> getAnnotation() {
        return BinaryType
    }

    Class<?> ruleClass = Rules

    def "applies ComponentModelBasePlugin and creates binary type rule"() {
        def mockRegistry = Mock(ModelRegistry)

        when:
        def registration = ruleHandler.registration(ruleDefinitionForMethod("validTypeRule"))

        then:
        registration instanceof ExtractedModelAction
        registration.ruleDependencies == [ComponentModelBasePlugin]

        when:
        registration.apply(mockRegistry, ModelPath.ROOT)

        then:
        1 * mockRegistry.configure(_, _, _) >> { ModelActionRole role, ModelAction<?> action, ModelPath scope ->
            assert role == ModelActionRole.Defaults
            assert action.subject == ModelReference.of(BinarySpecFactoryRegistry)
        }
        0 * _
    }

    def "applies ComponentModelBasePlugin only when implementation not set for unmanaged binary spec"() {
        when:
        def registration = ruleHandler.registration(ruleDefinitionForMethod("noImplementationSetForUnmanagedBinarySpec"))

        then:
        registration instanceof DependencyOnlyExtractedModelRule
        registration.ruleDependencies == [ComponentModelBasePlugin]
    }

    @Unroll
    def "decent error message for #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod)

        when:
        ruleHandler.registration(ruleMethod)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == "${ruleDescription} is not a valid binary model rule method."
        ex.cause instanceof InvalidModelException
        ex.cause.message == expectedMessage

        where:
        methodName                         | expectedMessage                                                                                                        | descr
        "extraParameter"                   | "Method annotated with @BinaryType must have a single parameter of type '${BinaryTypeBuilder.name}'."                  | "additional rule parameter"
        "returnValue"                      | "Method annotated with @BinaryType must not have a return value."                                                      | "method with return type"
        "implementationSetMultipleTimes"   | "Method annotated with @BinaryType cannot set default implementation multiple times."                                  | "implementation set multiple times"
        "implementationSetForManagedType"  | "Method annotated with @BinaryType cannot set default implementation for managed type ${ManagedBinarySpec.name}."      | "implementation set for managed type"
        "noTypeParam"                      | "Parameter of type '${BinaryTypeBuilder.name}' must declare a type parameter."                                         | "missing type parameter"
        "notBinarySpec"                    | "Binary type '${NotBinarySpec.name}' is not a subtype of '${BinarySpec.name}'."                                        | "type not extending BinarySpec"
        "notCustomBinary"                  | "Binary type '${BinarySpec.name}' is not a subtype of '${BinarySpec.name}'."                                           | "type is BinarySpec"
        "wildcardType"                     | "Binary type '?' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)."                                 | "wildcard type parameter"
        "extendsType"                      | "Binary type '? extends ${BinarySpec.getName()}' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)." | "extends type parameter"
        "superType"                        | "Binary type '? super ${BinarySpec.getName()}' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)."   | "super type parameter"
        "notImplementingBinaryType"        | "Binary implementation '${NotImplementingCustomBinary.name}' must implement '${SomeBinarySpec.name}'."                 | "implementation not implementing type class"
        "notExtendingDefaultSampleLibrary" | "Binary implementation '${NotExtendingBaseBinarySpec.name}' must extend '${BaseBinarySpec.name}'."                     | "implementation not extending BaseBinarySpec"
        "noDefaultConstructor"             | "Binary implementation '${NoDefaultConstructor.name}' must have public default constructor."                           | "implementation with no public default constructor"
    }


    static class SomeBinarySpecImpl extends BaseBinarySpec implements SomeBinarySpec {}

    static class SomeBinarySpecOtherImpl extends SomeBinarySpecImpl {}

    static class NotImplementingCustomBinary extends BaseBinarySpec implements BinarySpec {}

    abstract static class NotExtendingBaseBinarySpec implements SomeBinarySpec {}

    static class NoDefaultConstructor extends BaseBinarySpec implements SomeBinarySpec {
        NoDefaultConstructor(String arg) {
        }
    }

    @Managed
    static abstract class ManagedBinarySpec implements BinarySpec {}

    static class Rules {
        @BinaryType
        static void validTypeRule(BinaryTypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(SomeBinarySpecImpl)
        }

        @BinaryType
        static void extraParameter(BinaryTypeBuilder<SomeBinarySpec> builder, String otherParam) {
        }

        @BinaryType
        static String returnValue(BinaryTypeBuilder<SomeBinarySpec> builder) {
        }

        @BinaryType
        static void noImplementationSetForUnmanagedBinarySpec(BinaryTypeBuilder<SomeBinarySpec> builder) {
        }

        @BinaryType
        static void implementationSetMultipleTimes(BinaryTypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(SomeBinarySpecImpl)
            builder.defaultImplementation(SomeBinarySpecOtherImpl)
        }

        @BinaryType
        static void implementationSetForManagedType(BinaryTypeBuilder<ManagedBinarySpec> builder) {
            builder.defaultImplementation(ManagedBinarySpec)
        }

        @BinaryType
        static void noTypeParam(BinaryTypeBuilder builder) {
        }

        @BinaryType
        static void wildcardType(BinaryTypeBuilder<?> builder) {
        }

        @BinaryType
        static void extendsType(BinaryTypeBuilder<? extends BinarySpec> builder) {
        }

        @BinaryType
        static void superType(BinaryTypeBuilder<? super BinarySpec> builder) {
        }

        @BinaryType
        static void notBinarySpec(BinaryTypeBuilder<NotBinarySpec> builder) {
        }

        @BinaryType
        static void notCustomBinary(BinaryTypeBuilder<BinarySpec> builder) {
        }

        @BinaryType
        static void notImplementingBinaryType(BinaryTypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(NotImplementingCustomBinary)
        }

        @BinaryType
        static void notExtendingDefaultSampleLibrary(BinaryTypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(NotExtendingBaseBinarySpec)
        }

        @BinaryType
        static void noDefaultConstructor(BinaryTypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(NoDefaultConstructor)
        }
    }
}
