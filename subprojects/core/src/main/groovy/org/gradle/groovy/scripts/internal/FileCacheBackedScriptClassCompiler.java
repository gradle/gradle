/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.groovy.scripts.internal;

import groovy.lang.Script;
import org.codehaus.groovy.ast.ClassNode;
import org.gradle.api.Action;
import org.gradle.api.internal.changedetection.state.CachingFileSnapshotter;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.CacheValidator;
import org.gradle.cache.PersistentCache;
import org.gradle.groovy.scripts.NonExistentFileScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ScriptClassCompiler} which compiles scripts to a cache directory, and loads them from there.
 */
public class FileCacheBackedScriptClassCompiler implements ScriptClassCompiler, Closeable {
    private final ScriptCompilationHandler scriptCompilationHandler;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final CacheRepository cacheRepository;
    private final CacheValidator validator;
    private final CompositeStoppable caches = new CompositeStoppable();
    private final CachingFileSnapshotter snapshotter;
    private final ClassLoaderCache classLoaderCache;

    public FileCacheBackedScriptClassCompiler(CacheRepository cacheRepository, CacheValidator validator, ScriptCompilationHandler scriptCompilationHandler,
                                              ProgressLoggerFactory progressLoggerFactory, CachingFileSnapshotter snapshotter, ClassLoaderCache classLoaderCache) {
        this.cacheRepository = cacheRepository;
        this.validator = validator;
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.progressLoggerFactory = progressLoggerFactory;
        this.snapshotter = snapshotter;
        this.classLoaderCache = classLoaderCache;
    }

    @Override
    public <T extends Script, M> CompiledScript<T, M> compile(final ScriptSource source,
                                                              final ClassLoader classLoader,
                                                              final ClassLoaderId classLoaderId,
                                                              CompileOperation<M> operation,
                                                              final Class<T> scriptBaseClass,
                                                              Action<? super ClassNode> verifier) {
        if (source instanceof NonExistentFileScriptSource) {
            return emptyCompiledScript(classLoaderId, operation);
        }

        String hash = hashFor(source);
        Map<String, Object> properties = createCacheProperties(source, hash);

        String dslId = operation.getId();
        String cacheName = String.format("scripts/%s/%s", hash, operation.getCacheKey());
        PersistentCache cache = cacheRepository.cache(cacheName)
            .withProperties(properties)
            .withValidator(validator)
            .withDisplayName(String.format("%s class cache for %s", dslId, source.getDisplayName()))
            .withInitializer(new ProgressReportingInitializer(progressLoggerFactory, new CacheInitializer(source, classLoader, operation, verifier, scriptBaseClass)))
            .open();

        try {
            final File classesDir = classesDir(cache);
            final File metadataDir = metadataDir(cache);

            return scriptCompilationHandler.loadFromDir(source, classLoader, classesDir, metadataDir, operation, scriptBaseClass, classLoaderId);
        } finally {
            cache.close();
        }
    }

    private Map<String, Object> createCacheProperties(ScriptSource source, String hash) {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("source.filename", source.getFileName());
        properties.put("source.hash", hash);
        return properties;
    }

    private <T extends Script, M> CompiledScript<T, M> emptyCompiledScript(ClassLoaderId classLoaderId, CompileOperation<M> operation) {
        classLoaderCache.remove(classLoaderId);
        return new EmptyCompiledScript<T, M>(operation);
    }

    private String hashFor(ScriptSource source) {
        File file = source.getResource().getFile();
        String hash;
        if (file != null && file.exists()) {
            CachingFileSnapshotter.FileInfo snapshot = snapshotter.snapshot(file);
            hash = new HashValue(snapshot.getHash()).asCompactString();
        } else {
            hash = HashUtil.createCompactMD5(source.getResource().getText());
        }
        return hash;
    }

    public void close() {
        caches.stop();
    }

    private File classesDir(PersistentCache cache) {
        return new File(cache.getBaseDir(), "classes");
    }

    private File metadataDir(PersistentCache cache) {
        return new File(cache.getBaseDir(), "metadata");
    }

    private class CacheInitializer implements Action<PersistentCache> {
        private final Action<? super ClassNode> verifier;
        private final Class<? extends Script> scriptBaseClass;
        private final ClassLoader classLoader;
        private final CompileOperation<?> transformer;
        private final ScriptSource source;

        public <T extends Script> CacheInitializer(ScriptSource source, ClassLoader classLoader, CompileOperation<?> transformer,
                                                   Action<? super ClassNode> verifier, Class<T> scriptBaseClass) {
            this.source = source;
            this.classLoader = classLoader;
            this.transformer = transformer;
            this.verifier = verifier;
            this.scriptBaseClass = scriptBaseClass;
        }

        public void execute(PersistentCache cache) {
            File classesDir = classesDir(cache);
            File metadataDir = metadataDir(cache);
            scriptCompilationHandler.compileToDir(source, classLoader, classesDir, metadataDir, transformer, scriptBaseClass, verifier);
        }
    }

    static class ProgressReportingInitializer implements Action<PersistentCache> {
        private ProgressLoggerFactory progressLoggerFactory;
        private Action<? super PersistentCache> delegate;

        public ProgressReportingInitializer(ProgressLoggerFactory progressLoggerFactory, Action<PersistentCache> delegate) {
            this.progressLoggerFactory = progressLoggerFactory;
            this.delegate = delegate;
        }

        public void execute(PersistentCache cache) {
            ProgressLogger op = progressLoggerFactory.newOperation(FileCacheBackedScriptClassCompiler.class)
                .start("Compile script into cache", "Compiling script into cache");
            try {
                delegate.execute(cache);
            } finally {
                op.completed();
            }
        }
    }

    private static class EmptyCompiledScript<T extends Script, M> implements CompiledScript<T, M> {
        private final M data;

        public EmptyCompiledScript(CompileOperation<M> operation) {
            this.data = operation.getExtractedData();
        }

        @Override
        public boolean getRunDoesSomething() {
            return false;
        }

        @Override
        public boolean getHasMethods() {
            return false;
        }

        public Class<? extends T> loadClass() {
            throw new UnsupportedOperationException("Cannot load a script that does nothing.");
        }

        @Override
        public M getData() {
            return data;
        }
    }
}
