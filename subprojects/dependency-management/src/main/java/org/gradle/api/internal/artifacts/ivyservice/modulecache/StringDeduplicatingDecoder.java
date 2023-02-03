/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import com.google.common.collect.Interner;
import org.gradle.internal.serialize.Decoder;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

class StringDeduplicatingDecoder implements Decoder, Closeable {
    private final Decoder delegate;
    private final Interner<String> stringInterner;

    StringDeduplicatingDecoder(Decoder delegate, Interner<String> stringInterner) {
        this.delegate = delegate;
        this.stringInterner = stringInterner;
    }

    @Override
    public InputStream getInputStream() {
        return delegate.getInputStream();
    }

    @Override
    public long readLong() throws EOFException, IOException {
        return delegate.readLong();
    }

    @Override
    public long readSmallLong() throws EOFException, IOException {
        return delegate.readSmallLong();
    }

    @Override
    public int readInt() throws EOFException, IOException {
        return delegate.readInt();
    }

    @Override
    public int readSmallInt() throws EOFException, IOException {
        return delegate.readSmallInt();
    }

    @Nullable
    @Override
    public Integer readNullableSmallInt() throws IOException {
        return delegate.readNullableSmallInt();
    }

    @Override
    public boolean readBoolean() throws EOFException, IOException {
        return delegate.readBoolean();
    }

    @Override
    public String readString() throws EOFException, IOException {
        return stringInterner.intern(delegate.readString());
    }

    @Override
    @Nullable
    public String readNullableString() throws EOFException, IOException {
        String str = delegate.readNullableString();
        if (str != null) {
            str = stringInterner.intern(str);
        }
        return str;
    }

    @Override
    public byte readByte() throws EOFException, IOException {
        return delegate.readByte();
    }

    @Override
    public void readBytes(byte[] buffer) throws EOFException, IOException {
        delegate.readBytes(buffer);
    }

    @Override
    public void readBytes(byte[] buffer, int offset, int count) throws EOFException, IOException {
        delegate.readBytes(buffer, offset, count);
    }

    @Override
    public byte[] readBinary() throws EOFException, IOException {
        return delegate.readBinary();
    }

    @Override
    public void skipBytes(long count) throws EOFException, IOException {
        delegate.skipBytes(count);
    }

    @Override
    public <T> T decodeChunked(DecodeAction<Decoder, T> decodeAction) throws EOFException, Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void skipChunked() throws EOFException, IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        ((Closeable)delegate).close();
    }
}
