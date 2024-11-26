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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

import javax.annotation.Nullable;

/**
 * A factory class for creating additional data builders for various types of additional data.
 *
 * @since 8.12
 */
@Incubating
public interface AdditionalDataBuilderFactory {

    /**
     * Registers a provider for additional data of the given type.
     *
     * @param dataType The type of additional data to provide
     * @param provider The builder function, which will be called to create a builder for the given additional data type
     * @since 8.12
     */
    void registerAdditionalDataProvider(Class<?> dataType, AdditionalDataBuilderProvider provider);

    /**
     * Returns a comma-separated string of the supported additional data types.
     *
     * @return a comma-separated list of supported types
     * @since 8.12
     */
    String getSupportedTypes();

    /**
     * Creates a builder for the specified additional data type.
     *
     * @param specType The type of additional data spec
     * @param additionalData The additional data instance (nullable)
     * @return the additional data builder
     * @since 8.12
     */
    <U extends AdditionalDataSpec> AdditionalDataBuilder<? extends AdditionalData> createAdditionalDataBuilder(Class<? extends U> specType, @Nullable AdditionalData additionalData);

    /**
     * Checks if there is a provider for the specified spec type.
     *
     * @param specType The spec type
     * @return true if a provider exists, false otherwise
     * @since 8.12
     */
    <U extends AdditionalDataSpec> boolean hasProviderForSpec(Class<? extends U> specType);
}
