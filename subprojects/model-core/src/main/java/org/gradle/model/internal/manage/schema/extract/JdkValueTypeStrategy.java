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
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.ModelSchema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

public class JdkValueTypeStrategy implements ModelSchemaExtractionStrategy {

    // Assuming that all types in this list are immutable and final
    public final static List<ModelType<?>> SUPPORTED_TYPES = ImmutableList.<ModelType<?>>of(
            ModelType.of(String.class),
            ModelType.of(Boolean.class),
            ModelType.of(Integer.class),
            ModelType.of(Long.class),
            ModelType.of(Double.class),
            ModelType.of(BigInteger.class),
            ModelType.of(BigDecimal.class)
    );

    public <R> ModelSchemaExtractionResult<R> extract(ModelSchemaExtractionContext<R> extractionContext, ModelSchemaCache cache) {
        ModelType<R> type = extractionContext.getType();
        if (SUPPORTED_TYPES.contains(type)) {
            return new ModelSchemaExtractionResult<R>(ModelSchema.value(type));
        } else {
            return null;
        }
    }

    public Iterable<String> getSupportedTypes() {
        return Collections.singleton("JDK value types: " + Joiner.on(", ").join(Iterables.transform(SUPPORTED_TYPES, new Function<ModelType<?>, Object>() {
            public Object apply(ModelType<?> input) {
                return input.getRawClass().getSimpleName();
            }
        })));
    }
}
