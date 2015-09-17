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
import org.gradle.language.base.internal.testinterfaces.RawLibrary
import org.gradle.language.base.internal.testinterfaces.SomeBinarySpec
import org.gradle.language.base.internal.testinterfaces.SomeBinarySubType
import org.gradle.language.base.internal.testinterfaces.SomeLibrary
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.ModelMap
import org.gradle.model.internal.core.*
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.*
import spock.lang.Unroll

import java.lang.annotation.Annotation

class ComponentBinariesModelRuleExtractorTest extends AbstractAnnotationModelRuleExtractorTest {

    ComponentBinariesModelRuleExtractor ruleHandler = new ComponentBinariesModelRuleExtractor()

    @Override
    Class<? extends Annotation> getAnnotation() {
        return ComponentBinaries
    }

    Class<?> ruleClass = Rules

    @Unroll
    def "applies ComponentModelBasePlugin and creates componentBinary rule #descr"() {
        def mockRegistry = Mock(ModelRegistry)

        when:
        def registration = ruleHandler.registration(ruleDefinitionForMethod(ruleName))

        then:
        registration instanceof ExtractedModelAction
        registration.ruleDependencies == [ComponentModelBasePlugin]


        when:
        registration.apply(mockRegistry, ModelPath.ROOT)

        then:
        1 * mockRegistry.configure(_, _, _) >> { ModelActionRole role, ModelAction<?> action, ModelPath scope ->
            assert role == ModelActionRole.Finalize
            assert action.subject == ModelReference.of("components", ComponentSpecContainer)
        }
        0 * _

        where:
        ruleName         | descr
        "rawBinarySpec"  | "for plain BinarySpec"
        "validTypeRule"  | "for plain sample binary"
        "librarySubType" | "for library sub types"
    }

    @Unroll
    def "decent error message for #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod)

        when:
        ruleHandler.registration(ruleMethod)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == "${ruleDescription} is not a valid ComponentBinaries model rule method."
        ex.cause instanceof InvalidModelException
        ex.cause.message == expectedMessage

        where:
        methodName                | expectedMessage                                                                                                                               | descr
        "noParams"                | "Method annotated with @ComponentBinaries must have a parameter of type '${ModelMap.name}'."                                                  | "no ModelMap parameter"
        "wrongSubject"            | "Method annotated with @ComponentBinaries first parameter must be of type '${ModelMap.name}'."                                                | "wrong rule subject type"
        "multipileComponentSpecs" | "Method annotated with @ComponentBinaries must have one parameter extending ComponentSpec. Found multiple parameter extending ComponentSpec." | "additional component spec parameter"
        "noComponentSpec"         | "Method annotated with @ComponentBinaries must have one parameter extending ComponentSpec. Found no parameter extending ComponentSpec."       | "no component spec parameter"
        "returnValue"             | "Method annotated with @ComponentBinaries must not have a return value."                                                                      | "non void method"
        "rawModelMap"             | "Parameter of type '${ModelMap.simpleName}' must declare a type parameter extending 'BinarySpec'."                                            | "non typed ModelMap parameter"
    }

    static class Rules {
        @ComponentBinaries
        static void noParams() {
        }

        @ComponentBinaries
        static void validTypeRule(ModelMap<SomeBinarySpec> binaries, SomeLibrary library) {
            binaries.create("${library.name}Binary", library)
        }

        @ComponentBinaries
        static void rawBinarySpec(ModelMap<BinarySpec> binaries, RawLibrary library) {
            binaries.create("${library.name}Binary", library)
        }

        @ComponentBinaries
        static void rawModelMap(ModelMap binaries, RawLibrary library) {
            binaries.create("${library.name}Binary", library)
        }

        @ComponentBinaries
        static void librarySubType(ModelMap<SomeBinarySubType> binaries, SomeLibrary library) {
            binaries.create("${library.name}Binary", library)
        }

        @ComponentBinaries
        static void wrongSubject(SomeLibrary library) {
        }

        @ComponentBinaries
        static void multipileComponentSpecs(ModelMap<SomeBinarySpec> binaries, SomeLibrary library, SomeLibrary otherLibrary) {
            binaries.create("${library.name}Binary", library)
        }

        @ComponentBinaries
        static void noComponentSpec(ModelMap<SomeBinarySpec> binaries) {
        }

        @ComponentBinaries
        static String returnValue(BinaryTypeBuilder<SomeBinarySpec> builder) {
        }
    }
}
