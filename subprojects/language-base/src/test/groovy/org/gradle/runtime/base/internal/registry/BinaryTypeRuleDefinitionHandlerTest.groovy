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

package org.gradle.runtime.base.internal.registry

import org.gradle.api.initialization.Settings
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.internal.inspect.DefaultMethodRuleDefinition
import org.gradle.model.internal.inspect.MethodRuleDefinition
import org.gradle.model.internal.inspect.RuleSourceDependencies
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.runtime.base.BinarySpec
import org.gradle.runtime.base.BinaryType
import org.gradle.runtime.base.BinaryTypeBuilder
import org.gradle.runtime.base.InvalidComponentModelException
import org.gradle.runtime.base.binary.DefaultBinarySpec
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Method

class BinaryTypeRuleDefinitionHandlerTest extends Specification {

        Instantiator instantiator = new DirectInstantiator()
        def ruleDefinition = Mock(MethodRuleDefinition)
        def modelRegistry = Mock(ModelRegistry)
        def ruleDependencies = Mock(RuleSourceDependencies)

        BinaryTypeRuleDefinitionHandler componentRuleHandler = new BinaryTypeRuleDefinitionHandler(instantiator)

        def "handles methods annotated with @BinaryType"() {
            when:
            1 * ruleDefinition.getAnnotation(BinaryType) >> null

            then:
            !componentRuleHandler.isSatisfiedBy(ruleDefinition)


            when:
            1 * ruleDefinition.getAnnotation(BinaryType) >> Mock(BinaryType)

            then:
            componentRuleHandler.isSatisfiedBy(ruleDefinition)
        }

        def "applies ComponentModelBasePlugin and creates binary type rule"() {
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
            def ruleMethod = ruleDefinitionForMethod(methodName)
            def ruleDescription = getStringDescription(ruleMethod)

            when:
            componentRuleHandler.register(ruleMethod, modelRegistry, ruleDependencies)

            then:
            def ex = thrown(InvalidModelRuleDeclarationException)
            ex.message == "${ruleDescription} is not a valid binary model rule method."
            ex.cause instanceof InvalidComponentModelException
            ex.cause.message == expectedMessage

            where:
            methodName                         | expectedMessage                                                                          | descr
            "extraParameter"                   | "BinaryType method must have a single parameter of type BinaryTypeBuilder."              | "additional rule parameter"
            "returnValue"                      | "BinaryType method must not have a return value."                                        | "method with return type"
            "implementationSetMultipleTimes"   | "BinaryType method cannot set default implementation multiple times."                    | "implementation set multiple times"
            "noTypeParam"                      | "BinaryTypeBuilder parameter must declare a type parameter."                             | "missing type parameter"
            "notBinarySpec"                    | "Binary type 'NotBinarySpec' must extend 'BinarySpec'."                                  | "type not extending BinarySpec"
            "notCustomBinary"                  | "Binary type must be a subtype of 'BinarySpec'."                                         | "type is BinarySpec"
            "notImplementingBinaryType"        | "Binary implementation 'NotImplementingCustomBinary' must implement 'SomeBinarySpec'."   | "implementation not implementing type class"
            "notExtendingDefaultSampleLibrary" | "Binary implementation 'NotExtendingDefaultBinarySpec' must extend 'DefaultBinarySpec'." | "implementation not extending DefaultBinarySpec"
            "noDefaultConstructor"             | "Binary implementation 'NoDefaultConstructor' must have public default constructor."     | "implementation with no public default constructor"
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
            componentRuleHandler = new ComponentModelRuleDefinitionHandler(instantiator)
        }

        interface SomeBinarySpec extends BinarySpec {}

        static class SomeBinarySpecImpl extends DefaultBinarySpec implements SomeBinarySpec {}

        static class SomeBinarySpecOtherImpl extends SomeBinarySpecImpl {}

        interface NotBinarySpec {}

        static class NotImplementingCustomBinary extends DefaultBinarySpec implements BinarySpec {}

        abstract static class NotExtendingDefaultBinarySpec implements BinaryTypeRuleDefinitionHandlerTest.SomeBinarySpec {}

        static class NoDefaultConstructor extends DefaultBinarySpec implements SomeBinarySpec {
            NoDefaultConstructor(String arg) {
            }
        }

        static class Rules {
            @BinaryType
            static void validTypeRule(BinaryTypeBuilder<SomeBinarySpec> builder) {
                builder.setDefaultImplementation(SomeBinarySpecImpl)
            }

            @BinaryType
            static void extraParameter(BinaryTypeBuilder<SomeBinarySpec> builder, String otherParam) {
            }

            @BinaryType
            static String returnValue(BinaryTypeBuilder<SomeBinarySpec> builder) {
            }

            @BinaryType
            static void noImplementationSet(BinaryTypeBuilder<SomeBinarySpec> builder) {
            }

            @BinaryType
            static void implementationSetMultipleTimes(BinaryTypeBuilder<SomeBinarySpec> builder) {
                builder.setDefaultImplementation(SomeBinarySpecImpl)
                builder.setDefaultImplementation(SomeBinarySpecOtherImpl)
            }

            @BinaryType
            static void noTypeParam(BinaryTypeBuilder builder) {
            }

            @BinaryType
            static void notBinarySpec(BinaryTypeBuilder<NotBinarySpec> builder) {
            }

            @BinaryType
            static void notCustomBinary(BinaryTypeBuilder<BinarySpec> builder) {
            }

            @BinaryType
            static void notImplementingBinaryType(BinaryTypeBuilder<SomeBinarySpec> builder) {
                builder.setDefaultImplementation(NotImplementingCustomBinary)
            }

            @BinaryType
            static void notExtendingDefaultSampleLibrary(BinaryTypeBuilder<SomeBinarySpec> builder) {
                builder.setDefaultImplementation(NotExtendingDefaultBinarySpec)
            }

            @BinaryType
            static void noDefaultConstructor(BinaryTypeBuilder<SomeBinarySpec> builder) {
                builder.setDefaultImplementation(NoDefaultConstructor)
            }
        }
}
