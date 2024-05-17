/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.cache.internal.streams;

/**
 * An opaque (outside this package) pointer to a block in a file.
 */
public class BlockAddress {
    final int fileId;
    final long pos;
    final long length;

    public BlockAddress(int fileId, long pos, long length) {
        this.fileId = fileId;
        this.pos = pos;
        this.length = length;
    }

    @Override
    public String toString() {
        return "block(file=" + fileId + ", pos=" + pos + ", length=" + length + ")";
    }
}
