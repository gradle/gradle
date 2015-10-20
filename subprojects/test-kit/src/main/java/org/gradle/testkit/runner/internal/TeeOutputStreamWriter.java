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

package org.gradle.testkit.runner.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

public class TeeOutputStreamWriter extends OutputStream {
    private final OutputStream out;
    private final Writer tee;

    public TeeOutputStreamWriter(OutputStream out, Writer tee) {
        if (out == null) {
            throw new IllegalArgumentException("out argument cannot be null");
        }
        if (tee == null) {
            throw new IllegalArgumentException("tee argument cannot be null");
        }

        this.out = out;
        this.tee = tee;
    }

    @Override
    public synchronized void write(int b) throws IOException {
        out.write(b);
        tee.write(b);
    }

    @Override
    public synchronized void write(byte[] b) throws IOException {
        out.write(b);
        tee.write(new String(b));
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        tee.write(new String(b), off, len);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
        tee.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
        // we do not close the provided writer
        // it's going to be the end user's responsibility
    }
}
