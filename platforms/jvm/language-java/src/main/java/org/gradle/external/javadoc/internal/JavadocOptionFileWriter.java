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

import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;
import org.gradle.external.javadoc.JavadocOptionFileOption;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public class JavadocOptionFileWriter {
    private final JavadocOptionFile optionFile;

    public JavadocOptionFileWriter(JavadocOptionFile optionFile) {
        if (optionFile == null) {
            throw new IllegalArgumentException("optionFile == null!");
        }
        this.optionFile = optionFile;
    }

    void write(File outputFile) throws IOException {
        IoActions.writeTextFile(outputFile, new ErroringAction<BufferedWriter>() {
            @Override
            protected void doExecute(BufferedWriter writer) throws Exception {
                final Map<String, JavadocOptionFileOption<?>> options = new TreeMap<String, JavadocOptionFileOption<?>>(optionFile.getOptions());
                JavadocOptionFileWriterContext writerContext = new JavadocOptionFileWriterContext(writer);

                JavadocOptionFileOption<?> localeOption = options.remove("locale");
                if (localeOption != null) {
                    localeOption.write(writerContext);
                }

                for (final String option : options.keySet()) {
                    options.get(option).write(writerContext);
                }

                optionFile.getSourceNames().write(writerContext);
            }
        });
    }
}
