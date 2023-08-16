/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath.intercept;

import org.gradle.api.NonNullApi;

import javax.annotation.Nullable;

/**
 * A wrapper for {@code Class} instances that knows about Groovy type coercions.
 *
 * @param <T> the target type
 */
@NonNullApi
public class TargetType<T> {
    private final Class<T> cls;

    private TargetType(Class<T> cls) {
        this.cls = cls;
    }

    /**
     * Checks if the given argument is assignment-compatible with this type, taking Groovy coercions into account.
     *
     * @param argument the argument, can be {@code null}
     * @return {@code true} if the argument can be assigned to a variable of this type
     */
    public boolean isAssignableFrom(@Nullable Object argument) {
        return argument == null || cls.isInstance(argument);
    }

    /**
     * Checks if the given argument class is assignment-compatible with this type, taking Groovy coercions into account.
     *
     * @param argumentClass the class of the argument
     * @return {@code true} if the argument of the given class can be assigned to a variable of this type
     */
    public boolean isAssignableFromType(Class<?> argumentClass) {
        return cls.isAssignableFrom(argumentClass);
    }

    /**
     * Casts the argument to this type, applying type coercions if necessary.
     *
     * @param argument the argument to case, can be {@code null}
     * @return the value of this type
     * @throws ClassCastException is the argument cannot be converted to this class
     */
    @Nullable
    public T cast(@Nullable Object argument) {
        return cls.cast(argument);
    }

    /**
     * Creates an instance of {@link TargetType} representing the given class.
     *
     * @param cls the target class
     * @param <T> the target type
     * @return the TargetType representing the class
     */
    public static <T> TargetType<T> of(Class<T> cls) {
        return new TargetType<>(cls);
    }
}
