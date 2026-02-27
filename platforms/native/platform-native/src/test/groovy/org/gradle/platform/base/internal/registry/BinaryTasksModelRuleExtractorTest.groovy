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

import org.gradle.api.Task
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.ModelMap
import org.gradle.model.internal.core.ModelAction
import org.gradle.model.internal.core.ModelActionRole
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.BinaryTasks
import org.gradle.platform.base.plugins.BinaryBasePlugin

import java.lang.annotation.Annotation

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf

class BinaryTasksModelRuleExtractorTest extends AbstractAnnotationModelRuleExtractorTest {

    BinaryTasksModelRuleExtractor ruleHandler = new BinaryTasksModelRuleExtractor()

    @Override
    Class<? extends Annotation> getAnnotation() {
        return BinaryTasks
    }

    Class<?> ruleClass = Rules.class

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
        methodName                 | expectedMessage                                                                                                                     | descr
        "returnValue"              | "A method annotated with @BinaryTasks must have void return type."                                                                  | "non void method"
        "noParams"                 | "A method annotated with @BinaryTasks must have at least two parameters."                                                           | "no ModelMap subject"
        "wrongSubject"             | "The first parameter of a method annotated with @BinaryTasks must be of type org.gradle.model.ModelMap."                            | "wrong rule subject type"
        "noBinaryParameter"        | "A method annotated with @BinaryTasks must have one parameter extending BinarySpec. Found no parameter extending BinarySpec."       | "no binary spec parameter"
        "multipleBinaryParameters" | "A method annotated with @BinaryTasks must have one parameter extending BinarySpec. Found multiple parameter extending BinarySpec." | "multiple binary spec parameters"
        "rawModelMap"              | "Parameter of type ${ModelMap.simpleName} must declare a type parameter extending Task."                                            | "non typed ModelMap parameter"
    }

    def "reports multiple problems with rule definition"() {
        def ruleMethod = ruleDefinitionForMethod("multipleProblems")
        def ruleDescription = getStringDescription(ruleMethod.method)

        when:
        extract(ruleMethod)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == """Type ${fullyQualifiedNameOf(ruleClass)} is not a valid rule source:
- Method ${ruleDescription} is not a valid rule method: A method annotated with @BinaryTasks must have void return type.
- Method ${ruleDescription} is not a valid rule method: The first parameter of a method annotated with @BinaryTasks must be of type org.gradle.model.ModelMap.
- Method ${ruleDescription} is not a valid rule method: A method annotated with @BinaryTasks must have one parameter extending BinarySpec. Found no parameter extending BinarySpec."""
    }

    def "applies ComponentModelBasePlugin and adds binary task creation rule for plain sample binary"() {
        def mockRegistry = Mock(ModelRegistry)

        when:
        def registration = extract(ruleDefinitionForMethod("validTypeRule"))

        then:
        registration.ruleDependencies == [BinaryBasePlugin]

        when:
        apply(registration, mockRegistry)

        then:
        1 * mockRegistry.configure(_, _) >> { ModelActionRole role, ModelAction action ->
            assert role == ModelActionRole.Defaults
            assert action.subject == ModelReference.of("binaries", BinaryContainer.class)
        }
        0 * _
    }

    static interface SomeBinarySpec extends BinarySpec {}

    class Rules {

        @BinaryTasks
        static String returnValue(ModelMap<Task> builder, SomeBinarySpec binary) {
        }

        @BinaryTasks
        static void noParams() {
        }

        @BinaryTasks
        static void wrongSubject(BinarySpec binary, String input) {
        }

        @BinaryTasks
        static void rawModelMap(ModelMap tasks, SomeBinarySpec binary) {
        }

        @BinaryTasks
        static void noBinaryParameter(ModelMap<Task> builder) {
        }

        @BinaryTasks
        static void multipleBinaryParameters(ModelMap<Task> builder, BinarySpec b1, BinarySpec b2) {
        }

        @BinaryTasks
        private <T> T multipleProblems(String p1, String p2) {
        }

        @BinaryTasks
        static void validTypeRule(ModelMap<Task> tasks, SomeBinarySpec binary) {
            tasks.create("create${binary.getName()}")
        }
    }
}
