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
