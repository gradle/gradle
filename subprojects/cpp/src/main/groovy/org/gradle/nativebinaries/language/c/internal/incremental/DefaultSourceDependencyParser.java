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

import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DefaultSourceDependencyParser implements SourceDependencyParser {
    private final IncludesParser includesParser;
    private final List<File> includePaths;

    public DefaultSourceDependencyParser(IncludesParser includesParser, List<File> includePaths) {
        this.includesParser = includesParser;
        this.includePaths = includePaths;
    }

    public List<File> parseDependencies(File sourceFile) {
        List<File> dependencies = new ArrayList<File>();
        IncludesParser.Includes includes = includesParser.parseIncludes(sourceFile);

        searchForDependencies(dependencies, prependSourceDir(sourceFile, includePaths), includes.getQuotedIncludes());
        searchForDependencies(dependencies, includePaths, includes.getSystemIncludes());

        return dependencies;
    }

    private List<File> prependSourceDir(File sourceFile, List<File> includePaths) {
        List<File> quotedSearchPath = new ArrayList<File>(includePaths.size() + 1);
        quotedSearchPath.add(sourceFile.getParentFile());
        quotedSearchPath.addAll(includePaths);
        return quotedSearchPath;
    }

    private void searchForDependencies(List<File> dependencies, List<File> quotedSearchPath, List<String> quotedIncludes) {
        for (String include : quotedIncludes) {
            searchForDependency(dependencies, quotedSearchPath, include);
        }
    }

    private void searchForDependency(List<File> dependencies, List<File> quotedSearchPath, String include) {
        for (File searchDir : quotedSearchPath) {
            File candidate = new File(searchDir, include);
            if (candidate.exists()) {
                dependencies.add(GFileUtils.canonicalise(candidate));
                break;
            }
        }
    }
}
