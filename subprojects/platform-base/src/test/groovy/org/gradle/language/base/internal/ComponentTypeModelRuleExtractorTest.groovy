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

package org.gradle.language.base.internal

import org.gradle.language.base.internal.testinterfaces.NotComponentSpec
import org.gradle.language.base.internal.testinterfaces.SomeComponentSpec
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.internal.core.*
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.model.internal.type.ModelType
import org.gradle.platform.base.*
import org.gradle.platform.base.component.BaseComponentSpec
import org.gradle.platform.base.internal.ComponentSpecFactory
import org.gradle.platform.base.internal.registry.AbstractAnnotationModelRuleExtractorTest
import org.gradle.platform.base.internal.registry.ComponentTypeModelRuleExtractor
import spock.lang.Unroll

import java.lang.annotation.Annotation

class ComponentTypeModelRuleExtractorTest extends AbstractAnnotationModelRuleExtractorTest {
    final static ModelType<ComponentSpecFactory> FACTORY_REGISTRY_TYPE = ModelType.of(ComponentSpecFactory)
    ComponentTypeModelRuleExtractor ruleHandler = new ComponentTypeModelRuleExtractor(DefaultModelSchemaStore.getInstance())

    @Override
    Class<? extends Annotation> getAnnotation() { return ComponentType }

    Class<?> ruleClass = Rules

    def "applies ComponentModelBasePlugin and creates component type rule"() {
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
            assert action.subject == ModelReference.of(FACTORY_REGISTRY_TYPE)
        }
        0 * _
    }

    def "applies ComponentModelBasePlugin only when implementation not set"() {
        when:
        def registration = ruleHandler.registration(ruleDefinitionForMethod("noImplementationSet"))

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
        ex.message == "${ruleDescription} is not a valid component model rule method."
        ex.cause instanceof InvalidModelException
        ex.cause.message == expectedMessage

        where:
        methodName                         | expectedMessage                                                                                                         | descr
        "extraParameter"                   | "Method annotated with @ComponentType must have a single parameter of type '${ComponentTypeBuilder.name}'."             | "additional rule parameter"
        "binaryTypeBuilder"                | "Method annotated with @ComponentType must have a single parameter of type '${ComponentTypeBuilder.name}'."             | "wrong builder type"
        "returnValue"                      | "Method annotated with @ComponentType must not have a return value."                                                    | "method with return type"
        "implementationSetMultipleTimes"   | "Method annotated with @ComponentType cannot set default implementation multiple times."                                | "implementation set multiple times"
        "noTypeParam"                      | "Parameter of type '${ComponentTypeBuilder.name}' must declare a type parameter."                                       | "missing type parameter"
        "wildcardType"                     | "Component type '?' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)."                               | "wildcard type parameter"
        "extendsType"                      | "Component type '? extends ${ComponentSpec.name}' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)." | "extends type parameter"
        "superType"                        | "Component type '? super ${ComponentSpec.name}' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)."   | "super type parameter"
        "notComponentSpec"                 | "Component type '${NotComponentSpec.name}' is not a subtype of '${ComponentSpec.name}'."                                | "type not extending ComponentSpec"
        "notCustomComponent"               | "Component type '${ComponentSpec.name}' is not a subtype of '${ComponentSpec.name}'."                                   | "type is ComponentSpec"
        "notImplementingLibraryType"       | "Component implementation '${NotImplementingCustomComponent.name}' must implement '${SomeComponentSpec.name}'."         | "implementation not implementing type class"
        "notExtendingDefaultSampleLibrary" | "Component implementation '${NotExtendingBaseComponentSpec.name}' must extend '${BaseComponentSpec.name}'."             | "implementation not extending BaseComponentSpec"
        "noDefaultConstructor"             | "Component implementation '${NoDefaultConstructor.name}' must have public default constructor."                         | "implementation with no public default constructor"
    }



    static class SomeComponentSpecImpl extends BaseComponentSpec implements SomeComponentSpec {}

    static class SomeComponentSpecOtherImpl extends SomeComponentSpecImpl {}

    static class NotImplementingCustomComponent extends BaseComponentSpec implements ComponentSpec {}

    abstract static class NotExtendingBaseComponentSpec implements SomeComponentSpec {}

    static class NoDefaultConstructor extends BaseComponentSpec implements SomeComponentSpec {
        NoDefaultConstructor(String arg) {
        }
    }

    static class Rules {
        @ComponentType
        static void validTypeRule(ComponentTypeBuilder<SomeComponentSpec> builder) {
            builder.defaultImplementation(SomeComponentSpecImpl)
        }

        @ComponentType
        static void wildcardType(ComponentTypeBuilder<?> builder) {
        }

        @ComponentType
        static void extendsType(ComponentTypeBuilder<? extends ComponentSpec> builder) {
        }

        @ComponentType
        static void superType(ComponentTypeBuilder<? super ComponentSpec> builder) {
        }

        @ComponentType
        static void extraParameter(ComponentTypeBuilder<SomeComponentSpec> builder, String otherParam) {
        }

        @ComponentType
        static String returnValue(ComponentTypeBuilder<SomeComponentSpec> builder) {
        }

        @ComponentType
        static void noImplementationSet(ComponentTypeBuilder<SomeComponentSpec> builder) {
        }

        @ComponentType
        static void implementationSetMultipleTimes(ComponentTypeBuilder<SomeComponentSpec> builder) {
            builder.defaultImplementation(SomeComponentSpecImpl)
            builder.defaultImplementation(SomeComponentSpecOtherImpl)
        }

        @ComponentType
        static void binaryTypeBuilder(BinaryTypeBuilder<BinarySpec> builder) {
        }

        @ComponentType
        static void noTypeParam(ComponentTypeBuilder builder) {
        }

        @ComponentType
        static void notComponentSpec(ComponentTypeBuilder<NotComponentSpec> builder) {
        }

        @ComponentType
        static void notCustomComponent(ComponentTypeBuilder<ComponentSpec> builder) {
        }

        @ComponentType
        static void notImplementingLibraryType(ComponentTypeBuilder<SomeComponentSpec> builder) {
            builder.defaultImplementation(NotImplementingCustomComponent)
        }

        @ComponentType
        static void notExtendingDefaultSampleLibrary(ComponentTypeBuilder<SomeComponentSpec> builder) {
            builder.defaultImplementation(NotExtendingBaseComponentSpec)
        }

        @ComponentType
        static void noDefaultConstructor(ComponentTypeBuilder<SomeComponentSpec> builder) {
            builder.defaultImplementation(NoDefaultConstructor)
        }
    }
}


