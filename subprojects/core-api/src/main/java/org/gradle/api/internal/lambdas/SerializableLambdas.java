/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.lambdas;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Factory;

import java.io.Serializable;
import java.util.concurrent.Callable;

/**
 * Provides a mechanism for creating Java lambdas that can be stored to the configuration cache.
 *
 * The methods on this class are meant to be statically imported.
 */
public class SerializableLambdas {

    public static <T> Spec<T> spec(SerializableSpec<T> spec) {
        return spec;
    }

    public static <T> Action<T> action(SerializableAction<T> action) {
        return action;
    }

    public static <T> Factory<T> factory(SerializableFactory<T> factory) {
        return factory;
    }

    public static <OUT, IN> Transformer<OUT, IN> transformer(SerializableTransformer<OUT, IN> transformer) {
        return transformer;
    }

    public static <T> Callable<T> callable(SerializableCallable<T> callable) {
        return callable;
    }

    /**
     * A {@link Serializable} version of {@link Spec}.
     */
    public interface SerializableSpec<T> extends Spec<T>, Serializable {
    }

    /**
     * A {@link Serializable} version of {@link Action}.
     */
    public interface SerializableAction<T> extends Action<T>, Serializable {
    }

    /**
     * A {@link Serializable} version of {@link Factory}.
     */
    public interface SerializableFactory<T> extends Factory<T>, Serializable {
    }

    /**
     * A {@link Serializable} version of {@link org.gradle.api.Transformer}.
     */
    public interface SerializableTransformer<OUT, IN> extends Transformer<OUT, IN>, Serializable {
    }

    /**
     * A {@link Serializable} version of {@link Callable}.
     */
    public interface SerializableCallable<T> extends Callable<T>, Serializable {
    }

    private SerializableLambdas() {
    }
}
