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

package org.gradle.internal.schema;

import java.lang.annotation.Annotation;

public abstract class AbstractPropertySchemaExtractor<B extends InstanceSchema.Builder<?>> implements PropertySchemaExtractor<B> {
    private final Class<? extends Annotation> annotationType;

    public AbstractPropertySchemaExtractor(Class<? extends Annotation> annotationType) {
        this.annotationType = annotationType;
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }

    @Override
    public String toString() {
        return annotationType.getSimpleName();
    }
}
