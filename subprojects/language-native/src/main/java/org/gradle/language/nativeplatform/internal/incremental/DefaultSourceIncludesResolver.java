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
    private final Map<File, Map<String, Candidate>> includeRoots;

    public DefaultSourceIncludesResolver(List<File> includePaths) {
        this.includePaths = includePaths;
        this.includeRoots = new HashMap<File, Map<String, Candidate>>();
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
                                resolvedSourceIncludes.resolved(new ResolvedInclude(include.getValue(), null));
                            }
                        }
                    }
                }
                if (!found) {
                    resolvedSourceIncludes.resolved(new ResolvedInclude(include.getValue(), null));
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
            Map<String, Candidate> searchedIncludes = includeRoots.get(searchDir);
            if (searchedIncludes == null) {
                searchedIncludes = new HashMap<String, Candidate>();
                includeRoots.put(searchDir, searchedIncludes);
            }

            Candidate candidate = searchedIncludes.get(include);
            if (candidate != null) {
                if (candidate.applyTo(dependencies)) {
                    return;
                }
                continue;
            }

            File candidateFile = new File(searchDir, include);
            if (candidateFile.isFile()) {
                candidate = new ResolvedIncludeFile(include, candidateFile);
            } else {
                candidate = new MissingIncludeFile(candidateFile);
            }
            searchedIncludes.put(include, candidate);

            if (candidate.applyTo(dependencies)) {
                return;
            }
        }
    }

    private interface Candidate {
        /**
         * @return true when this candidate exists.
         */
        boolean applyTo(BuildableResolvedSourceIncludes includes);
    }

    private static class ResolvedIncludeFile implements Candidate {
        private final ResolvedInclude resolvedInclude;

        ResolvedIncludeFile(String include, File file) {
            file = FileUtils.canonicalize(file);
            this.resolvedInclude = new ResolvedInclude(include, file);
        }

        @Override
        public boolean applyTo(BuildableResolvedSourceIncludes includes) {
            includes.searched(resolvedInclude.getFile());
            includes.resolved(resolvedInclude);
            return true;
        }
    }

    private static class MissingIncludeFile implements Candidate {
        private final File file;

        MissingIncludeFile(File file) {
            this.file = FileUtils.canonicalize(file);
        }

        @Override
        public boolean applyTo(BuildableResolvedSourceIncludes includes) {
            includes.searched(file);
            return false;
        }
    }

    private static class BuildableResolvedSourceIncludes implements ResolvedSourceIncludes {
        private final Set<ResolvedInclude> dependencies = Sets.newLinkedHashSet();
        private final Set<File> candidates = Sets.newLinkedHashSet();

        void searched(File candidate) {
            candidates.add(candidate);
        }

        void resolved(ResolvedInclude include) {
            dependencies.add(include);
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
