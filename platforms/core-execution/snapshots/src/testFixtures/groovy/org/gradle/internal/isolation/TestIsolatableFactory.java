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

package org.gradle.internal.isolation;

import org.gradle.internal.hash.Hasher;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nullable;

public class TestIsolatableFactory implements IsolatableFactory {
    @Override
    public <T> Isolatable<T> isolate(T value) {
        return new Isolatable<T>() {
            @Override
            public T isolate() {
                return value;
            }

            @Override
            public ValueSnapshot asSnapshot() {
                throw new UnsupportedOperationException();
            }

            @Nullable
            @Override
            public <S> S coerce(Class<S> type) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void appendToHasher(Hasher hasher) {
                hasher.putString(String.valueOf(value));
            }
        };
    }
}
