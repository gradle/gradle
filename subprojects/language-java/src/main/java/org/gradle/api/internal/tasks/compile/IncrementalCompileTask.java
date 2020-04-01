/*
 * Copyright 2020 the original author or authors.
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

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.code.Symbol;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

public class IncrementalCompileTask implements JavaCompiler.CompilationTask {

    private final File mappingFile;
    private final CompilationSourceDirs compilationSourceDirs;
    private final JavaCompiler.CompilationTask delegate;

    public IncrementalCompileTask(File mappingFile, CompilationSourceDirs compilationSourceDirs, JavaCompiler.CompilationTask delegate) {
        this.mappingFile = mappingFile;
        this.compilationSourceDirs = compilationSourceDirs;
        this.delegate = delegate;
    }

    @Override
    public void addModules(Iterable<String> moduleNames) {
        delegate.addModules(moduleNames);
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
        if (delegate instanceof JavacTask) {
            ClassNameCollector collector = new ClassNameCollector(compilationSourceDirs);
            ((JavacTask) delegate).addTaskListener(collector);
            try {
                return delegate.call();
            } finally {
                persistMappingFile(collector.getMapping());
            }
        } else {
            throw new UnsupportedOperationException("Unexpected Java compile task : " + delegate.getClass().getName());
        }
    }

    void persistMappingFile(Map<String, Collection<String>> mapping) {
        SourceClassesMappingFileAccessor.writeSourceClassesMappingFile(mappingFile, mapping);
    }

    private static class ClassNameCollector implements TaskListener {
        private final Map<File, Optional<String>> relativePaths = new HashMap<>();
        private final Map<String, Collection<String>> mapping = new HashMap<>();
        private final CompilationSourceDirs compilationSourceDirs;

        private ClassNameCollector(CompilationSourceDirs compilationSourceDirs) {
            this.compilationSourceDirs = compilationSourceDirs;
        }

        @Override
        public void started(TaskEvent e) {

        }

        @Override
        public void finished(TaskEvent e) {
            JavaFileObject sourceFile = e.getSourceFile();
            if (sourceFile != null && sourceFile.getKind() == JavaFileObject.Kind.SOURCE) {
                    File asSourceFile = new File(sourceFile.getName());
                    if (isClassGenerationPhase(e) && asSourceFile.exists()) {
                        Optional<String> relativePath = findRelativePath(asSourceFile);
                        if (relativePath.isPresent()) {
                            String key = relativePath.get();
                            TypeElement typeElement = e.getTypeElement();
                            Name name = typeElement.getQualifiedName();
                            if (typeElement instanceof Symbol.TypeSymbol) {
                                Symbol.TypeSymbol symbol = (Symbol.TypeSymbol) typeElement;
                                name = symbol.flatName();
                            }
                            String symbol = normalizeName(name);
                            registerMapping(key, symbol);
                        }
                    } else if (isPackageInfoFile(e, asSourceFile)) {
                        Optional<String> relativePath = findRelativePath(asSourceFile);
                        if (relativePath.isPresent()) {
                            String key = relativePath.get();
                            String pkgInfo = key.substring(0, key.lastIndexOf(".java")).replace('/', '.');
                            registerMapping(key, pkgInfo);
                        }
                    }
            }
        }

        public Optional<String> findRelativePath(File asSourceFile) {
            return relativePaths.computeIfAbsent(asSourceFile, compilationSourceDirs::relativize);
        }

        public String normalizeName(Name name) {
            String symbol = name.toString();
            if (symbol.endsWith("module-info")) {
                symbol = "module-info";
            }
            return symbol;
        }

        public boolean isPackageInfoFile(TaskEvent e, File asSourceFile) {
            return e.getKind() == TaskEvent.Kind.ANALYZE && "package-info.java".equals(asSourceFile.getName());
        }

        public boolean isClassGenerationPhase(TaskEvent e) {
            return e.getKind() == TaskEvent.Kind.GENERATE;
        }

        public void registerMapping(String key, String symbol) {
            Collection<String> symbols = mapping.get(key);
            if (symbols == null) {
                symbols = new TreeSet<String>();
                mapping.put(key, symbols);
            }
            symbols.add(symbol);
        }

        private Map<String, Collection<String>> getMapping() {
            return mapping;
        }
    }
}
