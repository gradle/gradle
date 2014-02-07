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
import java.util.List;

public class DefaultIncludesParser implements IncludesParser {
    private final SourceParser sourceParser;

    public DefaultIncludesParser(SourceParser sourceParser) {
        this.sourceParser = sourceParser;
    }

    public Includes parseIncludes(File sourceFile) {
        return new DefaultIncludes(sourceParser.parseSource(sourceFile));
    }

    private class DefaultIncludes implements Includes {
        private final SourceParser.SourceDetails delegate;

        private DefaultIncludes(SourceParser.SourceDetails delegate) {
            this.delegate = delegate;
        }

        public List<String> getQuotedIncludes() {
            return delegate.getQuotedIncludes();
        }

        public List<String> getSystemIncludes() {
            return delegate.getSystemIncludes();
        }
    }
}
