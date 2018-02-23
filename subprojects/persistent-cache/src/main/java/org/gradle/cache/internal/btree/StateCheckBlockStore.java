/*
 * Copyright 2009 the original author or authors.
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

public class StateCheckBlockStore implements BlockStore {
    private final BlockStore blockStore;
    private boolean open;

    public StateCheckBlockStore(BlockStore blockStore) {
        this.blockStore = blockStore;
    }

    public void open(Runnable initAction, Factory factory) {
        assert !open;
        open = true;
        blockStore.open(initAction, factory);
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        if (!open) {
            return;
        }
        open = false;
        blockStore.close();
    }

    public void clear() {
        assert open;
        blockStore.clear();
    }

    public void remove(BlockPayload block) {
        assert open;
        blockStore.remove(block);
    }

    public <T extends BlockPayload> T readFirst(Class<T> payloadType) {
        assert open;
        return blockStore.readFirst(payloadType);
    }

    public <T extends BlockPayload> T read(BlockPointer pos, Class<T> payloadType) {
        assert open;
        return blockStore.read(pos, payloadType);
    }

    public void write(BlockPayload block) {
        assert open;
        blockStore.write(block);
    }

    public void attach(BlockPayload block) {
        assert open;
        blockStore.attach(block);
    }

    public void flush() {
        assert open;
        blockStore.flush();
    }
}
