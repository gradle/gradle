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
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class AdditionalDataBuilderFactory {

    static String getSupportedTypes() {
        StringBuilder result = new StringBuilder();
        for (Class<?> key : additionalDataBuilderProviders.keySet()) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(key.getName());
        }
        return result.toString();
    }

    static class DataTypeAndProvider {
        final Class<?> dataType;
        final Function<Object, AdditionalDataBuilder<? extends AdditionalData>> builderProvider;

        DataTypeAndProvider(Class<?> dataType, Function<Object, AdditionalDataBuilder<? extends AdditionalData>> builderProvider) {
            this.dataType = dataType;
            this.builderProvider = builderProvider;
        }
    }

    static Map<Class<?>, DataTypeAndProvider> additionalDataBuilderProviders = ImmutableMap.<Class<?>, DataTypeAndProvider>builder()
        .put(GeneralDataSpec.class, new DataTypeAndProvider(
            GeneralData.class,
            new Function<Object, AdditionalDataBuilder<? extends AdditionalData>>() {
                @Override
                public AdditionalDataBuilder<? extends AdditionalData> apply(@Nullable Object instance) {
                    if (instance == null) {
                        return DefaultGeneralData.builder();
                    }
                    return DefaultGeneralData.builder((GeneralData) instance);
                }
            }))
        .put(DeprecationDataSpec.class, new DataTypeAndProvider(
            DeprecationData.class,
            new Function<Object, AdditionalDataBuilder<? extends AdditionalData>>() {
                @Override
                public AdditionalDataBuilder<? extends AdditionalData> apply(@Nullable Object instance) {
                    if (instance == null) {
                        return DefaultDeprecationData.builder();
                    }
                    return DefaultDeprecationData.builder((DeprecationData) instance);
                }
            }))
        .put(TypeValidationDataSpec.class, new DataTypeAndProvider(
            TypeValidationData.class,
            new Function<Object, AdditionalDataBuilder<? extends AdditionalData>>() {
                @Override
                public AdditionalDataBuilder<? extends AdditionalData> apply(@Nullable Object instance) {
                    if (instance == null) {
                        return DefaultTypeValidationData.builder();
                    }
                    return DefaultTypeValidationData.builder((TypeValidationData) instance);
                }
            }))
        .put(PropertyTraceDataSpec.class, new DataTypeAndProvider(
            PropertyTraceData.class,
            new Function<Object, AdditionalDataBuilder<? extends AdditionalData>>() {
                @Override
                public AdditionalDataBuilder<? extends AdditionalData> apply(@Nullable Object instance) {
                    if (instance == null) {
                        return DefaultPropertyTraceData.builder();
                    }
                    return DefaultPropertyTraceData.builder((PropertyTraceData) instance);
                }
            }))
        .build();

    @SuppressWarnings("unchecked")
    public static <T extends AdditionalData, S extends AdditionalDataSpec> AdditionalDataBuilder<T> builderFor(Class<? extends S> specType) {
        Preconditions.checkNotNull(specType);
        DataTypeAndProvider dataTypeAndProvider = additionalDataBuilderProviders.get(specType);
        if (dataTypeAndProvider != null) {
            return (AdditionalDataBuilder<T>) dataTypeAndProvider.builderProvider.apply(null);
        }
        throw new IllegalArgumentException("Unsupported type: " + specType);
    }

    @SuppressWarnings("unchecked")
    public static <S extends AdditionalData, U extends AdditionalDataSpec> AdditionalDataBuilder<S> builderFor(Class<? extends U> specType, S instance) {
        Preconditions.checkNotNull(specType);
        DataTypeAndProvider dataTypeAndProvider = additionalDataBuilderProviders.get(specType);
        if (dataTypeAndProvider != null) {
            return (AdditionalDataBuilder<S>) dataTypeAndProvider.builderProvider.apply(instance);
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
        DataTypeAndProvider dataTypeAndProvider = additionalDataBuilderProviders.get(specType);
        return dataTypeAndProvider != null && dataTypeAndProvider.dataType.isInstance(additionalData);
    }
}
