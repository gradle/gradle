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

public class UUIDFileStore implements FileStore<String> {

    private final PathKeyFileStore delegate;
    private final Factory<UUID> uuidFactory;

    public UUIDFileStore(PathKeyFileStore delegate) {
        this(delegate, new Factory<UUID>() {
            public UUID create() {
                return UUID.randomUUID();
            }
        });
    }

    UUIDFileStore(PathKeyFileStore delegate, Factory<UUID> uuidFactory) {
        this.delegate = delegate;
        this.uuidFactory = uuidFactory;
    }

    public FileStoreEntry move(String dir, File source) {
        return delegate.move(getPath(dir), source);
    }

    public FileStoreEntry copy(String dir, File source) {
        return delegate.copy(getPath(dir), source);
    }

    private String getPath(String dir) {
        return String.format("%s/%s", dir, uuidFactory.create());
    }

    public File getTempFile() {
        return delegate.getTempFile();
    }

    public void moveFilestore(File destination) {
        delegate.moveFilestore(destination);
    }
}
