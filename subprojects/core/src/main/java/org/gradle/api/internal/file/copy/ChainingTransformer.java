/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import groovy.lang.Closure;
import groovy.lang.GString;
import org.gradle.api.Transformer;

import java.util.ArrayList;
import java.util.List;

public class ChainingTransformer<T> implements Transformer<T, T> {
    private final List<Transformer<T, T>> transformers = new ArrayList<Transformer<T, T>>();
    private final Class<T> type;

    public ChainingTransformer(Class<T> type) {
        this.type = type;
    }

    @Override
    public T transform(T original) {
        T value = original;
        for (Transformer<T, T> transformer : transformers) {
            value = type.cast(transformer.transform(value));
        }
        return value;
    }

    public void add(Transformer<T, T> transformer) {
        transformers.add(transformer);
    }

    public void add(final Closure transformer) {
        transformers.add(new Transformer<T, T>() {
            @Override
            public T transform(T original) {
                transformer.setDelegate(original);
                transformer.setResolveStrategy(Closure.DELEGATE_FIRST);
                Object value = transformer.call(original);
                if (type.isInstance(value)) {
                    return type.cast(value);
                }
                if (type == String.class && value instanceof GString) {
                    return type.cast(value.toString());
                }
                return original;
            }
        });
    }

    public boolean hasTransformers() {
        return !transformers.isEmpty();
    }
}
