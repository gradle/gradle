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
import org.gradle.language.base.plugins.ComponentModelBasePlugin
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.collection.CollectionBuilder
import org.gradle.model.internal.core.ExtractedModelRule
import org.gradle.model.internal.core.ModelActionRole
import org.gradle.model.internal.core.ModelReference
import org.gradle.platform.base.BinarySpec
import org.gradle.platform.base.BinaryTasks
import org.gradle.platform.base.InvalidModelException
import spock.lang.Unroll

import java.lang.annotation.Annotation

class BinaryTasksModelRuleExtractorTest extends AbstractAnnotationModelRuleExtractorTest {

    BinaryTasksModelRuleExtractor ruleHandler = new BinaryTasksModelRuleExtractor()

    @Override
    Class<? extends Annotation> getAnnotation() {
        return BinaryTasks
    }

    Class<?> ruleClass = Rules.class

    @Unroll
    def "decent error message for #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod)

        when:
        ruleHandler.registration(ruleMethod)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == "${ruleDescription} is not a valid BinaryTask model rule method."
        ex.cause instanceof InvalidModelException
        ex.cause.message == expectedMessage

        where:
        methodName             | expectedMessage                                                                                                             | descr
        "returnValue"          | "Method annotated with @BinaryTasks must not have a return value."                                                          | "non void method"
        "noParams"             | "Method annotated with @BinaryTasks must have a parameter of type '${CollectionBuilder.name}'."                             | "no CollectionBuilder subject"
        "wrongSubject"         | "Method annotated with @BinaryTasks first parameter must be of type '${CollectionBuilder.name}'."                           | "wrong rule subject type"
        "noBinaryParameter"    | "Method annotated with @BinaryTasks must have one parameter extending BinarySpec. Found no parameter extending BinarySpec." | "no component spec parameter"
        "rawCollectionBuilder" | "Parameter of type 'CollectionBuilder' must declare a type parameter extending 'Task'."                                     | "non typed CollectionBuilder parameter"
    }

    @Unroll
    def "applies ComponentModelBasePlugin and adds binary task creation rule for plain sample binary"() {
        when:
        def registration = ruleHandler.registration(ruleDefinitionForMethod("validTypeRule"))

        then:
        registration.ruleDependencies == [ComponentModelBasePlugin]
        registration.type == ExtractedModelRule.Type.ACTION
        registration.actionRole == ModelActionRole.Defaults
        registration.action.subject == ModelReference.of("binaries")
    }

    interface SomeBinary extends BinarySpec {}

    static class Rules {

        @BinaryTasks
        static String returnValue(CollectionBuilder<Task> builder, SomeBinary binary) {
        }

        @BinaryTasks
        static void noParams() {
        }

        @BinaryTasks
        static void wrongSubject(binary) {
        }

        @BinaryTasks
        static void rawCollectionBuilder(CollectionBuilder tasks, SomeBinary binary) {
        }

        @BinaryTasks
        static void noBinaryParameter(CollectionBuilder<Task> builder) {
        }

        @BinaryTasks
        static void validTypeRule(CollectionBuilder<Task> tasks, SomeBinary binary) {
            tasks.create("create${binary.getName()}")
        }
    }
}