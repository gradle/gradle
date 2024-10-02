/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.properties.annotations;

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.lang.annotation.Annotation;

/**
 * Handles validation, dependency handling, and skipping for a method marked with a given annotation.
 *
 * <p>Each handler must be registered as a global service.</p>
 */
@ServiceScope(Scope.Global.class)
public interface MethodAnnotationHandler {
    /**
     * The annotation type which this handler is responsible for.
     */
    Class<? extends Annotation> getAnnotationType();

    /**
     * The modifier annotations allowed for the handled method type. This set can further be restricted by the actual work type.
     */
    ImmutableSet<Class<? extends Annotation>> getAllowedModifiers();

    /**
     * Does this handler do something useful with the method that match it? Or can these methods be ignored?
     *
     * Should consider splitting up this type, perhaps into something that inspects the methods and produces the actual handlers and validation problems.
     */
    boolean isMethodRelevant();

    /**
     * Visits problems associated with the given method, if any.
     */
    default void validateMethodMetadata(MethodMetadata methodMetadata, TypeValidationContext validationContext) {}

}
