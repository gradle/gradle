/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.analyzer;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.cache.Cache;
import org.gradle.internal.hash.HashCode;

public class CachingClassDependenciesAnalyzer implements ClassDependenciesAnalyzer {
    private final ClassDependenciesAnalyzer analyzer;
    private final Cache<HashCode, ClassAnalysis> cache;

    public CachingClassDependenciesAnalyzer(ClassDependenciesAnalyzer analyzer, Cache<HashCode, ClassAnalysis> cache) {
        this.analyzer = analyzer;
        this.cache = cache;
    }

    @Override
    public ClassAnalysis getClassAnalysis(final HashCode classFileHash, final FileTreeElement classFile) {
        return cache.get(classFileHash, () -> analyzer.getClassAnalysis(classFileHash, classFile));
    }
}
