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

package org.gradle.language.nativeplatform.internal.incremental;

import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.CSourceParser;

import java.io.File;

public class DefaultSourceIncludesParser implements SourceIncludesParser {
    private final CSourceParser sourceParser;
    private final boolean importAware;

    public DefaultSourceIncludesParser(CSourceParser sourceParser, boolean importAware) {
        this.sourceParser = sourceParser;
        this.importAware = importAware;
    }

    @Override
    public IncludeDirectives parseIncludes(File sourceFile) {
        IncludeDirectives parsedIncludes = sourceParser.parseSource(sourceFile);
        if (importAware) {
            return parsedIncludes;
        } else {
            return parsedIncludes.discardImports();
        }
    }

}
