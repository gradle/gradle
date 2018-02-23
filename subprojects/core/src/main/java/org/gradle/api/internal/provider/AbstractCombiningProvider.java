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

import javax.annotation.Nullable;

public abstract class AbstractCombiningProvider<OUT, BASE, IN> extends AbstractProvider<OUT> {
    private final Class<OUT> type;
    private final Provider<? extends BASE> base;
    private final Provider<? extends IN> provider;

    public AbstractCombiningProvider(Class<OUT> type, Provider<? extends BASE> base, Provider<? extends IN> provider) {
        this.type = type;
        this.base = base;
        this.provider = provider;
    }

    @Nullable
    @Override
    public Class<OUT> getType() {
        return type;
    }

    @Override
    public boolean isPresent() {
        return base.isPresent() && provider.isPresent();
    }

    @Override
    public OUT getOrNull() {
        if (base.isPresent() && provider.isPresent()) {
            return map(base.get(), provider.get());
        }
        return null;
    }

    protected abstract OUT map(BASE b, IN v);
}
