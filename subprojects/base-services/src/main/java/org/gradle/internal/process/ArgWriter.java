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

package org.gradle.internal.process;

import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class ArgWriter implements ArgCollector {
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private final PrintWriter writer;
    private final boolean backslashEscape;

    private ArgWriter(PrintWriter writer, boolean backslashEscape) {
        this.writer = writer;
        this.backslashEscape = backslashEscape;
    }

    /**
     * Double quotes around args containing whitespace, backslash chars are escaped using double backslash, platform line separators.
     */
    public static ArgWriter unixStyle(PrintWriter writer) {
        return new ArgWriter(writer, true);
    }

    public static Transformer<ArgWriter, PrintWriter> unixStyleFactory() {
        return new Transformer<ArgWriter, PrintWriter>() {
            public ArgWriter transform(PrintWriter original) {
                return unixStyle(original);
            }
        };
    }

    /**
     * Double quotes around args containing whitespace, platform line separators.
     */
    public static ArgWriter windowsStyle(PrintWriter writer) {
        return new ArgWriter(writer, false);
    }

    public static Transformer<ArgWriter, PrintWriter> windowsStyleFactory() {
        return new Transformer<ArgWriter, PrintWriter>() {
            public ArgWriter transform(PrintWriter original) {
                return windowsStyle(original);
            }
        };
    }

    /**
     * Returns an args transformer that replaces the provided args with a generated args file containing the args. Uses platform text encoding.
     */
    public static Transformer<List<String>, List<String>> argsFileGenerator(final File argsFile, final Transformer<ArgWriter, PrintWriter> argWriterFactory) {
        return new Transformer<List<String>, List<String>>() {
            @Override
            public List<String> transform(List<String> args) {
                if (args.isEmpty()) {
                    return args;
                }
                argsFile.getParentFile().mkdirs();
                try {
                    PrintWriter writer = new PrintWriter(argsFile);
                    try {
                        ArgWriter argWriter = argWriterFactory.transform(writer);
                        argWriter.args(args);
                    } finally {
                        writer.close();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(String.format("Could not write options file '%s'.", argsFile.getAbsolutePath()), e);
                }
                return Collections.singletonList("@" + argsFile.getAbsolutePath());
            }
        };
    }

    /**
     * Writes a set of args on a single line, escaping and quoting as required.
     */
    public ArgWriter args(Object... args) {
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (i > 0) {
                writer.print(' ');
            }
            String str = arg.toString();
            if (backslashEscape) {
                str = str.replace("\\", "\\\\").replace("\"", "\\\"");
            }
            if (WHITESPACE.matcher(str).find()) {
                writer.print('\"');
                writer.print(str);
                writer.print('\"');
            } else {
                writer.print(str);
            }
        }
        writer.println();
        return this;
    }

    public ArgCollector args(Iterable<?> args) {
        for (Object arg : args) {
            args(arg);
        }
        return this;
    }
}
