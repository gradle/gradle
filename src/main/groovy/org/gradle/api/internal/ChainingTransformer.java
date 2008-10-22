/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.internal;

import groovy.lang.Closure;

import java.util.List;
import java.util.ArrayList;

public class ChainingTransformer<T> implements Transformer<T> {
    private final List<Transformer<T>> transformers = new ArrayList<Transformer<T>>();

    public T transform(T original) {
        T value = original;
        for (Transformer<T> transformer : transformers) {
            value = transformer.transform(value);
        }
        return value;
    }

    public void add(Transformer<T> transformer) {
        transformers.add(transformer);
    }

    public void add(final Closure transformer) {
        transformers.add(new Transformer<T>() {
            public T transform(T original) {
                return (T) transformer.call(original);
            }
        });
    }
}
