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

import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.ModelMap
import org.gradle.model.internal.core.ModelActionRole
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.ComponentBinaries
import org.gradle.platform.base.GeneralComponentSpec
import org.gradle.platform.base.VariantComponentSpec

import java.lang.annotation.Annotation

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf

class ComponentBinariesModelRuleExtractorTest extends AbstractAnnotationModelRuleExtractorTest {

    ComponentBinariesModelRuleExtractor ruleHandler = new ComponentBinariesModelRuleExtractor()

    @Override
    Class<? extends Annotation> getAnnotation() {
        return ComponentBinaries
    }

    Class<?> ruleClass = Rules

    def "applies ComponentModelBasePlugin and creates componentBinary rule #descr"() {
        def registry = Mock(ModelRegistry)

        when:
        def registration = extract(ruleDefinitionForMethod(ruleName))

        then:
        registration.ruleDependencies == [ComponentModelBasePlugin]

        when:
        apply(registration, registry)

        then:
        1 * registry.configureMatching(_, ModelActionRole.Finalize, _)
        0 * _

        where:
        ruleName         | descr
        "rawBinarySpec"  | "for plain BinarySpec"
        "validTypeRule"  | "for plain sample binary"
        "librarySubType" | "for library sub types"
    }

    def "decent error message for #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod.method)

        when:
        extract(ruleMethod)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == """Type ${fullyQualifiedNameOf(ruleClass)} is not a valid rule source:
- Method ${ruleDescription} is not a valid rule method: ${expectedMessage}"""

        where:
        methodName               | expectedMessage                                                                                                                                               | descr
        "noParams"               | "A method annotated with @ComponentBinaries must have at least two parameters."                                                                               | "no ModelMap parameter"
        "wrongSubject"           | "The first parameter of a method annotated with @ComponentBinaries must be of type ${ModelMap.name}."                                                         | "wrong rule subject type"
        "multipleComponentSpecs" | "A method annotated with @ComponentBinaries must have one parameter extending VariantComponentSpec. Found multiple parameter extending VariantComponentSpec." | "additional component spec parameter"
        "noComponentSpec"        | "A method annotated with @ComponentBinaries must have one parameter extending VariantComponentSpec. Found no parameter extending VariantComponentSpec."       | "no component spec parameter"
        "returnValue"            | "A method annotated with @ComponentBinaries must have void return type."                                                                                      | "non void method"
        "rawModelMap"            | "Parameter of type ${ModelMap.simpleName} must declare a type parameter extending BinarySpec."                                                                | "non typed ModelMap parameter"
    }

    static interface SomeBinarySpec extends BinarySpec {}

    static interface SomeLibrary extends GeneralComponentSpec {}

    static interface RawLibrary extends GeneralComponentSpec {}

    static interface SomeBinarySubType extends SomeBinarySpec {}

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
        static void multipleComponentSpecs(ModelMap<SomeBinarySpec> binaries, SomeLibrary library, SomeLibrary otherLibrary) {
            binaries.create("${library.name}Binary", library)
        }

        @ComponentBinaries
        static void noComponentSpec(ModelMap<SomeBinarySpec> binaries) {
        }

        @ComponentBinaries
        static String returnValue(ModelMap<SomeBinarySpec> builder, VariantComponentSpec componentSpec) {
        }
    }
}
