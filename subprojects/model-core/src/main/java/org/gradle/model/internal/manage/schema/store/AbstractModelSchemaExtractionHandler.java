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

package org.gradle.model.internal.manage.schema.store;

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.InvalidManagedModelElementTypeException;

@ThreadSafe
abstract class AbstractModelSchemaExtractionHandler implements ModelSchemaExtractionHandler {

    private final ModelType<?> supportedSuperType;
    private final Spec<? super ModelType<?>> spec;

    public AbstractModelSchemaExtractionHandler(Spec<? super ModelType<?>> spec) {
        this(ModelType.of(Object.class), spec);
    }

    public AbstractModelSchemaExtractionHandler(ModelType<?> supportedSuperType) {
        this(supportedSuperType, Specs.satisfyAll());
    }

    public AbstractModelSchemaExtractionHandler(ModelType<?> supportedSuperType, Spec<? super ModelType<?>> spec) {
        this.supportedSuperType = supportedSuperType;
        this.spec = spec;
    }

    public ModelType<?> getSupportedSuperType() {
        return supportedSuperType;
    }

    public Spec<? super ModelType<?>> getSpec() {
        return spec;
    }

    protected <T> InvalidManagedModelElementTypeException invalidMethod(ModelType<T> type, String methodName, String message) {
        return new InvalidManagedModelElementTypeException(type, message + " (method: " + methodName + ")");
    }

    protected <T> InvalidManagedModelElementTypeException invalid(ModelType<T> type, String message) {
        return new InvalidManagedModelElementTypeException(type, message);
    }

    protected <T> InvalidManagedModelElementTypeException invalid(ModelType<T> type, String message, InvalidManagedModelElementTypeException e) {
        return new InvalidManagedModelElementTypeException(type, message, e);
    }
}
