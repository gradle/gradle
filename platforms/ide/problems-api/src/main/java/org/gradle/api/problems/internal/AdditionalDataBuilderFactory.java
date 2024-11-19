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
import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.AdditionalDataSpec;
import org.gradle.api.problems.GeneralData;
import org.gradle.api.problems.GeneralDataSpec;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@ServiceScope(Scope.Build.class)
public final class AdditionalDataBuilderFactory {
    private final Map<Class<?>, DataTypeAndProvider> additionalDataProviders = new HashMap<Class<?>, DataTypeAndProvider>();

    public AdditionalDataBuilderFactory() {
        additionalDataProviders.put(GeneralDataSpec.class, new DataTypeAndProvider(
            GeneralData.class,
            new Function<AdditionalData, AdditionalDataBuilder<? extends AdditionalData>>() {
                @Override
                public AdditionalDataBuilder<? extends AdditionalData> apply(@Nullable AdditionalData instance) {
                    return DefaultGeneralData.builder((GeneralData) instance);
                }
            }));
        additionalDataProviders.put(DeprecationDataSpec.class, new DataTypeAndProvider(
            DeprecationData.class,
            new Function<AdditionalData, AdditionalDataBuilder<? extends AdditionalData>>() {
                @Override
                public AdditionalDataBuilder<? extends AdditionalData> apply(@Nullable AdditionalData instance) {
                    return DefaultDeprecationData.builder((DeprecationData) instance);
                }
            }));
        additionalDataProviders.put(TypeValidationDataSpec.class, new DataTypeAndProvider(
            TypeValidationData.class,
            new Function<AdditionalData, AdditionalDataBuilder<? extends AdditionalData>>() {
                @Override
                public AdditionalDataBuilder<? extends AdditionalData> apply(@Nullable AdditionalData instance) {
                    return DefaultTypeValidationData.builder((TypeValidationData) instance);
                }
            }));
        additionalDataProviders.put(PropertyTraceDataSpec.class, new DataTypeAndProvider(
            PropertyTraceData.class,
            new Function<AdditionalData, AdditionalDataBuilder<? extends AdditionalData>>() {
                @Override
                public AdditionalDataBuilder<? extends AdditionalData> apply(@Nullable AdditionalData instance) {
                    return DefaultPropertyTraceData.builder((PropertyTraceData) instance);
                }
            }));
    }

    /**
     * Registers a provider for additional data of the given type.
     *
     * @param dataType The type of additional data to provide
     * @param provider The builder function, which will be called to create a builder for the given additional data type
     */
    public void registerAdditionalDataProvider(Class<?> dataType, Function<AdditionalData, AdditionalDataBuilder<? extends AdditionalData>> provider) {
        if (additionalDataProviders.put(dataType, new DataTypeAndProvider(dataType, provider)) != null) {
            throw new IllegalArgumentException("Data type: '" + dataType + "' already has an additional data provider registered!");
        }
    }

    public String getSupportedTypes() {
        StringBuilder result = new StringBuilder();
        for (Class<?> key : additionalDataProviders.keySet()) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(key.getName());
        }
        return result.toString();
    }

    @SuppressWarnings("unchecked")
    private <S extends AdditionalData, U extends AdditionalDataSpec> AdditionalDataBuilder<S> builderFor(Class<? extends U> specType, @Nullable S instance, String illegalArgumentMessage) {
        Preconditions.checkNotNull(specType);
        DataTypeAndProvider dataTypeAndProvider = additionalDataProviders.get(specType);
        if (dataTypeAndProvider != null) {
            return (AdditionalDataBuilder<S>) dataTypeAndProvider.builderProvider.apply(instance);
        }
        throw new IllegalArgumentException(illegalArgumentMessage);
    }

    public <U extends AdditionalDataSpec> AdditionalDataBuilder<? extends AdditionalData> createAdditionalDataBuilder(Class<? extends U> specType, @Nullable AdditionalData additionalData) {
        if (additionalData == null) {
            return builderFor(specType, null, "Unsupported type: " + specType);
        }
        if (isCompatible(specType, additionalData)) {
            return builderFor(specType, additionalData, "Unsupported instance: " + additionalData);
        }
        throw new IllegalArgumentException("Additional data of type " + additionalData.getClass() + " is already set");
    }

    public <U extends AdditionalDataSpec> boolean hasProviderForSpec(Class<? extends U> specType) {
        return additionalDataProviders.containsKey(specType);
    }

    private <U extends AdditionalDataSpec> boolean isCompatible(Class<? extends U> specType, @Nonnull AdditionalData additionalData) {
        DataTypeAndProvider dataTypeAndProvider = additionalDataProviders.get(specType);
        return dataTypeAndProvider != null && dataTypeAndProvider.dataType.isInstance(additionalData);
    }

    private static class DataTypeAndProvider {
        final Class<?> dataType;
        final Function<AdditionalData, AdditionalDataBuilder<? extends AdditionalData>> builderProvider;

        private DataTypeAndProvider(Class<?> dataType, Function<AdditionalData, AdditionalDataBuilder<? extends AdditionalData>> builderProvider) {
            this.dataType = dataType;
            this.builderProvider = builderProvider;
        }
    }
}
