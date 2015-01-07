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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

public class JdkValueTypeStrategy implements ModelSchemaExtractionStrategy {

    private final static List<ModelType<?>> TYPES = ImmutableList.<ModelType<?>>of(
            ModelType.of(String.class),
            ModelType.of(Boolean.class),
            ModelType.of(Character.class),
            ModelType.of(Integer.class),
            ModelType.of(Long.class),
            ModelType.of(Double.class),
            ModelType.of(BigInteger.class),
            ModelType.of(BigDecimal.class)
    );

    // Expected to be a subset of above
    private final static List<ModelType<?>> NON_FINAL_TYPES = ImmutableList.<ModelType<?>>of(
            ModelType.of(BigInteger.class),
            ModelType.of(BigDecimal.class)
    );

    public <R> ModelSchemaExtractionResult<R> extract(ModelSchemaExtractionContext<R> extractionContext, ModelSchemaCache cache) {
        ModelType<R> type = extractionContext.getType();
        if (TYPES.contains(type)) {
            return new ModelSchemaExtractionResult<R>(ModelSchema.value(type));
        } else {
            for (ModelType<?> nonFinalType : NON_FINAL_TYPES) {
                if (nonFinalType.isAssignableFrom(type)) {
                    throw new InvalidManagedModelElementTypeException(extractionContext, "subclasses of " + nonFinalType + " are not supported");
                }
            }

            return null;
        }
    }

    public Iterable<String> getSupportedManagedTypes() {
        return Collections.singleton("JDK value types: " + Joiner.on(", ").join(Iterables.transform(TYPES, new Function<ModelType<?>, Object>() {
            public Object apply(ModelType<?> input) {
                return input.getRawClass().getSimpleName();
            }
        })));
    }
}
