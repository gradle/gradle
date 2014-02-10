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

    public List<SourceDependency> parseDependencies(File sourceFile) {
        List<SourceDependency> dependencies = new ArrayList<SourceDependency>();
        IncludesParser.Includes includes = includesParser.parseIncludes(sourceFile);

        searchForDependencies(dependencies, prependSourceDir(sourceFile, includePaths), includes.getQuotedIncludes());
        searchForDependencies(dependencies, includePaths, includes.getSystemIncludes());
        if (!includes.getMacroIncludes().isEmpty()) {
            dependencies.add(new SourceDependency(includes.getMacroIncludes().get(0), null));
        }

        return dependencies;
    }

    private List<File> prependSourceDir(File sourceFile, List<File> includePaths) {
        List<File> quotedSearchPath = new ArrayList<File>(includePaths.size() + 1);
        quotedSearchPath.add(sourceFile.getParentFile());
        quotedSearchPath.addAll(includePaths);
        return quotedSearchPath;
    }

    private void searchForDependencies(List<SourceDependency> dependencies, List<File> searchPath, List<String> includes) {
        for (String include : includes) {
            searchForDependency(dependencies, searchPath, include);
        }
    }

    private void searchForDependency(List<SourceDependency> dependencies, List<File> searchPath, String include) {
        for (File searchDir : searchPath) {
            File candidate = new File(searchDir, include);
            // TODO:DAZ This means that we'll never detect changed files if they are not on the defined include path
            if (candidate.isFile()) {
                dependencies.add(new SourceDependency(include, GFileUtils.canonicalise(candidate)));
                break;
            }
        }
    }
}
