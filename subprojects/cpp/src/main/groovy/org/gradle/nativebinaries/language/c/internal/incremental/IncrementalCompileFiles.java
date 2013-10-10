package org.gradle.nativebinaries.language.c.internal.incremental;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.UncheckedIOException;
import org.gradle.cache.PersistentIndexedCache;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class IncrementalCompileFiles implements IncrementalCompilation {

    private final PersistentIndexedCache<File, FileState> fileStateCache;
    private final SourceDependencyParser dependencyParser;

    private final List<File> previous;
    private final List<File> recompile = new ArrayList<File>();
    private final List<File> removed = new ArrayList<File>();

    private final Map<File, Boolean> processed = new HashMap<File, Boolean>();

    public IncrementalCompileFiles(List<File> previousSourceFiles, PersistentIndexedCache<File, FileState> fileStateCache, SourceDependencyParser dependencyParser) {
        this.fileStateCache = fileStateCache;
        this.dependencyParser = dependencyParser;
        this.previous = previousSourceFiles;
        this.removed.addAll(this.previous);
    }

    public void processSource(File sourceFile) {
        removed.remove(sourceFile);
        if (checkChangedAndUpdateState(sourceFile) || !previous.contains(sourceFile)) {
            recompile.add(sourceFile);
        }
    }

    public boolean checkChangedAndUpdateState(File file) {
        boolean changed = false;

        if (processed.containsKey(file)) {
            return processed.get(file);
        }

        // Assume unchanged if we recurse to the same file due to dependency cycle
        processed.put(file, false);

        FileState state = findState(file);
        if (state == null) {
            state = new FileState();
        }

        if (state.isChanged(file)) {
            changed = true;
            state.setText(getText(file));
            state.getDeps().addAll(dependencyParser.parseDependencies(file));
            saveState(file, state);
        }

        for (File dep : state.getDeps()) {
            Boolean depChanged = checkChangedAndUpdateState(dep);
            changed = changed || depChanged;
        }

        processed.put(file, changed);

        return changed;
    }

    private String getText(File file) {
        try {
            return DefaultGroovyMethods.getText(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FileState findState(File file) {
        return fileStateCache.get(file);
    }

    private void saveState(File file, FileState state) {
        fileStateCache.put(file, state);
    }

    public List<File> getRecompile() {
        return recompile;
    }

    public List<File> getRemoved() {
        return removed;
    }

    public Set<File> getTouched() {
        return processed.keySet();
    }
}
