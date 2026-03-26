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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class ArgWriter {

    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final Pattern WHITESPACE_OR_HASH = Pattern.compile("\\s|#");

    private final boolean backslashEscape;
    private final Pattern quotablePattern;
    private final Charset encoding;

    private ArgWriter(boolean backslashEscape, Pattern quotablePattern, Charset encoding) {
        this.backslashEscape = backslashEscape;
        this.quotablePattern = quotablePattern;
        this.encoding = encoding;
    }

    /**
     * Double quotes around args containing whitespace, backslash chars are escaped using double backslash, platform line separators.
     */
    public static ArgWriter unixStyle() {
        return new ArgWriter(true, WHITESPACE, Charset.defaultCharset());
    }

    /**
     * Double quotes around args containing whitespace or #, backslash chars are escaped using double backslash, platform line separators.
     *
     * See <a href='https://docs.oracle.com/javase/9/tools/java.htm#JSWOR-GUID-4856361B-8BFD-4964-AE84-121F5F6CF111'>java Command-Line Argument Files</a>.
     */
    public static ArgWriter javaStyle() {
        // TODO(https://github.com/gradle/gradle/issues/29303)
        return new ArgWriter(true, WHITESPACE_OR_HASH, Charset.defaultCharset());
    }

    /**
     * Double quotes around args containing whitespace, platform line separators.
     */
    public static ArgWriter windowsStyle() {
        return new ArgWriter(false, WHITESPACE, Charset.defaultCharset());
    }

    /**
     * Generates an arg file at the given location, using the given args, if necessary.
     * If no args are provided, no arg file is generated.
     *
     * @return The argument to place on the command line which delegates to the generated
     * arg file. Empty if no arg file is generated.
     */
    public List<String> generateArgsFile(List<String> args, File argsFile) {
        if (args.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.singletonList(doGenerateArgsFile(argsFile, args));
    }

    private String doGenerateArgsFile(File argsFile, List<String> args) {
        try {
            Files.createDirectories(argsFile.getParentFile().toPath());
            try (PrintWriter writer = new PrintWriter(argsFile, encoding.name())) {
                for (String arg : args) {
                    writeArgument(arg, writer);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not write options file '%s'.", argsFile.getAbsolutePath()), e);
        }

        return "@" + argsFile.getAbsolutePath();
    }

    /**
     * Writes an argument on a single line, escaping and quoting as required.
     */
    private void writeArgument(String arg, PrintWriter writer) {
        if (backslashEscape) {
            arg = arg.replace("\\", "\\\\").replace("\"", "\\\"");
        }
        if (arg.isEmpty()) {
            writer.print("\"\"");
        } else if (needsQuoting(arg)) {
            writer.print('\"');
            writer.print(arg);
            writer.print('\"');
        } else {
            writer.print(arg);
        }
        writer.println();
    }

    private boolean needsQuoting(String str) {
        return quotablePattern.matcher(str).find();
    }

}
