/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.provider;

import com.google.common.collect.ImmutableCollection;
import groovy.lang.GString;
import org.gradle.internal.Cast;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.util.internal.GUtil;
import org.jspecify.annotations.Nullable;

public class ValueSanitizers {
    private static final ValueSanitizer<Object> STRING_VALUE_SANITIZER = new ValueSanitizer<Object>() {
        @Override
        @Nullable
        public Object sanitize(@Nullable Object value) {
            if (value instanceof GString) {
                return value.toString();
            } else {
                return value;
            }
        }
    };

    private static final ValueSanitizer<Object> LONG_VALUE_SANITIZER = new ValueSanitizer<Object>() {
        @Override
        @Nullable
        public Object sanitize(@Nullable Object value) {
            if (value instanceof Integer) {
                return ((Integer) value).longValue();
            } else {
                return value;
            }
        }
    };

    private static final ValueSanitizer<Object> IDENTITY_SANITIZER = new ValueSanitizer<Object>() {
        @Override
        @Nullable
        public Object sanitize(@Nullable Object value) {
            return value;
        }
    };

    private static final ValueCollector<Object> IDENTITY_VALUE_COLLECTOR = valueCollectorWithValueSanitizer(IDENTITY_SANITIZER);
    private static final ValueCollector<Object> STRING_VALUE_COLLECTOR = valueCollectorWithValueSanitizer(STRING_VALUE_SANITIZER);
    private static final ValueCollector<Object> LONG_VALUE_COLLECTOR = valueCollectorWithValueSanitizer(LONG_VALUE_SANITIZER);

    private static <T extends Enum<T>> ValueSanitizer<Object> getEnumValueSanitizer(Class<T> enumType) {
        return new ValueSanitizer<Object>() {
            @Override
            @Nullable
            public Object sanitize(@Nullable Object value) {
                if (value instanceof CharSequence) {
                    DeprecationLogger.deprecateBehaviour(String.format("Assigning String value '%s' to property of enum type '%s'.", value, enumType.getCanonicalName()))
                        .willBecomeAnErrorInGradle10()
                        .withUpgradeGuideSection(8, "deprecated_string_to_enum_coercion_for_rich_properties")
                        .nagUser();
                }
                return GUtil.toEnum(enumType, value);
            }
        };
    }

    public static <T> ValueSanitizer<T> forType(@Nullable Class<? extends T> targetType) {
        ValueSanitizer<Object> valueSanitizer;
        if (String.class.equals(targetType)) {
            valueSanitizer = STRING_VALUE_SANITIZER;
        } else if (Long.class.equals(targetType)) {
            valueSanitizer = LONG_VALUE_SANITIZER;
        } else if (targetType != null && targetType.isEnum()) {
            valueSanitizer = getEnumValueSanitizer(Cast.uncheckedCast(targetType));
        } else {
            valueSanitizer = IDENTITY_SANITIZER;
        }
        return Cast.uncheckedCast(valueSanitizer);
    }

    public static <T> ValueCollector<T> collectorFor(@Nullable Class<? extends T> elementType) {
        ValueCollector<Object> valueCollector;
        if (String.class.equals(elementType)) {
            valueCollector = STRING_VALUE_COLLECTOR;
        } else if (Long.class.equals(elementType)) {
            valueCollector = LONG_VALUE_COLLECTOR;
        } else if (elementType != null && elementType.isEnum()) {
            ValueSanitizer<Object> valueSanitizer = getEnumValueSanitizer(Cast.uncheckedCast(elementType));
            valueCollector = valueCollectorWithValueSanitizer(valueSanitizer);
        } else {
            valueCollector = IDENTITY_VALUE_COLLECTOR;
        }
        return Cast.uncheckedCast(valueCollector);
    }

    private static ValueCollector<Object> valueCollectorWithValueSanitizer(ValueSanitizer<Object> sanitizer) {
        return new ValueCollector<Object>() {
            @Override
            public void add(@Nullable Object value, ImmutableCollection.Builder<Object> dest) {
                dest.add(sanitizer.sanitize(value));
            }

            @Override
            public void addAll(Iterable<?> values, ImmutableCollection.Builder<Object> dest) {
                for (Object value : values) {
                    add(value, dest);
                }
            }
        };
    }
}
