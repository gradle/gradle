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

package org.gradle.internal.snapshot.impl;

import org.gradle.api.internal.provider.Providers;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nullable;

class IsolatedProvider implements Isolatable<Object> {
    protected final Isolatable<?> value;

    public IsolatedProvider(Isolatable<?> value) {
        this.value = value;
    }

    @Override
    public ValueSnapshot asSnapshot() {
        return value.asSnapshot();
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        return null;
    }

    @Nullable
    @Override
    public Object isolate() {
        return Providers.of(value.isolate());
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        value.appendToHasher(hasher);
    }
}
