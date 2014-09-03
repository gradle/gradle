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
import org.codehaus.groovy.classgen.Verifier;
import org.gradle.api.Action;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.CacheValidator;
import org.gradle.cache.PersistentCache;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.Transformer;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.hash.HashUtil;
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
    private ProgressLoggerFactory progressLoggerFactory;
    private final CacheRepository cacheRepository;
    private final CacheValidator validator;
    private final CompositeStoppable caches = new CompositeStoppable();

    public FileCacheBackedScriptClassCompiler(CacheRepository cacheRepository, CacheValidator validator, ScriptCompilationHandler scriptCompilationHandler, ProgressLoggerFactory progressLoggerFactory) {
        this.cacheRepository = cacheRepository;
        this.validator = validator;
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.progressLoggerFactory = progressLoggerFactory;
    }

    public <T extends Script> Class<? extends T> compile(ScriptSource source, ClassLoader classLoader, Transformer transformer, Class<T> scriptBaseClass, Verifier verifier) {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("source.filename", source.getFileName());
        properties.put("source.hash", HashUtil.createCompactMD5(source.getResource().getText()));

        String cacheName = String.format("scripts/%s/%s/%s", source.getClassName(), scriptBaseClass.getSimpleName(), transformer.getId());
        PersistentCache cache = cacheRepository.cache(cacheName)
                .withProperties(properties)
                .withValidator(validator)
                .withDisplayName(String.format("%s class cache for %s", transformer.getId(), source.getDisplayName()))
                .withInitializer(new ProgressReportingInitializer(progressLoggerFactory, new CacheInitializer(source, classLoader, transformer, verifier, scriptBaseClass)))
                .open();

        // This isn't quite right. The cache will be closed at the end of the build, releasing the shared lock on the classes. Instead, the cache for a script should be
        // closed once we no longer require the script classes. This may be earlier than the end of the current build, or it may used across multiple builds
        caches.add(cache);

        File classesDir = classesDir(cache);
        return scriptCompilationHandler.loadFromDir(source, classLoader, classesDir, scriptBaseClass);
    }

    public void close() {
        caches.stop();
    }

    private File classesDir(PersistentCache cache) {
        return new File(cache.getBaseDir(), "classes");
    }

    private class CacheInitializer implements Action<PersistentCache> {
        private final Verifier verifier;
        private final Class<? extends Script> scriptBaseClass;
        private final ClassLoader classLoader;
        private final Transformer transformer;
        private final ScriptSource source;

        private CacheInitializer(ScriptSource source, ClassLoader classLoader, Transformer transformer, Verifier verifier, Class<? extends Script> scriptBaseClass) {
            this.source = source;
            this.classLoader = classLoader;
            this.transformer = transformer;
            this.verifier = verifier;
            this.scriptBaseClass = scriptBaseClass;
        }

        public void execute(PersistentCache cache) {
            File classesDir = classesDir(cache);
            scriptCompilationHandler.compileToDir(source, classLoader, classesDir, transformer, scriptBaseClass, verifier);
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
}
