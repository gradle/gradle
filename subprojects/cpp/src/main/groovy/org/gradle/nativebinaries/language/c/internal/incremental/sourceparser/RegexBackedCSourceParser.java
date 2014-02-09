/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.language.c.internal.incremental.sourceparser;

import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO:DAZ We might be better with a hand-crafter parser
public class RegexBackedCSourceParser implements CSourceParser {
    private static final String INCLUDE_IMPORT_PATTERN = "\\s*#\\s*(include|import)\\s+((<[^>]+>)|(\"[^\"]+\")|(\\w+))";
    private final Pattern includePattern;

    public RegexBackedCSourceParser() {
        this.includePattern = Pattern.compile(INCLUDE_IMPORT_PATTERN, Pattern.CASE_INSENSITIVE);
    }

    public SourceDetails parseSource(File sourceFile) {
        DefaultSourceDetails sourceDetails = new DefaultSourceDetails();
        parseFile(sourceFile, sourceDetails);
        return sourceDetails;
    }

    private void parseFile(File file, DefaultSourceDetails sourceDetails) {
        try {
            BufferedReader bf = new BufferedReader(new PreprocessingReader(new BufferedReader(new FileReader(file))));

            try {
                String line;
                while ((line = bf.readLine()) != null) {
                    Matcher m = includePattern.matcher(line);

                    if (m.lookingAt()) {
                        boolean isImport = "import".equals(m.group(1));
                        String value = m.group(2);
                        if (isImport) {
                            sourceDetails.getImports().add(value);
                        } else {
                            sourceDetails.getIncludes().add(value);
                        }
                    }
                }
            } finally {
                IOUtils.closeQuietly(bf);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static class DefaultSourceDetails implements SourceDetails {
        private final List<String> includes = new ArrayList<String>();
        private final List<String> imports = new ArrayList<String>();

        public List<String> getIncludes() {
            return includes;
        }

        public List<String> getImports() {
            return imports;
        }
    }
}
