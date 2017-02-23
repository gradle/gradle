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

package org.gradle.api.internal.provider;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;

import java.util.Collections;
import java.util.concurrent.Callable;

public class ProviderFactory {

    private final FileOperations fileOperations;
    private final TaskResolver taskResolver;

    public ProviderFactory(FileOperations fileOperations, TaskResolver taskResolver) {
        this.fileOperations = fileOperations;
        this.taskResolver = taskResolver;
    }

    public <T> Provider<T> defaultProvider(Class<T> clazz) {
        if (clazz == null) {
            throw new InvalidUserDataException("Class cannot be null");
        }

        if (clazz == Boolean.class) {
            return Cast.uncheckedCast(new AbstractProvider<Boolean>(taskResolver) {
                @Override
                public Boolean get() {
                    return Boolean.FALSE;
                }
            });
        } else if (clazz == Byte.class) {
            return Cast.uncheckedCast(new AbstractProvider<Byte>(taskResolver) {
                @Override
                public Byte get() {
                    return 0;
                }
            });
        } else if (clazz == Short.class) {
            return Cast.uncheckedCast(new AbstractProvider<Short>(taskResolver) {
                @Override
                public Short get() {
                    return 0;
                }
            });
        } else if (clazz == Integer.class) {
            return Cast.uncheckedCast(new AbstractProvider<Integer>(taskResolver) {
                @Override
                public Integer get() {
                    return 0;
                }
            });
        } else if (clazz == Long.class) {
            return Cast.uncheckedCast(new AbstractProvider<Long>(taskResolver) {
                @Override
                public Long get() {
                    return 0L;
                }
            });
        } else if (clazz == Float.class) {
            return Cast.uncheckedCast(new AbstractProvider<Float>(taskResolver) {
                @Override
                public Float get() {
                    return 0.0f;
                }
            });
        } else if (clazz == Double.class) {
            return Cast.uncheckedCast(new AbstractProvider<Double>(taskResolver) {
                @Override
                public Double get() {
                    return 0.0d;
                }
            });
        } else if (clazz == Character.class) {
            return Cast.uncheckedCast(new AbstractProvider<Character>(taskResolver) {
                @Override
                public Character get() {
                    return '\u0000';
                }
            });
        } else if (clazz == FileCollection.class || clazz == ConfigurableFileCollection.class) {
            return Cast.uncheckedCast(new AbstractProvider<ConfigurableFileCollection>(taskResolver) {
                @Override
                public ConfigurableFileCollection get() {
                    return fileOperations.files();
                }
            });
        } else if (clazz == FileTree.class | clazz == ConfigurableFileTree.class) {
            return Cast.uncheckedCast(new AbstractProvider<ConfigurableFileTree>(taskResolver) {
                @Override
                public ConfigurableFileTree get() {
                    return fileOperations.fileTree(Collections.emptyMap());
                }
            });
        } else {
            return new AbstractProvider<T>(taskResolver) {
                @Override
                public T get() {
                    return null;
                }
            };
        }
    }

    public <T> Provider<T> lazilyEvaluatedProvider(final Callable<T> value) {
        if (value == null) {
            throw new InvalidUserDataException("Value cannot be null");
        }

        return new AbstractProvider<T>(taskResolver) {
            @Override
            public T get() {
                try {
                    return (T)((Callable) value).call();
                } catch (Exception e) {
                    throw new UncheckedException(e);
                }
            }
        };
    }

    public <T> Provider<T> eagerlyEvaluatedProvider(final T value) {
        if (value == null) {
            throw new InvalidUserDataException("Value cannot be null");
        }

        return new AbstractProvider<T>(taskResolver) {
            @Override
            public T get() {
                return value;
            }
        };
    }
}
