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

import org.gradle.api.logging.StandardOutputListener;

import java.io.Closeable;
import java.io.IOException;

/**
 * A {@link StyledTextOutput} implementation which writes text to some char stream. Ignores any
 * styling information.
 */
public class StreamingStyledTextOutput extends AbstractStyledTextOutput implements Closeable {
    private final StandardOutputListener listener;
    private final Closeable closeable;

    /**
     * Creates a text output which forwards text to the given listener.
     * @param listener The listener.
     */
    public StreamingStyledTextOutput(StandardOutputListener listener) {
        this(listener, listener);
    }

    /**
     * Creates a text output which writes text to the given appendable.
     * @param appendable The appendable.
     */
    public StreamingStyledTextOutput(final Appendable appendable) {
        this(appendable, new StreamBackedStandardOutputListener(appendable));
    }

    private StreamingStyledTextOutput(Object target, StandardOutputListener listener) {
        this.listener = listener;
        closeable = target instanceof Closeable ? (Closeable) target : null;
    }

    @Override
    protected void doAppend(String text) {
        listener.onOutput(text);
    }

    /**
     * Closes the target object if it implements {@link java.io.Closeable}.
     */
    @Override
    public void close() throws IOException {
        if (closeable != null) {
            closeable.close();
        }
    }
}
