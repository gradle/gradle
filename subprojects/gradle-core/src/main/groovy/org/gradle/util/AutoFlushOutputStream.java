/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An OutputStream which writes to another OutputStream and which does a flush after each write.
 */
public class AutoFlushOutputStream extends OutputStream {
    private final OutputStream outputStream;

    public AutoFlushOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void write(int i) throws IOException {
        outputStream.write(i);
        flush();
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        outputStream.write(bytes);
        flush();
    }

    @Override
    public void write(byte[] bytes, int offset, int count) throws IOException {
        outputStream.write(bytes, offset, count);
        flush();
    }

    @Override
    public void flush() throws IOException {
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }
}
