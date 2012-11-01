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

import org.apache.commons.io.IOUtils;
import org.gradle.api.specs.Spec;
import org.gradle.external.javadoc.JavadocOptionFileOption;
import org.gradle.util.CollectionUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public class JavadocOptionFileWriter {
    private final JavadocOptionFile optionFile;

    public JavadocOptionFileWriter(JavadocOptionFile optionFile) {
        if (optionFile == null) {
            throw new IllegalArgumentException("optionFile == null!");
        }
        this.optionFile = optionFile;
    }

    void write(File outputFile) throws IOException {
        BufferedWriter writer = null;
        try {
            final Map<String, JavadocOptionFileOption> options = optionFile.getOptions();
            writer = new BufferedWriter(new FileWriter(outputFile));
            JavadocOptionFileWriterContext writerContext = new JavadocOptionFileWriterContext(writer);

            JavadocOptionFileOption localeOption = options.get("locale");
            if (localeOption != null) {
                localeOption.write(writerContext);
            }

            final Map<String, JavadocOptionFileOption> optionsWithoutLocale = CollectionUtils.filter(options, new Spec<Map.Entry<String, JavadocOptionFileOption>>() {
                public boolean isSatisfiedBy(Map.Entry<String, JavadocOptionFileOption> element) {
                    return !"locale".equals(element.getKey());
                }
            });
            for (final String option : optionsWithoutLocale.keySet()) {
                options.get(option).write(writerContext);
            }

            optionFile.getSourceNames().write(writerContext);
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }
}
