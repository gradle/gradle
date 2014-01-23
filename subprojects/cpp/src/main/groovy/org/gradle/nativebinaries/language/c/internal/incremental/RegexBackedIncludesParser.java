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
package org.gradle.nativebinaries.language.c.internal.incremental;

import org.gradle.api.UncheckedIOException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexBackedIncludesParser implements IncludesParser {
    private final Pattern includePattern  = Pattern.compile("#(include|import)\\s+((<[^>]+>)|(\"[^\"]+\"))", Pattern.CASE_INSENSITIVE);

    public Includes parseIncludes(File sourceFile) {
        DefaultIncludes includes = new DefaultIncludes();
        parseFile(sourceFile, includes);
        return includes;
    }

    private void parseFile(File file, DefaultIncludes includes) {
        try {
            BufferedReader bf = new BufferedReader(new FileReader(file));
            try {
                String line;

                while ((line = bf.readLine()) != null) {
                    Matcher m = includePattern.matcher(line);

                    while (m.find()) {
                        String include = m.group(2);
                        if (include.startsWith("<")) {
                            includes.getSystemIncludes().add(strip(include));
                        } else {
                            includes.getQuotedIncludes().add(strip(include));
                        }
                    }
                }
            } finally {
                bf.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String strip(String include) {
        return include.substring(1, include.length() - 1);
    }

    private static class DefaultIncludes implements Includes {
        private final List<String> includes = new ArrayList<String>();
        private final List<String> systemIncludes = new ArrayList<String>();

        public List<String> getQuotedIncludes() {
            return includes;
        }

        public List<String> getSystemIncludes() {
            return systemIncludes;
        }
    }
}
