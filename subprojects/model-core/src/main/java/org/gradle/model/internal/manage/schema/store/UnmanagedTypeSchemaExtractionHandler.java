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
import org.gradle.model.internal.manage.schema.UnmanagedModelElementTypeException;

@ThreadSafe
public class UnmanagedTypeSchemaExtractionHandler extends AbstractModelSchemaExtractionHandler {

    public UnmanagedTypeSchemaExtractionHandler() {
        super(new UnmanagedTypeSpec());
    }

    public <T> ModelSchemaExtractionResult<T> extract(ModelType<T> type, ModelSchemaCache cache, ModelSchemaExtractionContext context) {
        throw new UnmanagedModelElementTypeException(type);
    }

    private static class UnmanagedTypeSpec implements Spec<ModelType<?>> {
        public boolean isSatisfiedBy(ModelType<?> type) {
            return !type.getRawClass().isAnnotationPresent(Managed.class);
        }
    }
}
