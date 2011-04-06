/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.logging.internal;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.logging.StandardOutputListener;

import java.io.*;

public class StreamBackedStandardOutputListener implements StandardOutputListener {
    private final Appendable appendable;
    private final Flushable flushable;

    public StreamBackedStandardOutputListener(Appendable appendable) {
        this.appendable = appendable;
        if (appendable instanceof Flushable) {
            flushable = (Flushable) appendable;
        } else {
            flushable = new Flushable() {
                public void flush() throws IOException {
                }
            };
        }
    }

    public StreamBackedStandardOutputListener(OutputStream outputStream) {
        this(new OutputStreamWriter(outputStream));
    }

    public void onOutput(CharSequence output) {
        try {
            appendable.append(output);
            flushable.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
