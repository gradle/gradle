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
import org.gradle.api.specs.Spec;

import java.lang.annotation.Annotation;

public abstract class AbstractAnnotationDrivenMethodRuleDefinitionHandler<T extends Annotation> implements MethodRuleDefinitionHandler {
    protected final Class<T> annotationType;

    protected AbstractAnnotationDrivenMethodRuleDefinitionHandler() {
        @SuppressWarnings("unchecked") Class<T> annotationType = (Class<T>) new TypeToken<T>(getClass()) {}.getRawType();
        this.annotationType = annotationType;
    }

    public Spec<MethodRuleDefinition<?>> getSpec() {
        return new AnnotationMatchingSpec();
    }

    public String getDescription() {
        return String.format("annotated with @%s", annotationType.getSimpleName());
   }

    private class AnnotationMatchingSpec implements Spec<MethodRuleDefinition<?>> {
        public boolean isSatisfiedBy(MethodRuleDefinition<?> ruleDefinition) {
           return ruleDefinition.getAnnotation(annotationType) != null;
       }
    }
}
