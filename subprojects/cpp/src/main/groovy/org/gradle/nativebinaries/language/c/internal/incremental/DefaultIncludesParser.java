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

import org.gradle.nativebinaries.language.c.internal.incremental.sourceparser.CSourceParser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DefaultIncludesParser implements IncludesParser {
    private final CSourceParser sourceParser;
    private final boolean importAware;

    public DefaultIncludesParser(CSourceParser sourceParser, boolean importAware) {
        this.sourceParser = sourceParser;
        this.importAware = importAware;
    }

    public Includes parseIncludes(File sourceFile) {
        return new DefaultIncludes(sourceParser.parseSource(sourceFile));
    }

    private class DefaultIncludes implements Includes {
        private final List<String> quotedIncludes = new ArrayList<String>();
        private final List<String> systemIncludes = new ArrayList<String>();
        private final List<String> unknownIncludes = new ArrayList<String>();

        private DefaultIncludes(CSourceParser.SourceDetails delegate) {
            List<String> includes = delegate.getIncludes();
            addAll(includes);
            if (importAware) {
                addAll(delegate.getImports());
            }
        }

        private void addAll(List<String> includes) {
            for (String value : includes) {
                if (value.startsWith("<") && value.endsWith(">")) {
                    systemIncludes.add(strip(value));
                } else if (value.startsWith("\"") && value.endsWith("\"")) {
                    quotedIncludes.add(strip(value));
                } else {
                    unknownIncludes.add(value);
                }
            }
        }

        private String strip(String include) {
            return include.substring(1, include.length() - 1);
        }

        public List<String> getQuotedIncludes() {
            return quotedIncludes;
        }

        public List<String> getSystemIncludes() {
            return systemIncludes;
        }
    }
}
