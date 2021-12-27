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

import org.gradle.internal.UncheckedException;

import java.util.concurrent.Callable;

public class DefaultProvider<T> extends AbstractMinimalProvider<T> implements TypeInferringProvider<T> {
    private final Callable<? extends T> value;

    public DefaultProvider(Callable<? extends T> value) {
        this.value = value;
    }

    @Override
    public Callable<? extends T> getCallable() {
        return value;
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try {
            return Value.ofNullable(value.call());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
