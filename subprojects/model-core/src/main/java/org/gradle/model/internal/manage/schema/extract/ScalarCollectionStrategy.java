/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.collect.ImmutableList;
import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.ScalarCollectionSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.*;

@ThreadSafe
public class ScalarCollectionStrategy extends CollectionStrategy {

    public final static List<ModelType<?>> TYPES = ImmutableList.<ModelType<?>>of(
        ModelType.of(List.class),
        ModelType.of(Set.class)
    );

    public <T> void extract(ModelSchemaExtractionContext<T> extractionContext, ModelSchemaStore store) {
        ModelType<T> type = extractionContext.getType();
        Class<? super T> rawClass = type.getRawClass();
        ModelType<? super T> rawCollectionType = ModelType.of(rawClass);
        if (TYPES.contains(rawCollectionType)) {
            ModelType<?> elementType = type.getTypeVariables().get(0);
            if (ScalarTypes.isScalarType(elementType)) {
                validateType(rawCollectionType, extractionContext, type);
                extractionContext.found(createSchema(type, elementType));
            }
        }
    }

    private <T, E> ScalarCollectionSchema<T, E> createSchema(ModelType<T> type, ModelType<E> elementType) {
        return new ScalarCollectionSchema<T, E>(type, elementType);
    }
}
