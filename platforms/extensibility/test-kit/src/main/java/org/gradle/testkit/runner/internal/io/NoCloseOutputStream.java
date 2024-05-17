/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.internal.io;

import java.io.IOException;
import java.io.OutputStream;

public class NoCloseOutputStream extends OutputStream {

    private final OutputStream delegate;

    public NoCloseOutputStream(OutputStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized void write(int b) throws IOException {
        delegate.write(b);
    }

    @Override
    public synchronized void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
    }

    @Override
    public synchronized void write(byte[] b) throws IOException {
        delegate.write(b);
    }

    @Override
    public synchronized void close() throws IOException {
        // don't forward
    }
}
