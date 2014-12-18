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
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.collection.CollectionBuilder
import org.gradle.model.internal.inspect.DefaultMethodRuleDefinition
import org.gradle.model.internal.inspect.MethodRuleDefinition
import org.gradle.model.internal.inspect.RuleSourceDependencies
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.*
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Unroll

import java.lang.annotation.Annotation
import java.lang.reflect.Method

class ComponentBinariesRuleDefinitionHandlerTest extends AbstractAnnotationRuleDefinitionHandlerTest {

    def ruleDefinition = Mock(MethodRuleDefinition)
    def modelRegistry = Mock(ModelRegistry)
    def ruleDependencies = Mock(RuleSourceDependencies)

    ComponentBinariesRuleDefinitionHandler ruleHandler = new ComponentBinariesRuleDefinitionHandler()

    @Override
    Class<? extends Annotation> getAnnotation() {
        return ComponentBinaries
    }

    @Unroll
    def "applies ComponentModelBasePlugin and creates componentBinary rule #descr"() {
        when:
        ruleHandler.register(ruleDefinitionForMethod(ruleName), modelRegistry, ruleDependencies)

        then:
        1 * ruleDependencies.add(ComponentModelBasePlugin)

        and:
        1 * modelRegistry.mutate(_)

        where:
        ruleName         | descr
        "rawBinarySpec"  | "for plain BinarySpec"
        "validTypeRule"  | "for plain sample binary"
        "librarySubType" | "for library sub types"
    }

    def "can use plain BinarySpec"() {
        when:
        ruleHandler.register(ruleDefinitionForMethod("rawBinarySpec"), modelRegistry, ruleDependencies)

        then:
        1 * ruleDependencies.add(ComponentModelBasePlugin)

        and:
        1 * modelRegistry.mutate(_)
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
        ex.message == "${ruleDescription} is not a valid ComponentBinaries model rule method."
        ex.cause instanceof InvalidModelException
        ex.cause.message == expectedMessage

        where:
        methodName                | expectedMessage                                                                                                                               | descr
        "noParams"                | "Method annotated with @ComponentBinaries must have a parameter of type '${CollectionBuilder.name}'."                                         | "no CollectionBuilder parameter"
        "wrongSubject"            | "Method annotated with @ComponentBinaries first parameter must be of type '${CollectionBuilder.name}'."                                       | "wrong rule subject type"
        "multipileComponentSpecs" | "Method annotated with @ComponentBinaries must have one parameter extending ComponentSpec. Found multiple parameter extending ComponentSpec." | "additional component spec parameter"
        "noComponentSpec"         | "Method annotated with @ComponentBinaries must have one parameter extending ComponentSpec. Found no parameter extending ComponentSpec."       | "no component spec parameter"
        "returnValue"             | "Method annotated with @ComponentBinaries must not have a return value."                                                                      | "non void method"
        "rawCollectionBuilder"    | "Parameter of type 'CollectionBuilder' must declare a type parameter extending 'BinarySpec'."                                                 | "non typed CollectionBuilder parameter"
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

    interface SomeLibrary extends ComponentSpec {}

    interface RawLibrary extends ComponentSpec {}

    interface SomeBinarySubType extends SomeBinarySpec {}


    static class Rules {
        @ComponentBinaries
        static void noParams() {
        }

        @ComponentBinaries
        static void validTypeRule(CollectionBuilder<SomeBinarySpec> binaries, SomeLibrary library) {
            binaries.create("${library.name}Binary", library)
        }

        @ComponentBinaries
        static void rawBinarySpec(CollectionBuilder<BinarySpec> binaries, RawLibrary library) {
            binaries.create("${library.name}Binary", library)
        }

        @ComponentBinaries
        static void rawCollectionBuilder(CollectionBuilder binaries, RawLibrary library) {
            binaries.create("${library.name}Binary", library)
        }

        @ComponentBinaries
        static void librarySubType(CollectionBuilder<SomeBinarySubType> binaries, SomeLibrary library) {
            binaries.create("${library.name}Binary", library)
        }

        @ComponentBinaries
        static void wrongSubject(SomeLibrary library) {
        }

        @ComponentBinaries
        static void multipileComponentSpecs(CollectionBuilder<SomeBinarySpec> binaries, SomeLibrary library, SomeLibrary otherLibrary) {
            binaries.create("${library.name}Binary", library)
        }

        @ComponentBinaries
        static void noComponentSpec(CollectionBuilder<SomeBinarySpec> binaries) {
        }

        @ComponentBinaries
        static String returnValue(BinaryTypeBuilder<SomeBinarySpec> builder) {
        }
    }
}