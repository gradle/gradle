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

import org.gradle.internal.FileUtils;
import org.gradle.language.nativeplatform.internal.Directive;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.IncludeType;
import org.gradle.language.nativeplatform.internal.Macro;
import org.gradle.language.nativeplatform.internal.MacroFunction;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultSourceIncludesResolver implements SourceIncludesResolver {
    private final List<File> includePaths;
    private final Map<File, Map<String, Boolean>> includeRoots;

    public DefaultSourceIncludesResolver(List<File> includePaths) {
        this.includePaths = includePaths;
        this.includeRoots = new HashMap<File, Map<String, Boolean>>();
    }

    @Override
    public IncludeResolutionResult resolveInclude(File sourceFile, Include include, List<IncludeDirectives> visibleIncludeDirectives) {
        BuildableResult resolvedSourceIncludes = new BuildableResult(include.getValue());
        resolveDirective(sourceFile, include, visibleIncludeDirectives, include, resolvedSourceIncludes, includePaths);
        return resolvedSourceIncludes;
    }

    private void resolveDirective(File sourceFile, Include include, List<IncludeDirectives> visibleIncludeDirectives, Directive directive, BuildableResult resolvedSourceIncludes, List<File> includePaths) {
        if (directive.getType() == IncludeType.SYSTEM) {
            searchForDependency(includePaths, directive.getValue(), resolvedSourceIncludes);
        } else if (directive.getType() == IncludeType.QUOTED) {
            List<File> quotedSearchPath = prependSourceDir(sourceFile, includePaths);
            searchForDependency(quotedSearchPath, directive.getValue(), resolvedSourceIncludes);
        } else if (directive.getType() == IncludeType.MACRO) {
            resolveMacroToIncludes(sourceFile, include, visibleIncludeDirectives, directive, resolvedSourceIncludes);
        } else if (directive.getType() == IncludeType.MACRO_FUNCTION) {
            resolveMacroFunctionToIncludes(sourceFile, include, visibleIncludeDirectives, directive, resolvedSourceIncludes);
        } else {
            resolvedSourceIncludes.unresolved();
        }
    }

    private void resolveMacroToIncludes(File sourceFile, Include include, List<IncludeDirectives> visibleIncludeDirectives, Directive directive, BuildableResult resolvedSourceIncludes) {
        boolean found = false;
        for (IncludeDirectives includeDirectives : visibleIncludeDirectives) {
            for (Macro macro : includeDirectives.getMacros()) {
                if (directive.getValue().equals(macro.getName())) {
                    found = true;
                    resolveDirective(sourceFile, include, visibleIncludeDirectives, macro, resolvedSourceIncludes, includePaths);
                }
            }
        }
        if (!found) {
            resolvedSourceIncludes.unresolved();
        }
    }

    private void resolveMacroFunctionToIncludes(File sourceFile, Include include, List<IncludeDirectives> visibleIncludeDirectives, Directive directive, BuildableResult resolvedSourceIncludes) {
        boolean found = false;
        for (IncludeDirectives includeDirectives : visibleIncludeDirectives) {
            for (MacroFunction macro : includeDirectives.getMacrosFunctions()) {
                if (directive.getValue().equals(macro.getName()) && macro.getParameterCount() == 0) {
                    found = true;
                    resolveDirective(sourceFile, include, visibleIncludeDirectives, macro, resolvedSourceIncludes, includePaths);
                }
            }
        }
        if (!found) {
            resolvedSourceIncludes.unresolved();
        }
    }

    private List<File> prependSourceDir(File sourceFile, List<File> includePaths) {
        List<File> quotedSearchPath = new ArrayList<File>(includePaths.size() + 1);
        quotedSearchPath.add(sourceFile.getParentFile());
        quotedSearchPath.addAll(includePaths);
        return quotedSearchPath;
    }

    private void searchForDependency(List<File> searchPath, String include, BuildableResult dependencies) {
        for (File searchDir : searchPath) {
            File candidate = new File(searchDir, include);

            Map<String, Boolean> searchedIncludes = includeRoots.get(searchDir);
            if (searchedIncludes == null) {
                searchedIncludes = new HashMap<String, Boolean>();
                includeRoots.put(searchDir, searchedIncludes);
            }
            dependencies.searched(candidate);
            if (searchedIncludes.containsKey(include)) {
                if (searchedIncludes.get(include)) {
                    dependencies.resolved(FileUtils.canonicalize(candidate));
                    return;
                }
                continue;
            }

            boolean found = candidate.isFile();
            searchedIncludes.put(include, found);

            if (found) {
                dependencies.resolved(FileUtils.canonicalize(candidate));
                return;
            }
        }
    }

    private static class BuildableResult implements IncludeResolutionResult {
        private final List<File> files = new ArrayList<File>();
        private final List<File> candidates = new ArrayList<File>();
        private final String include;
        private boolean missing;

        BuildableResult(String include) {
            this.include = include;
        }

        void searched(File candidate) {
            candidates.add(candidate);
        }

        void resolved(File file) {
            files.add(file);
        }

        void unresolved() {
            missing = true;
        }

        @Override
        public String getInclude() {
            return include;
        }

        @Override
        public boolean isComplete() {
            return !missing;
        }

        @Override
        public List<File> getFiles() {
            return files;
        }

        @Override
        public List<File> getCheckedLocations() {
            return candidates;
        }
    }
}
