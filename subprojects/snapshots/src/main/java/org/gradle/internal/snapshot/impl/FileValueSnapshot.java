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
import java.io.File;

public class FileValueSnapshot extends AbstractScalarValueSnapshot<String> implements Isolatable<File> {
    public FileValueSnapshot(File value) {
        super(value.getPath());
    }

    public FileValueSnapshot(String value) {
        super(value);
    }

    @Override
    public ValueSnapshot asSnapshot() {
        return this;
    }

    @Override
    public File isolate() {
        return new File(getValue());
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        if (type.isAssignableFrom(File.class)) {
            return type.cast(isolate());
        }
        return null;
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        if (value instanceof File) {
            File file = (File) value;
            if (file.getPath().equals(getValue())) {
                return this;
            }
        }
        return snapshotter.snapshot(value);
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        hasher.putString(getValue());
    }
}
