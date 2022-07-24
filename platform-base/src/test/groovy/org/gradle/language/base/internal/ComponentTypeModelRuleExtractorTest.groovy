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

import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.internal.core.ModelAction
import org.gradle.model.internal.core.ModelActionRole
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.model.internal.type.ModelType
import org.gradle.platform.base.*
import org.gradle.platform.base.component.BaseComponentSpec
import org.gradle.platform.base.component.internal.ComponentSpecFactory
import org.gradle.platform.base.internal.registry.AbstractAnnotationModelRuleExtractorTest
import org.gradle.platform.base.internal.registry.ComponentTypeModelRuleExtractor
import org.gradle.platform.base.plugins.ComponentBasePlugin

import java.lang.annotation.Annotation

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf

class ComponentTypeModelRuleExtractorTest extends AbstractAnnotationModelRuleExtractorTest {
    final static ModelType<ComponentSpecFactory> FACTORY_REGISTRY_TYPE = ModelType.of(ComponentSpecFactory)
    ComponentTypeModelRuleExtractor ruleHandler = new ComponentTypeModelRuleExtractor(schemaStore)

    @Override
    Class<? extends Annotation> getAnnotation() { return ComponentType }

    Class<?> ruleClass = Rules

    def "applies ComponentModelBasePlugin and creates component type rule"() {
        def mockRegistry = Mock(ModelRegistry)

        when:
        def registration = extract(ruleDefinitionForMethod(ruleName))

        then:
        registration.ruleDependencies == [plugin]

        when:
        apply(registration, mockRegistry)

        then:
        1 * mockRegistry.configure(_, _) >> { ModelActionRole role, ModelAction action ->
            assert role == ModelActionRole.Mutate
            assert action.subject == ModelReference.of(FACTORY_REGISTRY_TYPE)
        }
        0 * _

        where:
        ruleName           | plugin
        "validTypeRule"    | ComponentBasePlugin
        "generalComponent" | ComponentModelBasePlugin
        "library"          | ComponentModelBasePlugin
        "application"      | ComponentModelBasePlugin
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
        methodName          | expectedMessage                                                                                                         | descr
        "extraParameter"    | "A method annotated with @ComponentType must have a single parameter of type ${TypeBuilder.name}."                      | "additional rule parameter"
        "binaryTypeBuilder" | "A method annotated with @ComponentType must have a single parameter of type ${TypeBuilder.name}."                      | "wrong builder type"
        "returnValue"       | "A method annotated with @ComponentType must have void return type."                                                    | "method with return type"
        "noTypeParam"       | "Parameter of type ${TypeBuilder.name} must declare a type parameter."                                                  | "missing type parameter"
        "wildcardType"      | "Type '?' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)."                               | "wildcard type parameter"
        "extendsType"       | "Type '? extends ${ComponentSpec.name}' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)." | "extends type parameter"
        "superType"         | "Type '? super ${ComponentSpec.name}' cannot be a wildcard type (i.e. cannot use ? super, ? extends etc.)."   | "super type parameter"
        "notComponentSpec"  | "Type '${NotComponentSpec.name}' is not a subtype of '${ComponentSpec.name}'."                                | "type not extending ComponentSpec"
    }

    def "decent error message for rule execution problem - #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod)

        when:
        apply(ruleMethod)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == "${ruleDescription} is not a valid component model rule method."
        ex.cause instanceof InvalidModelException
        ex.cause.message == expectedMessage

