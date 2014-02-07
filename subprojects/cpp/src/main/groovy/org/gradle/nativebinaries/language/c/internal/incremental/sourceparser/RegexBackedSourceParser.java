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
public class RegexBackedSourceParser implements SourceParser {
    private static final String INCLUDE_IMPORT_PATTERN = "\\s*#\\s*(include|import)\\s+((<[^>]+>)|(\"[^\"]+\"))";
    private final Pattern includePattern;

    public RegexBackedSourceParser() {
        this.includePattern = Pattern.compile(INCLUDE_IMPORT_PATTERN, Pattern.CASE_INSENSITIVE);
    }

    public SourceDetails parseSource(File sourceFile) {
        DefaultSourceDetails includes = new DefaultSourceDetails();
        parseFile(sourceFile, includes);
        return includes;
    }

    private void parseFile(File file, DefaultSourceDetails includes) {
        try {
            BufferedReader bf = new BufferedReader(new PreprocessingReader(new BufferedReader(new FileReader(file))));

            try {
                String line;
                while ((line = bf.readLine()) != null) {
                    Matcher m = includePattern.matcher(line);

                    if (m.lookingAt()) {
                        boolean isImport = "import".equals(m.group(1));
                        String included = m.group(2);
                        boolean isSystem = included.startsWith("<");
                        includes.add(strip(included), isImport, isSystem);
                    }
                }
            } finally {
                IOUtils.closeQuietly(bf);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String strip(String include) {
        return include.substring(1, include.length() - 1);
    }

    private static class DefaultSourceDetails implements SourceDetails {
        private final List<String> includes = new ArrayList<String>();
        private final List<String> systemIncludes = new ArrayList<String>();
        private final List<String> imports = new ArrayList<String>();
        private final List<String> systemImports = new ArrayList<String>();

        public List<String> getQuotedIncludes() {
            return includes;
        }

        public List<String> getSystemIncludes() {
            return systemIncludes;
        }

        public List<String> getQuotedImports() {
            return imports;
        }

        public List<String> getSystemImports() {
            return systemImports;
        }

        public void add(String value, boolean imported, boolean system) {
            if (imported && system) {
                systemImports.add(value);
            } else if (imported) {
                imports.add(value);
            } else if (system) {
                systemIncludes.add(value);
            } else {
                includes.add(value);
            }
        }
    }
}
