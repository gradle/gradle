/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.buildoption;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

/**
 * A service that determines the value for an {@link InternalOption}.
 */
@ServiceScope({Scope.CrossBuildSession.class, Scope.BuildTree.class})
public interface InternalOptions {

    /**
     * Resolves the value of a given option, allowing to determine whether it was set explicitly.
     */
    <T extends @Nullable Object> Option.Value<T> getOptionValue(InternalOption<T> option);

    /**
     * Determines whether the user explicitly provided a value for the given property (regardless of its type).
     */
    default boolean isExplicitlySet(String propertyName) {
        return getOptionValue(ofStringOrNull(propertyName)).isExplicit();
    }

    /**
     * Resolves non-null value for the given option.
     */
    default <T> T getValue(InternalOption<T> option) {
        T value = getOptionValue(option).get();
        // The compiler ensures `InternalOption<@Nullable Type>` can't be passed, but the IDE can't see it and complains
        assert value != null : "Option '" + option.getPropertyName() + "' is expected to have a non-null value, but was null";
        return value;
    }

    /**
     * Resolves nullable-value for the given option.
     */
    default <T extends @Nullable Object> T getValueOrNull(InternalOption<T> option) {
        return getOptionValue(option).get();
    }

    /**
     * Resolves value of an ad-hoc string option.
     */
    default String getString(String propertyName, String defaultValue) {
        return getValue(ofString(propertyName, defaultValue));
    }

    /**
     * Resolves value of an ad-hoc string option, returning null if the property was not explicitly set.
     */
    default @Nullable String getStringOrNull(String propertyName) {
        return getOptionValue(ofStringOrNull(propertyName)).get();
    }

    /**
     * Resolves value of a boolean option.
     */
    default boolean getBoolean(InternalOption<Boolean> option) {
        return getValue(option);
    }

    /**
     * Resolves value of an ad-hoc boolean option.
     *
     * @apiNote request explicit default value to make it obvious at the declaration side
     */
    default boolean getBoolean(String propertyName, boolean defaultValue) {
        return getValue(ofBoolean(propertyName, defaultValue));
    }

    /**
     * Resolves value of an integer option.
     */
    default int getInt(InternalOption<Integer> option) {
        return getValue(option);
    }

    /**
     * Resolves value of an ad-hoc integer option.
     *
     * @apiNote request explicit default value to make it obvious at the declaration side
     */
    default int getInt(String propertyName, int defaultValue) {
        return getValue(ofInt(propertyName, defaultValue));
    }

    /**
     * Creates a boolean internal option.
     */
    static InternalOption<Boolean> ofBoolean(String propertyName, boolean defaultValue) {
        return new InternalFlag(propertyName, defaultValue);
    }

    /**
     * Creates an integer internal option.
     *
     * @apiNote request explicit default value to make it obvious at the declaration side
     */
    static InternalOption<Integer> ofInt(String propertyName, int defaultValue) {
        return new IntegerInternalOption(propertyName, defaultValue);
    }

    /**
     * Creates a string internal option with a non-null default.
     *
     * @apiNote request explicit default value to make it obvious at the declaration side
     */
    static InternalOption<String> ofString(String propertyName, String defaultValue) {
        return StringInternalOption.of(propertyName, defaultValue);
    }

    /**
     * Creates a string internal option that defaults to {@code null}.
     */
    static InternalOption<@Nullable String> ofStringOrNull(String propertyName) {
        return StringInternalOption.of(propertyName);
    }
}
