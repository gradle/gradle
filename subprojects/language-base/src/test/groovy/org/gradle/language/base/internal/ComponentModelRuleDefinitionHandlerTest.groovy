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
import org.gradle.runtime.base.*
import org.gradle.runtime.base.internal.registry.ComponentModelRuleDefinitionHandler
import org.gradle.runtime.base.library.DefaultLibrarySpec
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
        methodName                         | expectedMessage                                                                                 | descr
        "extraParameter"                   | "ComponentType method must have a single parameter of type ComponentTypeBuilder."               | "additional rule parameter"
        "noImplementationSet"              | "ComponentType method must set default implementation."                        | "missing implementation parameter"
        "noTypeParam"                      | "ComponentTypeBuilder parameter must declare a type parameter (must be generified)."            | "missing type parameter"
        "notLibrarySpec"                   | "Component type 'NotLibrarySpec' must extend 'LibrarySpec'."                                    | "type not extending LibrarySpec"
        "notCustomLibrary"                 | "Component type must be a subtype of 'LibrarySpec'."                                            | "type is LibrarySpec"
        "notImplementingLibraryType"       | "Component implementation 'NotImplementingSampleLibrary' must implement 'SomeLibrarySpec'."     | "implementation not implementing type class"
        "notExtendingDefaultSampleLibrary" | "Component implementation 'NotExtendingDefaultSampleLibrary' must extend 'DefaultLibrarySpec'." | "implementation not extending DefaultLibrarySpec"
        "noDefaultConstructor"             | "Component implementation 'NoDefaultConstructor' must have public default constructor."         | "implementation with no public default constructor"
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


    interface SomeLibrarySpec extends LibrarySpec {}

    static class SomeLibrarySpecImpl extends DefaultLibrarySpec implements SomeLibrarySpec {}

    interface NotLibrarySpec extends ComponentSpec {}

    static class NotImplementingSampleLibrary extends DefaultLibrarySpec implements LibrarySpec {}

    abstract static class NotExtendingDefaultSampleLibrary implements SomeLibrarySpec {}

    static class NoDefaultConstructor extends DefaultLibrarySpec implements SomeLibrarySpec {
        NoDefaultConstructor(String arg) {
        }
    }

    static class Rules {
        @ComponentType
        static void validTypeRule(ComponentTypeBuilder<SomeLibrarySpec> builder) {
            builder.setDefaultImplementation(SomeLibrarySpecImpl)
        }

        @ComponentType
        static void extraParameter(ComponentTypeBuilder<SomeLibrarySpec> builder, String otherParam) {
        }

        @ComponentType
        static void noImplementationSet(ComponentTypeBuilder<SomeLibrarySpec> builder) {
        }

        @ComponentType
        static void noTypeParam(ComponentTypeBuilder builder) {
        }

        @ComponentType
        static void notLibrarySpec(ComponentTypeBuilder<NotLibrarySpec> builder) {
        }

        @ComponentType
        static void notCustomLibrary(ComponentTypeBuilder<LibrarySpec> builder) {
        }

        @ComponentType
        static void notImplementingLibraryType(ComponentTypeBuilder<SomeLibrarySpec> builder) {
            builder.setDefaultImplementation(NotImplementingSampleLibrary)
        }

        @ComponentType
        static void notExtendingDefaultSampleLibrary(ComponentTypeBuilder<SomeLibrarySpec> builder) {
            builder.setDefaultImplementation(NotExtendingDefaultSampleLibrary)
        }

        @ComponentType
        static void noDefaultConstructor(ComponentTypeBuilder<SomeLibrarySpec> builder) {
            builder.setDefaultImplementation(NoDefaultConstructor)
        }
    }
}


