/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog;

import com.google.common.base.Splitter;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AbstractSourceGenerator {
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[.\\-_]");
    protected final Writer writer;
    private final String ln = System.getProperty("line.separator", "\n");
    private int indent = 0;

    public AbstractSourceGenerator(Writer writer) {
        this.writer = writer;
    }

    static String toJavaName(String alias) {
        return nameSplitter()
            .splitToList(alias)
            .stream()
            .map(StringUtils::capitalize)
            .collect(Collectors.joining());
    }

    protected static Splitter nameSplitter() {
        return Splitter.on(SEPARATOR_PATTERN);
    }

    protected void addImport(Class<?> clazz) throws IOException {
        addImport(clazz.getCanonicalName());
    }

    protected void addImport(String clazz) throws IOException {
        writeLn("import " + clazz + ";");
    }

    protected void writeLn() throws IOException {
        writer.write(ln);
    }

    public void writeLn(String source) throws IOException {
        writeIndent();
        writer.write(source + ln);
    }

    protected void writeIndent() throws IOException {
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    public void indent(WriteAction action) throws IOException {
        try {
            indent++;
            action.run();
        } finally {
            indent--;
        }
    }

    @FunctionalInterface
    interface WriteAction {
        void run() throws IOException;
    }
}
