/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import org.gradle.internal.concurrent.CompositeStoppable;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import java.io.Closeable;
import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Cleans up resources (e.g. file handles) after compilation has finished.
 */
class ResourceCleaningCompilationTask implements JavaCompiler.CompilationTask {
    private final JavaCompiler.CompilationTask delegate;
    private final Closeable fileManager;

    ResourceCleaningCompilationTask(JavaCompiler.CompilationTask delegate, Closeable fileManager) {
        this.delegate = delegate;
        this.fileManager = fileManager;
    }

    @Override
    public void addModules(Iterable<String> moduleNames) {
    }

    @Override
    public void setProcessors(Iterable<? extends Processor> processors) {
        delegate.setProcessors(processors);
    }

    @Override
    public void setLocale(Locale locale) {
        delegate.setLocale(locale);
    }

    @Override
    public Boolean call() {
        try {
            return delegate.call();
        } finally {
            CompositeStoppable.stoppable(fileManager).stop();
            cleanupZipCache();
        }
    }

    /**
     * The javac file manager uses a shared ZIP cache which keeps file handles open
     * after compilation. It's supposed to be tunable with the -XDuseOptimizedZip parameter,
     * but the {@link JavaCompiler#getStandardFileManager(DiagnosticListener, Locale, Charset)}
     * method does not take arguments, so the cache can't be turned off.
     * So instead we clean it ourselves using reflection.
     */
    private void cleanupZipCache() {
        try {
            Class<?> zipFileIndexCache = Class.forName("com.sun.tools.javac.file.ZipFileIndexCache");
            Object instance = zipFileIndexCache.getMethod("getSharedInstance").invoke(null);
            zipFileIndexCache.getMethod("clearCache").invoke(instance);
        } catch (Throwable e) {
            // Not an OpenJDK-compatible compiler or signature changed
        }
    }
}
