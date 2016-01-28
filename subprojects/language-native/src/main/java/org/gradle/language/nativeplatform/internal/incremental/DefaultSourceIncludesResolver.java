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
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.SourceIncludes;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefaultSourceIncludesResolver implements SourceIncludesResolver {
    private final List<File> includePaths;

    public DefaultSourceIncludesResolver(List<File> includePaths) {
        this.includePaths = includePaths;
    }

    public SourceIncludesResolutionResult resolveIncludes(File sourceFile, SourceIncludes includes) {
        Set<File> candidates = Sets.newLinkedHashSet();
        Set<ResolvedInclude> dependencies = Sets.newLinkedHashSet();
        searchForDependencies(dependencies, prependSourceDir(sourceFile, includePaths), includes.getQuotedIncludes(), candidates);
        searchForDependencies(dependencies, includePaths, includes.getSystemIncludes(), candidates);
        if (!includes.getMacroIncludes().isEmpty()) {
            dependencies.add(new ResolvedInclude(includes.getMacroIncludes().get(0).getValue(), null));
        }

        return new DefaultSourceIncludesResolutionResult(dependencies, candidates);
    }

    private List<File> prependSourceDir(File sourceFile, List<File> includePaths) {
        List<File> quotedSearchPath = new ArrayList<File>(includePaths.size() + 1);
        quotedSearchPath.add(sourceFile.getParentFile());
        quotedSearchPath.addAll(includePaths);
        return quotedSearchPath;
    }

    private void searchForDependencies(Set<ResolvedInclude> dependencies, List<File> searchPath, List<Include> includes, Set<File> candidates) {
        for (Include include : includes) {
            searchForDependency(dependencies, searchPath, include.getValue(), candidates);
        }
    }

    private void searchForDependency(Set<ResolvedInclude> dependencies, List<File> searchPath, String include, Set<File> candidates) {
        for (File searchDir : searchPath) {
            File candidate = new File(searchDir, include);
            candidates.add(candidate);
            if (candidate.isFile()) {
                dependencies.add(new ResolvedInclude(include, GFileUtils.canonicalise(candidate)));
                return;
            }
        }
    }

    private static class DefaultSourceIncludesResolutionResult implements SourceIncludesResolutionResult {
        private final Set<ResolvedInclude> dependencies;
        private final Set<File> candidates;

        private DefaultSourceIncludesResolutionResult(Set<ResolvedInclude> dependencies, Set<File> candidates) {
            this.dependencies = dependencies;
            this.candidates = candidates;
        }

        @Override
        public Set<ResolvedInclude> getDependencies() {
            return dependencies;
        }

        @Override
        public Set<File> getIncludeFileCandidates() {
            return candidates;
        }
    }
}
