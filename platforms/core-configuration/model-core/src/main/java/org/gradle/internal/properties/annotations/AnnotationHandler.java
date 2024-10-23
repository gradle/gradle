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

import java.lang.annotation.Annotation;

/**
 * Base class for handling validation, dependency handling, and skipping for a function or property marked with
 * a given annotation.
 */
public interface AnnotationHandler {
    /**
     * The annotation type which this handler is responsible for.
     */
    Class<? extends Annotation> getAnnotationType();

    /**
     * The modifier annotations allowed for the handled function type. This set can further be restricted by the actual work type.
     */
    ImmutableSet<Class<? extends Annotation>> getAllowedModifiers();
}
