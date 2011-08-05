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

public class BlockPointer implements Comparable<BlockPointer> {
    private final long pos;

    public BlockPointer() {
        pos = -1;
    }

    public BlockPointer(long pos) {
        this.pos = pos;
    }

    public boolean isNull() {
        return pos < 0;
    }

    public long getPos() {
        return pos;
    }

    @Override
    public String toString() {
        return String.valueOf(pos);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        BlockPointer other = (BlockPointer) obj;
        return pos == other.pos;
    }

    @Override
    public int hashCode() {
        return (int) pos;
    }

    public int compareTo(BlockPointer o) {
        if (pos > o.pos) {
            return 1;
        }
        if (pos < o.pos) {
            return -1;
        }
        return 0;
    }
}
