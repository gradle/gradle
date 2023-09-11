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

import org.gradle.internal.SystemProperties;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;

public class JavadocOptionFileWriterContext {
    private final Writer writer;

    public JavadocOptionFileWriterContext(Writer writer) {
        this.writer = writer;
    }

    public JavadocOptionFileWriterContext write(String string) throws IOException {
        writer.write(string);
        return this;
    }

    public JavadocOptionFileWriterContext newLine() throws IOException {
        writer.write(SystemProperties.getInstance().getLineSeparator());
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
        //First, we replace slashes because they have special meaning in the javadoc options file
        //Then, we replace every linebreak with slash+linebreak. Slash is needed according to javadoc options file format
        write(value.replaceAll("\\\\", "\\\\\\\\")
                //below does not help on windows environments. I was unable to get plain javadoc utility to work successfully with multiline options _in_ the options file.
                //at least, it will work out of the box on linux or mac environments.
                //on windows, the options file will have correct contents according to the javadoc spec but it may not work (the failure will be exactly the same as if we didn't replace line breaks)
                .replaceAll(SystemProperties.getInstance().getLineSeparator(), "\\\\" + SystemProperties.getInstance().getLineSeparator())
                .replace("\'", "\\'"));
        write("\'");
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
