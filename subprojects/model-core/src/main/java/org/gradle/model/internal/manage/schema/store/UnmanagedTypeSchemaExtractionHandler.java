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
import org.gradle.model.Managed;
import org.gradle.model.internal.core.ModelType;

@ThreadSafe
public class UnmanagedTypeSchemaExtractionHandler implements ModelSchemaExtractionHandler<Object> {

    private final ModelType<Object> type = ModelType.of(Object.class);

    private final Spec<? super ModelType<?>> spec = new UnmanagedTypeSpec();

    public ModelType<Object> getType() {
        return type;
    }

    public Spec<? super ModelType<?>> getSpec() {
        return spec;
    }

    public <R> ModelSchemaExtractionResult<R> extract(ModelType<R> type, ModelSchemaCache cache, ModelSchemaExtractionContext context) {
        throw new UnmanagedModelElementTypeException(type, context);
    }

    private static class UnmanagedTypeSpec implements Spec<ModelType<?>> {
        public boolean isSatisfiedBy(ModelType<?> type) {
            return !type.getRawClass().isAnnotationPresent(Managed.class);
        }
    }
}
