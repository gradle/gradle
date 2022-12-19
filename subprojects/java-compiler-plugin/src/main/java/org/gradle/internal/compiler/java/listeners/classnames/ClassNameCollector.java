/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.compiler.java.listeners.classnames;

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

public class ClassNameCollector implements TaskListener {
    private final Map<File, Optional<String>> relativePaths = new HashMap<>();
    private final Consumer<String> classBackupService;
    private final Map<String, Set<String>> mapping = new HashMap<>();
    private final Function<File, Optional<String>> relativize;
    private final Elements elements;

    public ClassNameCollector(Function<File, Optional<String>> relativize, Consumer<String> classBackupService, Elements elements) {
        this.relativize = relativize;
        this.classBackupService = classBackupService;
        this.elements = elements;
    }

    public Map<String, Set<String>> getMapping() {
        return mapping;
    }

    @Override
    public void started(TaskEvent e) {
        JavaFileObject sourceFile = e.getSourceFile();
        if (isSourceFile(sourceFile)) {
            File asSourceFile = new File(sourceFile.getName());
            if (isClassGenerationPhase(e)) {
                processSourceFile(e, asSourceFile);
            } else if (isPackageInfoFile(e, asSourceFile)) {
                processPackageInfo(asSourceFile);
            }
        }
    }

    @Override
    public void finished(TaskEvent e) {
    }

    private static boolean isSourceFile(JavaFileObject sourceFile) {
        return sourceFile != null && sourceFile.getKind() == JavaFileObject.Kind.SOURCE;
    }

    private void processSourceFile(TaskEvent e, File sourceFile) {
        Optional<String> relativePath = findRelativePath(sourceFile);
        if (relativePath.isPresent()) {
            String key = relativePath.get();
            String symbol = normalizeName(e.getTypeElement());
            registerMapping(key, symbol);
            classBackupService.accept(symbol);
        }
    }

    private void processPackageInfo(File sourceFile) {
        Optional<String> relativePath = findRelativePath(sourceFile);
        if (relativePath.isPresent()) {
            String key = relativePath.get();
            String pkgInfo = key.substring(0, key.lastIndexOf(".java")).replace('/', '.');
            registerMapping(key, pkgInfo);
        }
    }

    private Optional<String> findRelativePath(File asSourceFile) {
        return relativePaths.computeIfAbsent(asSourceFile, relativize);
    }

    private String normalizeName(TypeElement typeElement) {
        String symbol = typeElement.getQualifiedName().toString();
        if (symbol.endsWith("module-info")) {
            symbol = "module-info";
        } else if (typeElement.getNestingKind().isNested()) {
            symbol = elements.getBinaryName(typeElement).toString();
        }
        return symbol;
    }

    private static boolean isPackageInfoFile(TaskEvent e, File asSourceFile) {
        return e.getKind() == TaskEvent.Kind.ANALYZE && "package-info.java".equals(asSourceFile.getName());
    }

    private static boolean isClassGenerationPhase(TaskEvent e) {
        return e.getKind() == TaskEvent.Kind.GENERATE;
    }

    public void registerMapping(String key, String symbol) {
        Collection<String> symbols = mapping.computeIfAbsent(key, k -> new TreeSet<>());
        symbols.add(symbol);
    }

}
