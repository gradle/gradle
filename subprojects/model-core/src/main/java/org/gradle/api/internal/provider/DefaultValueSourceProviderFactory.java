/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.provider.ValueSourceSpec;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;

public class DefaultValueSourceProviderFactory implements ValueSourceProviderFactory {

    private final ObjectFactory objectFactory;
    private final InstantiatorFactory instantiatorFactory;
    private final IsolatableFactory isolatableFactory;

    public DefaultValueSourceProviderFactory(ObjectFactory objectFactory, InstantiatorFactory instantiatorFactory, IsolatableFactory isolatableFactory) {
        this.objectFactory = objectFactory;
        this.instantiatorFactory = instantiatorFactory;
        this.isolatableFactory = isolatableFactory;
    }

    @Override
    public <T, P extends ValueSourceParameters> Provider<T> createProviderOf(Class<? extends ValueSource<T, P>> valueSourceType, Action<? super ValueSourceSpec<P>> configuration) {
        throw new UnsupportedOperationException();
    }
}
