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

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

public abstract class Block {
    static final int LONG_SIZE = 8;
    static final int INT_SIZE = 4;
    static final int SHORT_SIZE = 2;

    private @Nullable BlockPayload payload;

    @SuppressWarnings("this-escape")
    protected Block(BlockPayload payload) {
        this.payload = payload;
        payload.setBlock(this);
    }

    public BlockPayload getPayload() {
        //TODO IllegalStateException might be the better option here. In the interest of not changing the behavior, we keep this
        return requireNonNull(payload, "Block has been detached");
    }

    protected void detach() {
        getPayload().clearBlock();
        payload = null;
    }

    public abstract BlockPointer getPos();

    public abstract int getSize();

    public abstract RuntimeException blockCorruptedException();

    @Override
    public String toString() {
        BlockPayload payload = this.payload;
        return (payload == null ? "detached" : payload.getClass().getSimpleName()) + " " + getPos();
    }

    public BlockPointer getNextPos() {
        return BlockPointer.pos(getPos().getPos() + getSize());
    }

    public abstract boolean hasPos();

    public abstract void setPos(BlockPointer pos);

    public abstract void setSize(int size);
}
