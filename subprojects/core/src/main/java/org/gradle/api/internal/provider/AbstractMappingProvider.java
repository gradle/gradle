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

import org.gradle.api.provider.Provider;

public abstract class AbstractMappingProvider<OUT, IN> extends AbstractProvider<OUT> {
    private final Provider<? extends IN> provider;

    public AbstractMappingProvider(Provider<? extends IN> provider) {
        this.provider = provider;
    }

    @Override
    public boolean isPresent() {
        return provider.isPresent();
    }

    @Override
    public OUT getOrNull() {
        if (provider.isPresent()) {
            return map(provider.get());
        }
        return null;
    }

    protected abstract OUT map(IN v);
}
