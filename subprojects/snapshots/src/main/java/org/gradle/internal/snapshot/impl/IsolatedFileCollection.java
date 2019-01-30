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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

public class IsolatedFileCollection implements Isolatable<ConfigurableFileCollection> {
    private final Set<File> files;

    public IsolatedFileCollection(ConfigurableFileCollection files) {
        this.files = files.getFiles();
    }

    @Override
    public ValueSnapshot asSnapshot() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        hasher.putString("files");
        for (File file : files) {
            hasher.putString(file.getAbsolutePath());
        }
    }

    @Nullable
    @Override
    public ConfigurableFileCollection isolate() {
        return new DefaultConfigurableFileCollection(new IdentityFileResolver(), null, files);
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        if (type.isAssignableFrom(ConfigurableFileCollection.class)) {
            return type.cast(isolate());
        }
        return null;
    }
}
