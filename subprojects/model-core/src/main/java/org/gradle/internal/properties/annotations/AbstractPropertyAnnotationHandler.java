/*
 * Copyright 2022 the original author or authors.
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

public abstract class AbstractPropertyAnnotationHandler implements PropertyAnnotationHandler {
    private final Class<? extends Annotation> annotationType;
    private final Kind kind;
    private final ImmutableSet<Class<? extends Annotation>> allowedModifiers;

    protected AbstractPropertyAnnotationHandler(Class<? extends Annotation> annotationType, Kind kind, ImmutableSet<Class<? extends Annotation>> allowedModifiers) {
        this.annotationType = annotationType;
        this.kind = kind;
        this.allowedModifiers = allowedModifiers;
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public ImmutableSet<Class<? extends Annotation>> getAllowedModifiers() {
        return allowedModifiers;
    }
}
