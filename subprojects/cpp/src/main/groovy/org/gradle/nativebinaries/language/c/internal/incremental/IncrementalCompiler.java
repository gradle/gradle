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

import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentStateCache;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class IncrementalCompiler {

    private final PersistentIndexedCache<File, FileState> fileStateCache;
    private final PersistentStateCache<List<File>> previousSourcesCache;
    private final SourceDependencyParser dependencyParser;

    public IncrementalCompiler(PersistentIndexedCache<File, FileState> fileStateCache, PersistentStateCache<List<File>> previousSourcesCache, SourceDependencyParser dependencyParser) {
        this.fileStateCache = fileStateCache;
        this.previousSourcesCache = previousSourcesCache;
        this.dependencyParser = dependencyParser;
    }

    public IncrementalCompileFiles processSourceFiles(List<File> sourceFiles) {
        List<File> previousSources = previousSourcesCache.get();
        previousSources = previousSources == null ? Collections.<File>emptyList() : previousSources;
        final IncrementalCompileFiles result = new IncrementalCompileFiles(previousSources, fileStateCache, dependencyParser);

        for (File sourceFile : sourceFiles) {
            result.processSource(sourceFile);
        }

        for (File removed : result.getRemoved()) {
            purgeRemoved(removed, result);
        }

        previousSourcesCache.set(sourceFiles);

        return result;
    }

    private void purgeRemoved(File removed, IncrementalCompileFiles result) {
        FileState state = fileStateCache.get(removed);
        if (state == null || result.getTouched().contains(removed)) {
            return;
        }

        fileStateCache.remove(removed);

        for (File file : state.getDeps()) {
            purgeRemoved(file, result);
        }
    }
}
