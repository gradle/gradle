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

package org.gradle.model.internal.inspect;

import com.google.common.reflect.TypeToken;
import org.gradle.model.internal.type.ModelType;

import java.lang.annotation.Annotation;

public abstract class AbstractAnnotationDrivenModelRuleExtractor<T extends Annotation> implements MethodModelRuleExtractor {

    private static final ModelType<Void> VOID = ModelType.of(Void.TYPE);

    private final Class<T> annotationType;

    protected AbstractAnnotationDrivenModelRuleExtractor() {
        @SuppressWarnings("unchecked") Class<T> annotationType = (Class<T>) new TypeToken<T>(getClass()) {}.getRawType();
        this.annotationType = annotationType;
    }

    public Class<T> getAnnotationType() {
        return annotationType;
    }

    @Override
    public boolean isSatisfiedBy(MethodRuleDefinition<?, ?> ruleDefinition) {
        return ruleDefinition.isAnnotationPresent(annotationType);
    }

    @Override
    public String getDescription() {
        return String.format("annotated with @%s", annotationType.getSimpleName());
   }

    protected void validateIsVoidMethod(MethodRuleDefinition<?, ?> ruleDefinition, RuleSourceValidationProblemCollector problems) {
        if (!isVoidMethod(ruleDefinition)) {
            problems.add(ruleDefinition, "A method " + getDescription() + " must have void return type.");
        }
    }

    protected boolean isVoidMethod(MethodRuleDefinition<?, ?> ruleDefinition) {
        return ruleDefinition.getReturnType().equals(VOID);
    }
}
