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

import org.gradle.nativebinaries.language.c.internal.incremental.sourceparser.SourceParser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImportsIncludedIncludesParser implements IncludesParser {
    private final SourceParser sourceParser;

    public ImportsIncludedIncludesParser(SourceParser sourceParser) {
        this.sourceParser = sourceParser;
    }

    public Includes parseIncludes(File sourceFile) {
        return new ImportsIncludedIncludes(sourceParser.parseSource(sourceFile));
    }

    private class ImportsIncludedIncludes implements Includes {
        private final SourceParser.SourceDetails delegate;

        private ImportsIncludedIncludes(SourceParser.SourceDetails delegate) {
            this.delegate = delegate;
        }

        public List<String> getQuotedIncludes() {
            return merge(delegate.getQuotedIncludes(), delegate.getQuotedImports());
        }

        public List<String> getSystemIncludes() {
            return merge(delegate.getSystemIncludes(), delegate.getSystemImports());
        }

        private List<String> merge(List<String> one, List<String> two) {
            List<String> combined = new ArrayList<String>(one.size() + two.size());
            combined.addAll(one);
            combined.addAll(two);
            return combined;
        }
    }
}
