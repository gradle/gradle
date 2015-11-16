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

public class TeeOutputStream extends OutputStream {

    private final OutputStream out1;
    private final OutputStream out2;

    public TeeOutputStream(OutputStream out1, OutputStream out2) {
        if (out1 == null) {
            throw new IllegalArgumentException("out1 argument cannot be null");
        }
        if (out2 == null) {
            throw new IllegalArgumentException("out2 argument cannot be null");
        }

        this.out1 = out1;
        this.out2 = out2;
    }

    @Override
    public synchronized void write(int b) throws IOException {
        out1.write(b);
        out1.write(b);
    }

    @Override
    public synchronized void write(byte[] b) throws IOException {
        out1.write(b);
        out2.write(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        out1.write(b, off, len);
        out2.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        out1.flush();
        out2.flush();
    }

    @Override
    public void close() throws IOException {
        out1.close();
        out2.close();
    }
}
