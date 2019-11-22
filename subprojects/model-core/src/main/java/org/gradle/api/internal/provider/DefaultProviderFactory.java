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

import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.provider.ValueSourceSpec;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

public class DefaultProviderFactory implements ProviderFactory {

    private final ProvidersListener broadcaster;
    @Nullable
    private final ValueSourceProviderFactory valueSourceProviderFactory;

    public DefaultProviderFactory(ProvidersListener broadcaster, @Nullable ValueSourceProviderFactory valueSourceProviderFactory) {
        this.broadcaster = broadcaster;
        this.valueSourceProviderFactory = valueSourceProviderFactory;
    }

    @Override
    public <T> Provider<T> provider(final Callable<? extends T> value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        return new DefaultProvider<T>(value);
    }

    @Override
    public Provider<String> systemProperty(String propertyName) {
        return new SystemPropertyProvider(propertyName, broadcaster);
    }

    @Override
    public <T, P extends ValueSourceParameters> Provider<T> of(Class<? extends ValueSource<T, P>> valueSourceType, Action<? super ValueSourceSpec<P>> configuration) {
        if (valueSourceProviderFactory == null) {
            throw new UnsupportedOperationException();
        }
        return valueSourceProviderFactory.createProviderOf(valueSourceType, configuration);
    }
}
