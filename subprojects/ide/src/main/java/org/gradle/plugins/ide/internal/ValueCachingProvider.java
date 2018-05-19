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

package org.gradle.plugins.ide.internal;

import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;

public class ValueCachingProvider<T> extends AbstractProvider<T> {
    private final Provider<T> delegate;
    private T cachedValue;

    ValueCachingProvider(Provider<T> delegate) {
        this.delegate = delegate;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return ((ProviderInternal<T>)delegate).getType();
    }

    @Nullable
    @Override
    public T getOrNull() {
        if (cachedValue == null) {
            cachedValue = delegate.getOrNull();
        }
        return cachedValue;
    }

    public static <T> Provider<T> of(Provider<T> provider) {
        return new ValueCachingProvider<T>(provider);
    }
}