        where:
        methodName                       | expectedMessage                                                                           | descr
        "implementationSetMultipleTimes" | "Method annotated with @ComponentType cannot set default implementation multiple times."  | "implementation set multiple times"
        "internalViewNotInterface"       | "Internal view ${NonInterfaceInternalView.name} must be an interface."                    | "non-interface internal view"
        "repeatedInternalView"           | "Internal view '${ComponentSpecInternalView.name}' must not be specified multiple times." | "internal view specified multiple times"
    }

    static interface SomeComponentSpec extends ComponentSpec {}

    static interface SomeGeneralComponentSpec extends GeneralComponentSpec {}

    static interface SomeLibrarySpec extends LibrarySpec {}

    static interface SomeApplicationSpec extends ApplicationSpec {}

    static class SomeComponentSpecImpl extends BaseComponentSpec implements SomeComponentSpec, ComponentSpecInternalView, BareInternalView {}

    static class SomeComponentSpecOtherImpl extends SomeComponentSpecImpl {}

    static class NotImplementingCustomComponent extends BaseComponentSpec implements ComponentSpec {}

    abstract static class NotExtendingBaseComponentSpec implements SomeComponentSpec {}

    abstract static class NonInterfaceInternalView implements ComponentSpec {}

    static interface ComponentSpecInternalView extends ComponentSpec {}

    static interface NotImplementedComponentSpecInternalView extends ComponentSpec {}

    static class NoDefaultConstructor extends BaseComponentSpec implements SomeComponentSpec {
        NoDefaultConstructor(String arg) {
        }
    }

    static class Rules {
        @ComponentType
        static void validTypeRule(TypeBuilder<SomeComponentSpec> builder) {
            builder.defaultImplementation(SomeComponentSpecImpl)
            builder.internalView(ComponentSpecInternalView)
            builder.internalView(BareInternalView)
        }

        @ComponentType
        static void generalComponent(TypeBuilder<SomeGeneralComponentSpec> builder) {
        }

        @ComponentType
        static void library(TypeBuilder<SomeLibrarySpec> builder) {
        }

        @ComponentType
        static void application(TypeBuilder<SomeApplicationSpec> builder) {
        }

        @ComponentType
        static void wildcardType(TypeBuilder<?> builder) {
        }

        @ComponentType
        static void extendsType(TypeBuilder<? extends ComponentSpec> builder) {
        }

        @ComponentType
        static void superType(TypeBuilder<? super ComponentSpec> builder) {
        }

        @ComponentType
        static void extraParameter(TypeBuilder<SomeComponentSpec> builder, String otherParam) {
        }

        @ComponentType
        static String returnValue(TypeBuilder<SomeComponentSpec> builder) {
        }

        @ComponentType
        static void noImplementationSet(TypeBuilder<SomeComponentSpec> builder) {
        }

        @ComponentType
        static void implementationSetMultipleTimes(TypeBuilder<SomeComponentSpec> builder) {
            builder.defaultImplementation(SomeComponentSpecImpl)
            builder.defaultImplementation(SomeComponentSpecOtherImpl)
        }

        @ComponentType
        static void binaryTypeBuilder(SomeOtherBuilder<BinarySpec> builder) {
        }

        @ComponentType
        static void noTypeParam(TypeBuilder builder) {
        }

        @ComponentType
        static void notComponentSpec(TypeBuilder<NotComponentSpec> builder) {
        }

        @ComponentType
        static void notImplementingLibraryType(TypeBuilder<SomeComponentSpec> builder) {
            builder.defaultImplementation(NotImplementingCustomComponent)
        }

        @ComponentType
        static void notExtendingDefaultSampleLibrary(TypeBuilder<SomeComponentSpec> builder) {
            builder.defaultImplementation(NotExtendingBaseComponentSpec)
        }

        @ComponentType
        static void noDefaultConstructor(TypeBuilder<SomeComponentSpec> builder) {
            builder.defaultImplementation(NoDefaultConstructor)
        }

        @ComponentType
        static void internalViewNotInterface(TypeBuilder<SomeComponentSpec> builder) {
            builder.defaultImplementation(SomeComponentSpecImpl)
            builder.internalView(NonInterfaceInternalView)
        }

        @ComponentType
        static void notExtendingInternalView(TypeBuilder<SomeComponentSpec> builder) {
            builder.defaultImplementation(SomeComponentSpecImpl)
            builder.internalView(NotImplementedComponentSpecInternalView)
        }

        @ComponentType
        static void repeatedInternalView(TypeBuilder<SomeComponentSpec> builder) {
            builder.defaultImplementation(SomeComponentSpecImpl)
            builder.internalView(ComponentSpecInternalView)
            builder.internalView(ComponentSpecInternalView)
        }
    }
}


