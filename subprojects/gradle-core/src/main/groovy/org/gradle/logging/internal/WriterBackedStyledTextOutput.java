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
package org.gradle.logging.internal;

import org.gradle.api.UncheckedIOException;
import org.gradle.logging.StyledTextOutput;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;

public class WriterBackedStyledTextOutput extends AbstractStyledTextOutput implements Closeable {
    private final Writer writer;

    public WriterBackedStyledTextOutput(Writer writer) {
        this.writer = writer;
    }

    public Writer getWriter() {
        return writer;
    }

    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public StyledTextOutput style(Style style) {
        return this;
    }

    public StyledTextOutput text(Object text) {
        try {
            writer.append(text.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }
}
