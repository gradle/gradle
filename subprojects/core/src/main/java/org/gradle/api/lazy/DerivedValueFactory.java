/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.lazy;

import org.gradle.internal.Cast;

public final class DerivedValueFactory {

    private DerivedValueFactory() {}

    public static <T> DerivedValue<T> newDerivedValue(final Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        if (value instanceof Boolean) {
            return Cast.uncheckedCast(new DerivedValue<Boolean>() {
                @Override
                public Boolean getValue() {
                    return (Boolean)value;
                }
            });
        } else if (value instanceof Byte) {
            return Cast.uncheckedCast(new DerivedValue<Byte>() {
                @Override
                public Byte getValue() {
                    return (Byte)value;
                }
            });
        } else if (value instanceof Short) {
            return Cast.uncheckedCast(new DerivedValue<Short>() {
                @Override
                public Short getValue() {
                    return (Short)value;
                }
            });
        } else if (value instanceof Integer) {
            return Cast.uncheckedCast(new DerivedValue<Integer>() {
                @Override
                public Integer getValue() {
                    return (Integer)value;
                }
            });
        } else if (value instanceof Long) {
            return Cast.uncheckedCast(new DerivedValue<Long>() {
                @Override
                public Long getValue() {
                    return (Long)value;
                }
            });
        } else if (value instanceof Float) {
            return Cast.uncheckedCast(new DerivedValue<Float>() {
                @Override
                public Float getValue() {
                    return (Float)value;
                }
            });
        } else if (value instanceof Double) {
            return Cast.uncheckedCast(new DerivedValue<Double>() {
                @Override
                public Double getValue() {
                    return (Double)value;
                }
            });
        } else if (value instanceof Character) {
            return Cast.uncheckedCast(new DerivedValue<Character>() {
                @Override
                public Character getValue() {
                    return (Character)value;
                }
            });
        } else if (value instanceof String) {
            return Cast.uncheckedCast(new DerivedValue<String>() {
                @Override
                public String getValue() {
                    return (String)value;
                }
            });
        }

        throw new IllegalArgumentException(String.format("Unsupported type %s for derived value", value.getClass()));
    }
}
