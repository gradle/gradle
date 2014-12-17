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

import org.gradle.api.initialization.Settings
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.internal.inspect.DefaultMethodRuleDefinition
import org.gradle.model.internal.inspect.MethodRuleDefinition
import org.gradle.model.internal.inspect.RuleSourceDependencies
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.*
import org.gradle.platform.base.component.BaseComponentSpec
import org.gradle.platform.base.internal.registry.AbstractAnnotationRuleDefinitionHandlerTest
import org.gradle.platform.base.internal.registry.ComponentTypeRuleDefinitionHandler
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Unroll

import java.lang.annotation.Annotation
import java.lang.reflect.Method

class ComponentTypeRuleDefinitionHandlerTest extends AbstractAnnotationRuleDefinitionHandlerTest {
    Instantiator instantiator = new DirectInstantiator()
    def ruleDefinition = Mock(MethodRuleDefinition)
    def modelRegistry = Mock(ModelRegistry)
    def ruleDependencies = Mock(RuleSourceDependencies)

    ComponentTypeRuleDefinitionHandler ruleHandler = new ComponentTypeRuleDefinitionHandler(instantiator)

    @Override
    Class<? extends Annotation> getAnnotation() { return ComponentType }

    def "applies ComponentModelBasePlugin and creates component type rule"() {
        when:
        ruleHandler.register(ruleDefinitionForMethod("validTypeRule"), modelRegistry, ruleDependencies)

        then:
        1 * ruleDependencies.add(ComponentModelBasePlugin)

        and:
        1 * modelRegistry.mutate(_)
    }

    def "applies ComponentModelBasePlugin only when implementation not set"() {
        when:
        ruleHandler.register(ruleDefinitionForMethod("noImplementationSet"), modelRegistry, ruleDependencies)

        then:
        1 * ruleDependencies.add(ComponentModelBasePlugin)

        and:
        0 * modelRegistry._
    }

    def ruleDefinitionForMethod(String methodName) {
        for (Method candidate : Rules.class.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                return DefaultMethodRuleDefinition.create(Rules.class, candidate)
            }
        }
        throw new IllegalArgumentException("Not a test method name")
    }

    @Unroll
    def "decent error message for #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod)

        when:
        ruleHandler.register(ruleMethod, modelRegistry, ruleDependencies)

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

    def getStringDescription(MethodRuleDefinition ruleDefinition) {
        def builder = new StringBuilder()
        ruleDefinition.descriptor.describeTo(builder)
        builder.toString()
    }

    def aProjectPlugin() {
        ruleDependencies = ProjectBuilder.builder().build()
        _ * pluginApplication.target >> ruleDependencies
    }

    def aSettingsPlugin(def plugin) {
        Settings settings = Mock(Settings)
        _ * pluginApplication.target >> settings
        _ * pluginApplication.plugin >> plugin
        ruleHandler = new ComponentTypeRuleDefinitionHandler(instantiator)
    }

    interface SomeComponentSpec extends ComponentSpec {}

    static class SomeComponentSpecImpl extends BaseComponentSpec implements SomeComponentSpec {}

    static class SomeComponentSpecOtherImpl extends SomeComponentSpecImpl {}

    interface NotComponentSpec {}

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


