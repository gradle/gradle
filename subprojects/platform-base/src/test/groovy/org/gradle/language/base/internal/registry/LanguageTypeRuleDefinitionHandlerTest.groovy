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

package org.gradle.language.base.internal.registry

import org.gradle.language.base.LanguageSourceSet
import org.gradle.platform.base.LanguageType
import org.gradle.platform.base.LanguageTypeBuilder
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.internal.inspect.DefaultMethodRuleDefinition
import org.gradle.model.internal.inspect.MethodRuleDefinition
import org.gradle.model.internal.inspect.RuleSourceDependencies
import org.gradle.platform.base.InvalidModelException
import org.gradle.platform.base.internal.registry.AbstractAnnotationRuleDefinitionHandlerTest
import org.gradle.platform.base.internal.registry.LanguageTypeRuleDefinitionHandler
import spock.lang.Unroll

import java.lang.annotation.Annotation
import java.lang.reflect.Method

class LanguageTypeRuleDefinitionHandlerTest extends AbstractAnnotationRuleDefinitionHandlerTest {

    def ruleDependencies = Mock(RuleSourceDependencies)

    LanguageTypeRuleDefinitionHandler ruleHandler = new LanguageTypeRuleDefinitionHandler()

    @Override
    Class<? extends Annotation> getAnnotation() {
        return LanguageType
    }

    @Unroll
    def "decent error message for #descr"() {
        def ruleMethod = ruleDefinitionForMethod(methodName)
        def ruleDescription = getStringDescription(ruleMethod)

        when:
        ruleHandler.register(ruleMethod, modelRegistry, ruleDependencies)

        then:
        def ex = thrown(InvalidModelRuleDeclarationException)
        ex.message == "${ruleDescription} is not a valid language model rule method."
        ex.cause instanceof InvalidModelException
        ex.cause.message == expectedMessage

        where:
        methodName               | expectedMessage                                                                                           | descr
        "returnValue"            | "Method annotated with @LanguageType must not have a return value."                                       | "non void method"
        "noParams"               | "Method annotated with @LanguageType must have a single parameter of type '${LanguageTypeBuilder.name}'." | "no LanguageTypeBuilder subject"
        "wrongSubject"           | "Method annotated with @LanguageType must have a single parameter of type '${LanguageTypeBuilder.name}'." | "wrong rule subject type"
        "rawLanguageTypeBuilder" | "Parameter of type 'org.gradle.platform.base.LanguageTypeBuilder' must declare a type parameter."         | "non typed CollectionBuilder parameter"
    }

    def ruleDefinitionForMethod(String methodName) {
        for (Method candidate : Rules.class.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                return DefaultMethodRuleDefinition.create(Rules.class, candidate)
            }
        }
        throw new IllegalArgumentException("Not a test method name")
    }

    def getStringDescription(MethodRuleDefinition ruleDefinition) {
        def builder = new StringBuilder()
        ruleDefinition.descriptor.describeTo(builder)
        builder.toString()
    }

    interface CustomLanguageSourceSet extends LanguageSourceSet {}


    static class Rules {

        @LanguageType
        String returnValue(LanguageTypeBuilder<CustomLanguageSourceSet> languageBuilder) {
        }

        @LanguageType
        void noParams() {
        }

        @LanguageType
        void wrongSubject(LanguageSourceSet sourcet) {
        }

        @LanguageType
        void rawLanguageTypeBuilder(LanguageTypeBuilder builder) {
        }


        @LanguageType
        String registerLanguage(LanguageTypeBuilder<CustomLanguageSourceSet> languageBuilder) {
        }

    }
}
