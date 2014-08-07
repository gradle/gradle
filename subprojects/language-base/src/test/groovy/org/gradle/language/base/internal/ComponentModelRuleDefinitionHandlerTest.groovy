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
import org.gradle.model.internal.inspect.DefaultMethodRuleDefinition
import org.gradle.model.internal.inspect.MethodRuleDefinition
import org.gradle.model.internal.inspect.RuleSourceDependencies
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.runtime.base.ComponentSpec
import org.gradle.runtime.base.ComponentType
import org.gradle.runtime.base.ComponentTypeBuilder
import org.gradle.runtime.base.InvalidComponentModelException
import org.gradle.runtime.base.component.DefaultComponentSpec
import org.gradle.runtime.base.internal.registry.ComponentModelRuleDefinitionHandler
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Method

class ComponentModelRuleDefinitionHandlerTest extends Specification {
    Instantiator instantiator = new DirectInstantiator()
    def ruleDefinition = Mock(MethodRuleDefinition)
    def modelRegistry = Mock(ModelRegistry)
    def ruleDependencies = Mock(RuleSourceDependencies)

    ComponentModelRuleDefinitionHandler componentRuleHandler = new ComponentModelRuleDefinitionHandler(instantiator)

    def "handles methods annotated with @ComponentType"() {
        when:
        1 * ruleDefinition.getAnnotation(ComponentType) >> null

        then:
        !componentRuleHandler.isSatisfiedBy(ruleDefinition)


        when:
        1 * ruleDefinition.getAnnotation(ComponentType) >> Mock(ComponentType)

        then:
        componentRuleHandler.isSatisfiedBy(ruleDefinition)
    }

    def "applies ComponentModelBasePlugin and creates component type rule"() {
        when:
        componentRuleHandler.register(ruleDefinitionForMethod("validTypeRule"), modelRegistry, ruleDependencies)

        then:
        1 * ruleDependencies.add(ComponentModelBasePlugin)

        and:
        1 * modelRegistry.mutate(_)
    }

    def "applies ComponentModelBasePlugin only when implementation not set"() {
        when:
        componentRuleHandler.register(ruleDefinitionForMethod("noImplementationSet"), modelRegistry, ruleDependencies)

        then:
        1 * ruleDependencies.add(ComponentModelBasePlugin)

        and:
        0 * modelRegistry._
    }

    def ruleDefinitionForMethod(String methodName) {
        for (Method candidate : Rules.class.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                return new DefaultMethodRuleDefinition(candidate)
            }
        }
        throw new IllegalArgumentException("Not a test method name")
    }

    @Unroll
    def "decent error message for #descr"() {
        when:
        componentRuleHandler.register(ruleDefinitionForMethod(methodName), modelRegistry, ruleDependencies)

        then:
        def ex = thrown(InvalidComponentModelException)
        ex.message == expectedMessage

        where:
        methodName                         | expectedMessage                                                                                   | descr
        "extraParameter"                   | "ComponentType method must have a single parameter of type ComponentTypeBuilder."                 | "additional rule parameter"
        "returnValue"                      | "ComponentType method must not have a return value."                                              | "method with return type"
        "implementationSetMultipleTimes"   | "ComponentType method cannot set default implementation multiple times."                          | "implementation set multiple times"
        "noTypeParam"                      | "ComponentTypeBuilder parameter must declare a type parameter (must be generified)."              | "missing type parameter"
        "notComponentSpec"                 | "Component type 'NotComponentSpec' must extend 'ComponentSpec'."                                  | "type not extending ComponentSpec"
        "notCustomComponent"               | "Component type must be a subtype of 'ComponentSpec'."                                            | "type is ComponentSpec"
        "notImplementingLibraryType"       | "Component implementation 'NotImplementingCustomComponent' must implement 'SomeComponentSpec'."   | "implementation not implementing type class"
        "notExtendingDefaultSampleLibrary" | "Component implementation 'NotExtendingDefaultComponentSpec' must extend 'DefaultComponentSpec'." | "implementation not extending DefaultComponentSpec"
        "noDefaultConstructor"             | "Component implementation 'NoDefaultConstructor' must have public default constructor."           | "implementation with no public default constructor"
    }

    def aProjectPlugin() {
        ruleDependencies = ProjectBuilder.builder().build()
        _ * pluginApplication.target >> ruleDependencies
    }

    def aSettingsPlugin(def plugin) {
        Settings settings = Mock(Settings)
        _ * pluginApplication.target >> settings
        _ * pluginApplication.plugin >> plugin
        componentRuleHandler = new ComponentModelRuleDefinitionHandler(instantiator)
    }

    interface SomeComponentSpec extends ComponentSpec {}

    static class SomeComponentSpecImpl extends DefaultComponentSpec implements SomeComponentSpec {}

    static class SomeComponentSpecOtherImpl extends SomeComponentSpecImpl {}

    interface NotComponentSpec {}

    static class NotImplementingCustomComponent extends DefaultComponentSpec implements ComponentSpec {}

    abstract static class NotExtendingDefaultComponentSpec implements SomeComponentSpec {}

    static class NoDefaultConstructor extends DefaultComponentSpec implements SomeComponentSpec {
        NoDefaultConstructor(String arg) {
        }
    }

    static class Rules {
        @ComponentType
        static void validTypeRule(ComponentTypeBuilder<SomeComponentSpec> builder) {
            builder.setDefaultImplementation(SomeComponentSpecImpl)
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
            builder.setDefaultImplementation(SomeComponentSpecImpl)
            builder.setDefaultImplementation(SomeComponentSpecOtherImpl)
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
            builder.setDefaultImplementation(NotImplementingCustomComponent)
        }

        @ComponentType
        static void notExtendingDefaultSampleLibrary(ComponentTypeBuilder<SomeComponentSpec> builder) {
            builder.setDefaultImplementation(NotExtendingDefaultComponentSpec)
        }

        @ComponentType
        static void noDefaultConstructor(ComponentTypeBuilder<SomeComponentSpec> builder) {
            builder.setDefaultImplementation(NoDefaultConstructor)
        }
    }
}


