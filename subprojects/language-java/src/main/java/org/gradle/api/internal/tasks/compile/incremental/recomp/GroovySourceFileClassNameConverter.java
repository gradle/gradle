/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import com.google.common.collect.Multimap;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GroovySourceFileClassNameConverter {
    private final Multimap<File, String> sourceClassesMapping;
    private final Map<String, File> classSourceMapping;
    private final CompilationSourceDirs compilationSourceDirs;

    public GroovySourceFileClassNameConverter(CompilationSourceDirs sourceDirs, Multimap<File, String> sourceClassesMapping) {
        this.compilationSourceDirs = sourceDirs;
        this.sourceClassesMapping = sourceClassesMapping;
        this.classSourceMapping = constructReverseMapping(sourceClassesMapping);
    }

    private Map<String, File> constructReverseMapping(Multimap<File, String> sourceClassesMapping) {
        return sourceClassesMapping.entries().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    }

    public Collection<String> getClassNames(File sourceFile) {
        return compilationSourceDirs.relativize(sourceFile)
            .map(relativeFile -> sourceClassesMapping.get(relativeFile))
            .orElse(Collections.emptyList());
    }

    boolean isEmpty() {
        return classSourceMapping.isEmpty();
    }

    Optional<File> getFileRelativePath(String fqcn) {
        return Optional.ofNullable(classSourceMapping.get(fqcn));
    }
}
