/*
 * Copyright 2021 the original author or authors.
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

import java.util.function.BiFunction;
import java.util.function.BinaryOperator;

/**
 * Utility class to help with using {@link java.util.stream.Stream#reduce(Object, BiFunction, BinaryOperator)}.
 * Promote to a more shared subproject if useful.
 */
public abstract class Combiners {

    /**
     * We know the stream we are processing is handled sequentially, and hence there is no need for a combiner.
     */
    public static <T> BinaryOperator<T> nonCombining() {
        return Combiners::unsupportedCombineOperation;
    }

    private static <T> T unsupportedCombineOperation(T a, T b) {
        throw new IllegalStateException("Not a combinable operation");
    }
}
