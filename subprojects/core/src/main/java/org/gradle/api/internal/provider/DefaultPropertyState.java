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

import com.google.common.base.Preconditions;
import org.gradle.api.provider.PropertyState;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import java.util.concurrent.Callable;

import static org.gradle.api.internal.provider.AbstractProvider.NON_NULL_VALUE_EXCEPTION_MESSAGE;

public class DefaultPropertyState<T> implements PropertyState<T> {
    private static final Provider<Object> NULL_PROVIDER = new Provider<Object>() {
        @Override
        public Object get() {
            throw new IllegalStateException(NON_NULL_VALUE_EXCEPTION_MESSAGE);
        }

        @Override
        public Object getOrNull() {
            return null;
        }

        @Override
        public boolean isPresent() {
            return false;
        }
    };

    private Provider<? extends T> provider = Cast.uncheckedCast(NULL_PROVIDER);

    @Override
    public void set(final T value) {
        this.provider = new DefaultProvider<T>(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return value;
            }
        });
    }

    @Override
    public void set(Provider<? extends T> provider) {
        this.provider = Preconditions.checkNotNull(provider);
    }

    @Override
    public T get() {
        return provider.get();
    }

    @Override
    public T getOrNull() {
        return provider.getOrNull();
    }

    @Override
    public boolean isPresent() {
        return provider.isPresent();
    }

    @Override
    public String toString() {
        return String.format("value: %s", getOrNull());
    }
}
