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

import com.google.common.collect.Sets;
import org.gradle.internal.FileUtils;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.IncludeType;
import org.gradle.language.nativeplatform.internal.Macro;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultInclude;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultSourceIncludesResolver implements SourceIncludesResolver {
    private final List<File> includePaths;
    private final Map<File, Map<String, Boolean>> includeRoots;

    public DefaultSourceIncludesResolver(List<File> includePaths) {
        this.includePaths = includePaths;
        this.includeRoots = new HashMap<File, Map<String, Boolean>>();
    }

    @Override
    public ResolvedSourceIncludes resolveIncludes(File sourceFile, IncludeDirectives includes, List<IncludeDirectives> included) {
        BuildableResolvedSourceIncludes resolvedSourceIncludes = new BuildableResolvedSourceIncludes();
        List<File> quotedSearchPath = prependSourceDir(sourceFile, includePaths);
        for (Include include : includes.getIncludesAndImports()) {
            if (include.getType() == IncludeType.SYSTEM) {
                searchForDependency(includePaths, include.getValue(), resolvedSourceIncludes);
            } else if (include.getType() == IncludeType.QUOTED) {
                searchForDependency(quotedSearchPath, include.getValue(), resolvedSourceIncludes);
            } else if (include.getType() == IncludeType.MACRO) {
                boolean found = false;
                for (IncludeDirectives includeDirectives : included) {
                    for (Macro macro : includeDirectives.getMacros()) {
                        if (include.getValue().equals(macro.getName())) {
                            found = true;
                            Include expandedInclude = DefaultInclude.parse(macro.getValue(), include.isImport());
                            if (expandedInclude.getType() == IncludeType.QUOTED) {
                                searchForDependency(quotedSearchPath, expandedInclude.getValue(), resolvedSourceIncludes);
                            } else {
                                // TODO - keep expanding
                                // TODO - handle system includes, which also need to be expanded when the value of a macro
                                resolvedSourceIncludes.resolved(include.getValue(), null);
                            }
                        }
                    }
                }
                if (!found) {
                    resolvedSourceIncludes.resolved(include.getValue(), null);
                }
            }
        }

        return resolvedSourceIncludes;
    }

    private List<File> prependSourceDir(File sourceFile, List<File> includePaths) {
        List<File> quotedSearchPath = new ArrayList<File>(includePaths.size() + 1);
        quotedSearchPath.add(sourceFile.getParentFile());
        quotedSearchPath.addAll(includePaths);
        return quotedSearchPath;
    }

    private void searchForDependency(List<File> searchPath, String include, BuildableResolvedSourceIncludes dependencies) {
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
                    dependencies.resolved(include, candidate);
                    return;
                }
                continue;
            }

            boolean found = candidate.isFile();
            searchedIncludes.put(include, found);

            if (found) {
                dependencies.resolved(include, candidate);
                return;
            }
        }
    }

    private static class BuildableResolvedSourceIncludes implements ResolvedSourceIncludes {
        private final Set<ResolvedInclude> dependencies = Sets.newLinkedHashSet();
        private final Set<File> candidates = Sets.newLinkedHashSet();

        void searched(File candidate) {
            candidates.add(candidate);
        }

        void resolved(String rawInclude, File resolved) {
            File dependencyFile = resolved == null ? null : FileUtils.canonicalize(resolved);
            dependencies.add(ResolvedInclude.create(rawInclude, dependencyFile));
        }

        @Override
        public Set<ResolvedInclude> getResolvedIncludes() {
            return dependencies;
        }

        @Override
        public Set<File> getResolvedIncludeFiles() {
            Set<File> files = new LinkedHashSet<File>(dependencies.size());
            for (ResolvedInclude dependency : dependencies) {
                if (!dependency.isUnknown()) {
                    files.add(dependency.getFile());
                }
            }
            return files;
        }

        @Override
        public Set<File> getCheckedLocations() {
            return candidates;
        }
    }
}
