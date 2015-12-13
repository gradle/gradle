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

import org.gradle.internal.reflect.MethodDescription
import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.internal.core.ExtractedModelRule
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.inspect.DefaultMethodModelRuleExtractionContext
import org.gradle.model.internal.inspect.DefaultMethodRuleDefinition
import org.gradle.model.internal.inspect.MethodRuleDefinition
import spock.lang.Unroll

import java.lang.annotation.Annotation
import java.lang.reflect.Method

public abstract class AbstractAnnotationModelRuleExtractorTest extends ProjectRegistrySpec {
    def ruleDefinition = Mock(MethodRuleDefinition)

    protected abstract AbstractAnnotationDrivenComponentModelRuleExtractor getRuleHandler();

    abstract Class<? extends Annotation> getAnnotation();
    abstract Class<?> getRuleClass();

    @Unroll
    def "handles methods annotated with @#annotationName"() {
        when:
        1 * ruleDefinition.getAnnotation(annotation) >> null

        then:
        !ruleHandler.isSatisfiedBy(ruleDefinition)


        when:
        1 * ruleDefinition.getAnnotation(annotation) >> Mock(annotation)

        then:
        ruleHandler.isSatisfiedBy(ruleDefinition)
        where:
        annotationName << [annotation.getSimpleName()]
    }

    ExtractedModelRule extract(MethodRuleDefinition<?, ?> definition) {
        def context = new DefaultMethodModelRuleExtractionContext(ruleClass, null)
        def registration = ruleHandler.registration(definition, context)
        if (context.hasProblems()) {
            throw new InvalidModelRuleDeclarationException(context.problems.format())
        }
        return registration
    }

    MethodRuleDefinition<?, ?> ruleDefinitionForMethod(String methodName) {
        for (Method candidate : ruleClass.getDeclaredMethods()) {
            if (candidate.getName().equals(methodName)) {
                return DefaultMethodRuleDefinition.create(ruleClass, candidate)
            }
        }
        throw new IllegalArgumentException("Not a test method name")
    }

    String getStringDescription(MethodRuleDefinition ruleDefinition) {
        def builder = new StringBuilder()
        ruleDefinition.descriptor.describeTo(builder)
        builder.toString()
    }

    String getStringDescription(Method method) {
        return MethodDescription.name(method.getName())
                .takes(method.getGenericParameterTypes())
                .toString();
    }
}
