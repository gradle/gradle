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
import org.gradle.api.provider.WestlineProvider;
import org.gradle.api.provider.WestlineProviderParameters;
import org.gradle.api.provider.WestlineProviderSpec;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

public class DefaultProviderFactory implements ProviderFactory {

    private final ProvidersListener broadcaster;
    @Nullable
    private final WestlineProviderFactory westlineProviderFactory;

    public DefaultProviderFactory(ProvidersListener broadcaster, @Nullable WestlineProviderFactory westlineProviderFactory) {
        this.broadcaster = broadcaster;
        this.westlineProviderFactory = westlineProviderFactory;
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
    public <T, P extends WestlineProviderParameters> Provider<T> westline(Class<? extends WestlineProvider<T, P>> providerType, Action<? super WestlineProviderSpec<P>> configuration) {
        if (westlineProviderFactory == null) {
            throw new UnsupportedOperationException();
        }
        return westlineProviderFactory.createProviderOf(providerType, configuration);
    }
}
