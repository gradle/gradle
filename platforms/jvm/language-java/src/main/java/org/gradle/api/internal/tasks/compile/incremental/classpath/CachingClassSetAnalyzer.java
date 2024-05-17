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

package org.gradle.api.internal.tasks.compile.incremental.classpath;

import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.cache.Cache;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;

public class CachingClassSetAnalyzer implements ClassSetAnalyzer {

    private final ClassSetAnalyzer delegate;
    private final FileSystemAccess fileSystemAccess;
    private final Cache<HashCode, ClassSetAnalysisData> cache;

    public CachingClassSetAnalyzer(ClassSetAnalyzer delegate,
                                   FileSystemAccess fileSystemAccess,
                                   Cache<HashCode, ClassSetAnalysisData> cache) {
        this.delegate = delegate;
        this.fileSystemAccess = fileSystemAccess;
        this.cache = cache;
    }

    @Override
    public ClassSetAnalysisData analyzeClasspathEntry(final File classpathEntry) {
        FileSystemLocationSnapshot snapshot = fileSystemAccess.read(classpathEntry.getAbsolutePath());
        return cache.get(snapshot.getHash(), hash -> delegate.analyzeClasspathEntry(classpathEntry));
    }

    @Override
    public ClassSetAnalysisData analyzeOutputFolder(File outputFolder) {
        return delegate.analyzeOutputFolder(outputFolder);
    }
}
