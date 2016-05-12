/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.file;

import com.google.common.base.Charsets;
import com.google.common.hash.Hasher;
import org.gradle.internal.UncheckedException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class StringHasher {
    private final Writer writer;

    public StringHasher(final Hasher hasher) {
        this.writer = new OutputStreamWriter(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                hasher.putByte((byte) b);
            }

            @Override
            public void write(byte[] bytes, int off, int len) throws IOException {
                hasher.putBytes(bytes, off, len);
            }
        }, Charsets.UTF_8);
    }

    public void hashString(String string) {
        try {
            writer.write(string);
            writer.flush();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
