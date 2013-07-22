/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.external.javadoc.internal;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

public class JavadocOptionFileWriterContext {
    private final BufferedWriter writer;

    public JavadocOptionFileWriterContext(BufferedWriter writer) {
        this.writer = writer;
    }

    public JavadocOptionFileWriterContext write(String string) throws IOException {
        writer.write(string);
        return this;
    }

    public JavadocOptionFileWriterContext newLine() throws IOException {
        writer.newLine();
        return this;
    }

    public JavadocOptionFileWriterContext writeOptionHeader(String option) throws IOException {
        write("-");
        write(option);
        write(" ");
        return this;
    }

    public JavadocOptionFileWriterContext writeOption(String option) throws IOException {
        writeOptionHeader(option);
        newLine();
        return this;
    }

    public JavadocOptionFileWriterContext writeValueOption(String option, String value) throws IOException {
        writeOptionHeader(option);
        writeValue(value);
        newLine();
        return this;
    }

    public JavadocOptionFileWriterContext writeValue(String value) throws IOException {
        write("\'");
        write(value.replaceAll("\\\\", "\\\\\\\\"));
        write("\'");
        return this;
    }

    public JavadocOptionFileWriterContext writeValueOption(String option, Collection<String> values) throws IOException {
        for (final String value : values) {
            writeValueOption(option, value);
        }
        return this;
    }

    public JavadocOptionFileWriterContext writeValuesOption(String option, Collection<String> values, String joinValuesBy) throws IOException {
        StringBuilder builder = new StringBuilder();
        Iterator<String> valuesIt = values.iterator();
        while (valuesIt.hasNext()) {
            builder.append(valuesIt.next());
            if (valuesIt.hasNext()) {
                builder.append(joinValuesBy);
            }
        }
        writeValueOption(option, builder.toString());
        return this;
    }

    public JavadocOptionFileWriterContext writeMultilineValuesOption(String option, Collection<String> values) throws IOException {
        for (String value : values) {
            writeValueOption(option, value);
        }
        return this;
    }

    public JavadocOptionFileWriterContext writePathOption(String option, Collection<File> files, String joinValuesBy) throws IOException {
        StringBuilder builder = new StringBuilder();
        Iterator<File> filesIt = files.iterator();
        while (filesIt.hasNext()) {
            builder.append(filesIt.next().getAbsolutePath());
            if (filesIt.hasNext()) {
                builder.append(joinValuesBy);
            }
        }
        writeValueOption(option, builder.toString());
        return this;
    }
}
