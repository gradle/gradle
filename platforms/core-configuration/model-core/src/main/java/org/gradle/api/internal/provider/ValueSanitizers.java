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

import javax.annotation.Nullable;

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
    private static final ValueSanitizer<Object> IDENTITY_SANITIZER = new ValueSanitizer<Object>() {
        @Override
        @Nullable
        public Object sanitize(@Nullable Object value) {
            return value;
        }
    };
    private static final ValueCollector<Object> IDENTITY_VALUE_COLLECTOR = new ValueCollector<Object>() {
        @Override
        public void add(@Nullable Object value, ImmutableCollection.Builder<Object> dest) {
            dest.add(value);
        }

        @Override
        public void addAll(Iterable<?> values, ImmutableCollection.Builder<Object> dest) {
            dest.addAll(values);
        }
    };
    private static final ValueCollector<Object> STRING_VALUE_COLLECTOR = new ValueCollector<Object>() {
        @Override
        public void add(@Nullable Object value, ImmutableCollection.Builder<Object> dest) {
            dest.add(STRING_VALUE_SANITIZER.sanitize(value));
        }

        @Override
        public void addAll(Iterable<?> values, ImmutableCollection.Builder<Object> dest) {
            for (Object value : values) {
                add(value, dest);
            }
        }
    };

    public static <T> ValueSanitizer<T> forType(Class<? extends T> targetType) {
        if (String.class.equals(targetType)) {
            return Cast.uncheckedCast(STRING_VALUE_SANITIZER);
        } else {
            return Cast.uncheckedCast(IDENTITY_SANITIZER);
        }
    }

    public static <T> ValueCollector<T> collectorFor(Class<? extends T> elementType) {
        if (String.class.equals(elementType)) {
            return Cast.uncheckedCast(STRING_VALUE_COLLECTOR);
        } else {
            return Cast.uncheckedCast(IDENTITY_VALUE_COLLECTOR);
        }
    }
}
