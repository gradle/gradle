/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.local.internal.mvstore;

import org.gradle.internal.hash.HashCode;
import org.h2.mvstore.DataUtils;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.BasicDataType;

import java.nio.ByteBuffer;

class ComparableByteArrayType extends BasicDataType<byte[]> {

    static final ComparableByteArrayType INSTANCE = new ComparableByteArrayType();

    @Override
    public int compare(byte[] a, byte[] b) {
        return HashCode.compareBytes(a, b);
    }

    @Override
    public int getMemory(byte[] data) {
        return data.length;
    }

    @Override
    public void write(WriteBuffer buff, byte[] data) {
        buff.putVarInt(data.length);
        buff.put(data);
    }

    @Override
    public byte[] read(ByteBuffer buff) {
        int size = DataUtils.readVarInt(buff);
        byte[] data = new byte[size];
        buff.get(data);
        return data;
    }

    @Override
    public byte[][] createStorage(int size) {
        return new byte[size][];
    }
}
