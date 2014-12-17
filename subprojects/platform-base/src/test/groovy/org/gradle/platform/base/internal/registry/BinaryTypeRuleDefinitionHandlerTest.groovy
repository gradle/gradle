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

import org.gradle.api.initialization.Settings
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.internal.inspect.DefaultMethodRuleDefinition
import org.gradle.model.internal.inspect.MethodRuleDefinition
import org.gradle.model.internal.inspect.RuleSourceDependencies
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.BinaryType
import org.gradle.platform.base.BinaryTypeBuilder
import org.gradle.platform.base.InvalidModelException
import org.gradle.platform.base.binary.BaseBinarySpec
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Unroll

import java.lang.annotation.Annotation
import java.lang.reflect.Method

class BinaryTypeRuleDefinitionHandlerTest extends AbstractAnnotationRuleDefinitionHandlerTest {

    Instantiator instantiator = new DirectInstantiator()
    def ruleDependencies = Mock(RuleSourceDependencies)

    BinaryTypeRuleDefinitionHandler ruleHandler = new BinaryTypeRuleDefinitionHandler(instantiator)

    @Override
    Class<? extends Annotation> getAnnotation() {
        return BinaryType
    }

    def "applies ComponentModelBasePlugin and creates binary type rule"() {
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
        ex.message == "${ruleDescription} is not a valid binary model rule method."
        ex.cause instanceof InvalidModelException
        ex.cause.message == expectedMessage

        where:
        methodName                         | expectedMessage                                                                                                        | descr
        "extraParameter"                   | "Method annotated with @BinaryType must have a single parameter of type '${BinaryTypeBuilder.name}'."                                  | "additional rule parameter"
        "returnValue"                      | "Method annotated with @BinaryType must not have a return value."                                                                      | "method with return type"
        "implementationSetMultipleTimes"   | "Method annotated with @BinaryType cannot set default implementation multiple times."                                                  | "implementation set multiple times"
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

    interface SomeBinarySpec extends BinarySpec {}

    static class SomeBinarySpecImpl extends BaseBinarySpec implements SomeBinarySpec {}

    static class SomeBinarySpecOtherImpl extends SomeBinarySpecImpl {}

    interface NotBinarySpec {}

    static class NotImplementingCustomBinary extends BaseBinarySpec implements BinarySpec {}

    abstract static class NotExtendingBaseBinarySpec implements BinaryTypeRuleDefinitionHandlerTest.SomeBinarySpec {}

    static class NoDefaultConstructor extends BaseBinarySpec implements SomeBinarySpec {
        NoDefaultConstructor(String arg) {
        }
    }

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
        static void noImplementationSet(BinaryTypeBuilder<SomeBinarySpec> builder) {
        }

        @BinaryType
        static void implementationSetMultipleTimes(BinaryTypeBuilder<SomeBinarySpec> builder) {
            builder.defaultImplementation(SomeBinarySpecImpl)
            builder.defaultImplementation(SomeBinarySpecOtherImpl)
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
