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

package org.gradle.internal.logging.text;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.logging.StandardOutputListener;

import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

public class StreamBackedStandardOutputListener implements StandardOutputListener {
    private final Appendable appendable;
    private final Flushable flushable;

    public StreamBackedStandardOutputListener(Appendable appendable) {
        this.appendable = appendable;
        if (appendable instanceof Flushable) {
            flushable = (Flushable) appendable;
        } else {
            flushable = new Flushable() {
                @Override
                public void flush() {
                }
            };
        }
    }

    public StreamBackedStandardOutputListener(OutputStream outputStream) {
        this(new OutputStreamWriter(outputStream, Charset.defaultCharset()));
    }

    @Override
    public void onOutput(CharSequence output) {
        try {
            appendable.append(output);
            flushable.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
