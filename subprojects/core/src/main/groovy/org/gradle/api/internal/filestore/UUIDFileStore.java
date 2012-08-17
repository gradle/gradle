/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.filestore;

import org.gradle.internal.Factory;

import java.io.File;
import java.util.UUID;

public class UUIDFileStore implements FileStore<UUID> {

    private final UniquePathFileStore delegate;
    private final Factory<UUID> uuidFactory;

    public UUIDFileStore(UniquePathFileStore delegate) {
        this(delegate, new Factory<UUID>() {
            public UUID create() {
                return UUID.randomUUID();
            }
        });
    }

    UUIDFileStore(UniquePathFileStore delegate, Factory<UUID> uuidFactory) {
        this.delegate = delegate;
        this.uuidFactory = uuidFactory;
    }

    public FileStoreEntry add(File contentFile) {
        return add(uuidFactory.create(), contentFile);
    }

    public FileStoreEntry add(UUID key, File contentFile) {
        return delegate.add(key.toString(), contentFile);
    }

    public File getTempFile() {
        return delegate.getTempFile();
    }
}
