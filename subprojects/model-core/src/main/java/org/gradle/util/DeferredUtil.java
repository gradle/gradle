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

package org.gradle.util;

import org.gradle.api.provider.Provider;
import org.gradle.internal.Factory;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

import static org.gradle.util.GUtil.uncheckedCall;

public class DeferredUtil {

    /**
     * Successively unpacks a deferred value until it is resolved to null or something other than Callable (including Groovy Closure) or Kotlin lambda
     * then unpacks the remaining Provider or Factory.
     */
    @Nullable
    public static Object unpack(@Nullable Object deferred) {
        if (deferred == null) {
            return null;
        }
        Object value = unpackNestableDeferred(deferred);
        if (value instanceof Provider) {
            return ((Provider<?>) value).get();
        }
        if (value instanceof Factory) {
            return ((Factory<?>) value).create();
        }
        return value;
    }

    public static boolean isDeferred(Object value) {
        return value instanceof Provider
            || value instanceof Factory
            || isNestableDeferred(value);
    }

    public static boolean isNestableDeferred(@Nullable Object value) {
        return value instanceof Callable
            || isKotlinFunction0Deferrable(value);
    }

    @Nullable
    public static Object unpackNestableDeferred(Object deferred) {
        Object current = deferred;
        while (isNestableDeferred(current)) {
            if (current instanceof Callable) {
                current = uncheckedCall((Callable<?>) current);
            } else {
                current = unpackKotlinFunction0(current);
            }
        }
        return current;
    }

    private static boolean isKotlinFunction0Deferrable(@Nullable Object value) {
        return value instanceof kotlin.jvm.functions.Function0;
    }

    @Nullable
    private static Object unpackKotlinFunction0(Object value) {
        return ((kotlin.jvm.functions.Function0) value).invoke();
    }
}
