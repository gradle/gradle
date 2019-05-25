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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.hash.Hasher;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

import javax.annotation.Nullable;

public class NullValueSnapshot implements ValueSnapshot, Isolatable<Object> {
    public static final NullValueSnapshot INSTANCE = new NullValueSnapshot();

    private NullValueSnapshot() {
    }

    @Override
    public ValueSnapshot snapshot(@Nullable Object value, ValueSnapshotter snapshotter) {
        if (value == null) {
            return this;
        }
        return snapshotter.snapshot(value);
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        hasher.putNull();
    }

    @Override
    public ValueSnapshot asSnapshot() {
        return this;
    }

    @Override
    public Object isolate() {
        return null;
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        return null;
    }
}
