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

package org.gradle.api.problems.internal;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class AdditionalDataBuilderFactory {

    static BiMap<Class<?>, Class<?>> supportedAdditionalDataTypes = ImmutableBiMap.<Class<?>, Class<?>>of(
        GeneralDataSpec.class, GeneralData.class,
        DeprecationDataSpec.class, DeprecationData.class,
        TypeValidationDataSpec.class, TypeValidationData.class,
        PropertyTraceDataSpec.class, PropertyTraceData.class
    );

    static Map<Class<?>, Function<Object, AdditionalDataBuilder<? extends AdditionalData>>> additionalDataBuilderProviders = ImmutableMap.<Class<?>, Function<Object, AdditionalDataBuilder<? extends AdditionalData>>>of(
        GeneralDataSpec.class, new Function<Object, AdditionalDataBuilder<? extends AdditionalData>>() {
            @Override
            public AdditionalDataBuilder<? extends AdditionalData> apply(@Nullable Object instance) {
                if (instance == null) {
                    return DefaultGeneralData.builder();
                }
                return DefaultGeneralData.builder((GeneralData) instance);
            }
        },
        DeprecationDataSpec.class, new Function<Object, AdditionalDataBuilder<? extends AdditionalData>>() {
            @Override
            public AdditionalDataBuilder<? extends AdditionalData> apply(@Nullable Object instance) {
                if (instance == null) {
                    return DefaultDeprecationData.builder();
                }
                return DefaultDeprecationData.builder((DeprecationData) instance);
            }
        },
        TypeValidationDataSpec.class, new Function<Object, AdditionalDataBuilder<? extends AdditionalData>>() {
            @Override
            public AdditionalDataBuilder<? extends AdditionalData> apply(@Nullable Object instance) {
                if (instance == null) {
                    return DefaultTypeValidationData.builder();
                }
                return DefaultTypeValidationData.builder((TypeValidationData) instance);
            }
        },
        PropertyTraceDataSpec.class, new Function<Object, AdditionalDataBuilder<? extends AdditionalData>>() {
            @Override
            public AdditionalDataBuilder<? extends AdditionalData> apply(@Nullable Object instance) {
                if (instance == null) {
                    return DefaultPropertyTraceData.builder();
                }
                return DefaultPropertyTraceData.builder((PropertyTraceData) instance);
            }
        }
    );

    @SuppressWarnings("unchecked")
    public static <T extends AdditionalData, S extends AdditionalDataSpec> AdditionalDataBuilder<T> builderFor(Class<? extends S> type) {
        Preconditions.checkNotNull(type);
        Function<Object, AdditionalDataBuilder<? extends AdditionalData>> provider = additionalDataBuilderProviders.get(type);
        if (provider != null) {
            return (AdditionalDataBuilder<T>) provider.apply(null);

        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    @SuppressWarnings("unchecked")
    public static <S extends AdditionalData, U extends AdditionalDataSpec> AdditionalDataBuilder<S> builderFor(Class<? extends U> type, S instance) {
        Preconditions.checkNotNull(type);
        Function<Object, AdditionalDataBuilder<? extends AdditionalData>> provider = additionalDataBuilderProviders.get(type);
        if (provider != null) {
            return (AdditionalDataBuilder<S>) provider.apply(instance);
        }
        throw new IllegalArgumentException("Unsupported instance: " + instance);
    }

    static <U extends AdditionalDataSpec> AdditionalDataBuilder<? extends AdditionalData> createAdditionalDataBuilder(Class<? extends U> specType, @Nullable AdditionalData additionalData) {
        if (additionalData == null) {
            return builderFor(specType);
        }
        if (isCompatible(specType, additionalData)) {
            return builderFor(specType, additionalData);
        }
        throw new IllegalArgumentException("Additional data of type " + additionalData.getClass() + " is already set");
    }

    private static <U extends AdditionalDataSpec> boolean isCompatible(Class<? extends U> specType, @Nonnull AdditionalData additionalData) {
        for (Map.Entry<Class<?>, Class<?>> entry : supportedAdditionalDataTypes.entrySet()) {
            if (entry.getKey().equals(specType)) {
                return entry.getValue().isInstance(additionalData);
            }
        }
        return false;
    }
}
