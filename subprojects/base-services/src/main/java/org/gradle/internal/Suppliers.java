/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Transformer;

import java.io.Closeable;

public abstract class Suppliers {

    public static <I extends Closeable> Supplier<I> ofQuietlyClosed(final Factory<I> factory) {
        return new Supplier<I>() {
            public <R> R supplyTo(Transformer<R, ? super I> transformer) {
                I thing = factory.create();
                try {
                    return transformer.transform(thing);
                } finally {
                    try {
                        thing.close();
                    } catch (Exception ignore) {
                        // ignore
                    }
                }
            }
        };
    }

    public static <I> Supplier<I> of(final Factory<? extends I> factory) {
        return new Supplier<I>() {
            public <R> R supplyTo(Transformer<R, ? super I> transformer) {
                I value = factory.create();
                return transformer.transform(value);
            }
        };
    }

    public static <I, T> Supplier<T> wrap(final Supplier<? extends I> supplier, final Transformer<T, ? super I> suppliedTransformer) {
        return new Supplier<T>() {
            public <R> R supplyTo(final Transformer<R, ? super T> transformer) {
                return supplier.supplyTo(new Transformer<R, I>() {
                    public R transform(I original) {
                        T transformedSupplied = suppliedTransformer.transform(original);
                        return transformer.transform(transformedSupplied);
                    }
                });
            }
        };
    }

}
