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

package org.gradle.internal.util;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.gradle.internal.IoActions;
import org.gradle.internal.SystemProperties;
import org.jspecify.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class PropertiesUtils {

    /**
     * Writes {@link java.util.Properties} in a way that the results can be expected to be reproducible.
     *
     * Uses defaults for the arguments of {@link PropertiesUtils#store(Properties, File, String, Charset, String)}:
     * <ul>
     *     <li>no comment</li>
     *     <li>line separator {@literal '\n'}</li>
     *     <li>charset ISO-8859-1</li>
     * </ul>
     */
    public static void store(Properties properties, File propertyFile) throws IOException {
        store(properties, propertyFile, null);
    }

    /**
     * Writes {@link java.util.Properties} in a way that the results can be expected to be reproducible.
     *
     * Uses defaults for the arguments of {@link PropertiesUtils#store(Properties, File, String, Charset, String)}:
     * <ul>
     *     <li>provided comment</li>
     *     <li>line separator {@literal '\n'}</li>
     *     <li>charset ISO-8859-1</li>
     * </ul>
     */
    public static void store(Properties properties, File propertyFile, @Nullable String comment) throws IOException {
        store(properties, propertyFile, comment, Charsets.ISO_8859_1, "\n");
    }

    /**
     * Writes {@link java.util.Properties} in a way that the results can be expected to be reproducible.
     *
     * See {@link #store(Properties, OutputStream, String, Charset, String)} for more details.
     */
    public static void store(Properties properties, File propertyFile, @Nullable String comment, Charset charset, String lineSeparator) throws IOException {
        BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(propertyFile));
        try {
            store(properties, outputStream, comment, charset, lineSeparator);
        } finally {
            IoActions.closeQuietly(outputStream);
        }
    }

    /**
     * Writes {@link java.util.Properties} in a way that the results can be expected to be reproducible.
     *
     * <p>There are a number of differences compared to {@link java.util.Properties#store(java.io.Writer, String)}:</p>
     * <ul>
     *     <li>no timestamp comment is generated at the beginning of the file</li>
     *     <li>the lines in the resulting files are separated by a pre-set separator instead of the system default line separator</li>
     *     <li>the properties are sorted alphabetically</li>
     * </ul>
     *
     * <p>Like with {@link java.util.Properties#store(java.io.OutputStream, String)}, Unicode characters are
     * escaped when using the default Latin-1 (ISO-8559-1) encoding.</p>
     */
    public static void store(Properties properties, OutputStream outputStream, @Nullable String comment, Charset charset, String lineSeparator) throws IOException {
        String rawContents;
        if (charset.equals(Charsets.ISO_8859_1)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            properties.store(out, comment);
            rawContents = new String(out.toByteArray(), Charsets.ISO_8859_1);
        } else {
            StringWriter out = new StringWriter();
            properties.store(out, comment);
            rawContents = out.toString();
        }

        String systemLineSeparator = SystemProperties.getInstance().getLineSeparator();
        List<String> lines = Lists.newArrayList(Splitter.on(systemLineSeparator).omitEmptyStrings().split(rawContents));
        int lastCommentLine = -1;
        for (int lineNo = 0, len = lines.size(); lineNo < len; lineNo++) {
            String line = lines.get(lineNo);
            if (line.startsWith("#")) {
                lastCommentLine = lineNo;
            }
        }

        // The last comment line is the timestamp
        List<String> nonCommentLines;
        if (lastCommentLine != -1) {
            lines.remove(lastCommentLine);
            nonCommentLines = lines.subList(lastCommentLine, lines.size());
        } else {
            nonCommentLines = lines;
        }

        Collections.sort(nonCommentLines);
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line);
            builder.append(lineSeparator);
        }
        outputStream.write(builder.toString().getBytes(charset));
    }
}
