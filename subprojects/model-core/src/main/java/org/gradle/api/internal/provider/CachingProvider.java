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

package org.gradle.api.internal.provider;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.lazy.Lazy;

import java.util.concurrent.Callable;

public class CachingProvider<T> extends AbstractMinimalProvider<T> implements TypeInferringProvider<T> {
    private final Callable<? extends T> callable;
    private final Lazy<? extends T> lazyValue;

    public CachingProvider(Callable<? extends T> callable) {
        this.callable = callable;
        this.lazyValue = Lazy.locking().of(() -> {
            try {
                return callable.call();
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        });
    }

    @Override
    public Callable<? extends T> getCallable() {
        return callable;
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        return Value.ofNullable(lazyValue.get());
    }
}
