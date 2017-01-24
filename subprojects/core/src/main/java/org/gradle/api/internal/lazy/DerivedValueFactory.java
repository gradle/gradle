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

package org.gradle.api.internal.lazy;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.lazy.DerivedValue;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.util.concurrent.Callable;

public final class DerivedValueFactory {

    private final TaskResolver taskResolver;

    public DerivedValueFactory(TaskResolver taskResolver) {
        this.taskResolver = taskResolver;
    }

    public <T> DerivedValue<T> newDerivedValue(final Object value) {
        if (value == null) {
            throw new InvalidUserDataException("Value cannot be null");
        }

        if (value instanceof Boolean) {
            return Cast.uncheckedCast(new AbstractDerivedValue<Boolean>(taskResolver) {
                @Override
                public Boolean getValue() {
                    return (Boolean)value;
                }
            });
        } else if (value instanceof Byte) {
            return Cast.uncheckedCast(new AbstractDerivedValue<Byte>(taskResolver) {
                @Override
                public Byte getValue() {
                    return (Byte)value;
                }
            });
        } else if (value instanceof Short) {
            return Cast.uncheckedCast(new AbstractDerivedValue<Short>(taskResolver) {
                @Override
                public Short getValue() {
                    return (Short)value;
                }
            });
        } else if (value instanceof Integer) {
            return Cast.uncheckedCast(new AbstractDerivedValue<Integer>(taskResolver) {
                @Override
                public Integer getValue() {
                    return (Integer)value;
                }
            });
        } else if (value instanceof Long) {
            return Cast.uncheckedCast(new AbstractDerivedValue<Long>(taskResolver) {
                @Override
                public Long getValue() {
                    return (Long)value;
                }
            });
        } else if (value instanceof Float) {
            return Cast.uncheckedCast(new AbstractDerivedValue<Float>(taskResolver) {
                @Override
                public Float getValue() {
                    return (Float)value;
                }
            });
        } else if (value instanceof Double) {
            return Cast.uncheckedCast(new AbstractDerivedValue<Double>(taskResolver) {
                @Override
                public Double getValue() {
                    return (Double)value;
                }
            });
        } else if (value instanceof Character) {
            return Cast.uncheckedCast(new AbstractDerivedValue<Character>(taskResolver) {
                @Override
                public Character getValue() {
                    return (Character)value;
                }
            });
        } else if (value instanceof String) {
            return Cast.uncheckedCast(new AbstractDerivedValue<String>(taskResolver) {
                @Override
                public String getValue() {
                    return (String)value;
                }
            });
        } else if (value instanceof File) {
            return Cast.uncheckedCast(new AbstractDerivedValue<File>(taskResolver) {
                @Override
                public File getValue() {
                    return (File)value;
                }
            });
        } else if (value instanceof Callable) {
            return new AbstractDerivedValue<T>(taskResolver) {
                @Override
                public T getValue() {
                    try {
                        return (T)((Callable) value).call();
                    } catch (Exception e) {
                        throw new UncheckedException(e);
                    }
                }
            };
        }

        throw new InvalidUserDataException(String.format("Unsupported type %s for derived value", value.getClass()));
    }
}
