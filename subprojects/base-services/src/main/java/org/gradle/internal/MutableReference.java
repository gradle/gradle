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

package org.gradle.internal;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.io.Serializable;

/**
 * A non-thread-safe type to hold a reference to a single value.
 */
@NotThreadSafe
public final class MutableReference<T> implements Serializable {
    private T value;

    public static <T> MutableReference<T> empty() {
        return of(null);
    }

    public static <T> MutableReference<T> of(@Nullable T initialValue) {
        return new MutableReference<T>(initialValue);
    }

    private MutableReference(@Nullable T initialValue) {
        this.value = initialValue;
    }

    public void set(@Nullable T value) {
        this.value = value;
    }

    @Nullable
    public T get() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MutableReference<?> that = (MutableReference<?>) o;
        return value != null ? value.equals(that.value) : that.value == null;
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
