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

    public static <T> DerivedValue<T> newDerivedValue(Class<T> clazz) {
        if (clazz == Boolean.class) {
            return Cast.uncheckedCast(new DerivedValue<Boolean>() {
                @Override
                public Boolean getValue() {
                    return Boolean.FALSE;
                }
            });
        } else if (clazz == Byte.class) {
            return Cast.uncheckedCast(new DerivedValue<Byte>() {
                @Override
                public Byte getValue() {
                    return Byte.valueOf((byte) 0);
                }
            });
        } else if (clazz == Short.class) {
            return Cast.uncheckedCast(new DerivedValue<Short>() {
                @Override
                public Short getValue() {
                    return Short.valueOf((short) 0);
                }
            });
        } else if (clazz == Integer.class) {
            return Cast.uncheckedCast(new DerivedValue<Integer>() {
                @Override
                public Integer getValue() {
                    return Integer.valueOf(0);
                }
            });
        } else if (clazz == Long.class) {
            return Cast.uncheckedCast(new DerivedValue<Long>() {
                @Override
                public Long getValue() {
                    return Long.valueOf(0);
                }
            });
        } else if (clazz == Float.class) {
            return Cast.uncheckedCast(new DerivedValue<Float>() {
                @Override
                public Float getValue() {
                    return Float.valueOf(0);
                }
            });
        } else if (clazz == Double.class) {
            return Cast.uncheckedCast(new DerivedValue<Double>() {
                @Override
                public Double getValue() {
                    return Double.valueOf(0);
                }
            });
        } else {
            return new DerivedValue<T>() {
                @Override
                public T getValue() {
                    return null;
                }
            };
        }
    }
}
