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

package org.gradle.api.tasks;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Writes a {@link Properties} in a way that the results can be expected to be reproducible.
 *
 * <p>There are a number of differences compared to {@link Properties#store(java.io.Writer, String)}:</p>
 * <ul>
 *     <li>no timestamp comment is generated at the beginning of the file</li>
 *     <li>the lines in the resulting files are separated by a pre-set separator (defaults to
 *         {@literal '\n'}) instead of the system default line separator</li>
 *     <li>the lines are sorted alphabetically</li>
 * </ul>
 *
 * <p>When the default Latin-1 (ISO-8859-1) encoding is used, Unicode characters get escaped,
 * just like with {@link Properties#store(OutputStream, String)}. Otherwise no escaping is performed.</p>
 *
 * @since 3.3
 */
@Incubating
@CacheableTask
@ParallelizableTask
public class WriteProperties extends DefaultTask {
    private Map<?, ?> properties = new Properties();
    private String lineSeparator = "\n";
    private Object outputFile;
    private String comment;
    private String encoding = "ISO_8859_1";

    /**
     * Returns the properties to be written to the output file.
     */
    @Input
    public Map<?, ?> getProperties() {
        return properties;
    }

    /**
     * Sets the properties to be written to the output file.
     */
    public void setProperties(Map<?, ?> properties) {
        this.properties = properties;
    }

    /**
     * Returns the line separator to be used when creating the properties file.
     * Defaults to {@literal `\n`}.
     */
    @Input
    public String getLineSeparator() {
        return lineSeparator;
    }

    /**
     * Sets the line separator to be used when creating the properties file.
     */
    public void setLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
    }

    /**
     * Returns the optional comment to add at the beginning of the properties file.
     */
    @Input
    @Optional
    public String getComment() {
        return comment;
    }

    /**
     * Sets the optional comment to add at the beginning of the properties file.
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Returns the encoding used to write the properties file. Defaults to {@literal ISO_8859_1}.
     * If set to anything different, unicode escaping is turned off.
     */
    @Input
    public String getEncoding() {
        return encoding;
    }

    /**
     * Sets the encoding used to write the properties file. Defaults to {@literal ISO_8859_1}.
     * If set to anything different, unicode escaping is turned off.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     * Returns the output file to write the properties to.
     */
    @OutputFile
    public File getOutputFile() {
        return getProject().file(outputFile);
    }

    /**
     * Sets the output file to write the properties to.
     */
    public void setOutputFile(Object outputFile) {
        this.outputFile = outputFile;
    }

    @TaskAction
    public void writeProperties() throws IOException {
        Properties propertiesToWrite;
        if (properties instanceof Properties) {
            propertiesToWrite = (Properties) properties;
        } else {
            propertiesToWrite = new Properties();
            propertiesToWrite.putAll(properties);
        }

        Charset charset = Charset.forName(encoding);

        String rawContents;
        if (charset.equals(Charsets.ISO_8859_1)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            propertiesToWrite.store(out, getComment());
            rawContents = new String(out.toByteArray(), Charsets.ISO_8859_1);
        } else {
            StringWriter out = new StringWriter();
            propertiesToWrite.store(out, getComment());
            rawContents = out.toString();
        }

        String systemLineSeparator = System.getProperty("line.separator");
        List<String> lines = Lists.newArrayList(Arrays.asList(rawContents.split(systemLineSeparator)));
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
        String contents = Joiner.on(getLineSeparator()).join(lines);

        File outputFile = getOutputFile();
        FileUtils.forceMkdir(outputFile.getParentFile());

        OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile));
        try {
            out.write(contents.getBytes(charset));
        } finally {
            IOUtils.closeQuietly(out);
        }
    }
}
