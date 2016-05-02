/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.cache.internal.btree;

import org.gradle.cache.internal.FileAccess;

import java.util.concurrent.Callable;

public class LockingBlockStore implements BlockStore {
    private final BlockStore store;
    private final FileAccess fileAccess;

    public LockingBlockStore(BlockStore store, FileAccess fileAccess) {
        this.store = store;
        this.fileAccess = fileAccess;
    }

    public void open(final Runnable initAction, final BlockStore.Factory factory) {
        store.open(new Runnable() {
            public void run() {
                fileAccess.updateFile(initAction);
            }
        }, factory);
    }

    public void close() {
        store.close();
    }

    public void flush() {
        fileAccess.updateFile(new Runnable() {
            public void run() {
                store.flush();
            }
        });
    }

    public void clear() {
        fileAccess.updateFile(new Runnable() {
            public void run() {
                store.clear();
            }
        });
    }

    public void attach(BlockPayload block) {
        store.attach(block);
    }

    public <T extends BlockPayload> T read(final BlockPointer pos, final Class<T> payloadType) {
        return fileAccess.readFile(new Callable<T>() {
            public T call() throws Exception {
                return store.read(pos, payloadType);
            }
        });
    }

    public <T extends BlockPayload> T readFirst(final Class<T> payloadType) {
        return fileAccess.readFile(new Callable<T>() {
            public T call() throws Exception {
                return store.readFirst(payloadType);
            }
        });
    }

    public void write(final BlockPayload block) {
        fileAccess.updateFile(new Runnable() {
            public void run() {
                store.write(block);
            }
        });
    }

    public void remove(final BlockPayload block) {
        fileAccess.updateFile(new Runnable() {
            public void run() {
                store.remove(block);
            }
        });
    }
}
