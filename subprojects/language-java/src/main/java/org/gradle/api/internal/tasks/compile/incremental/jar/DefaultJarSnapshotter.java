/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.jar;

import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassFilesAnalyzer;

import java.util.Map;

class DefaultJarSnapshotter {

    private final FileHasher hasher;
    private final ClassDependenciesAnalyzer analyzer;

    public DefaultJarSnapshotter(FileHasher hasher, ClassDependenciesAnalyzer analyzer) {
        this.hasher = hasher;
        this.analyzer = analyzer;
    }

    public JarSnapshot createSnapshot(HashCode hash, JarArchive jarArchive) {
        return createSnapshot(hash, jarArchive.contents, new ClassFilesAnalyzer(analyzer));
    }

    JarSnapshot createSnapshot(HashCode hash, FileTree classes, final ClassFilesAnalyzer analyzer) {
        final Map<String, HashCode> hashes = Maps.newHashMap();
        classes.visit(new FileVisitor() {
            public void visitDir(FileVisitDetails dirDetails) {
            }

            public void visitFile(FileVisitDetails fileDetails) {
                analyzer.visitFile(fileDetails);
                String className = fileDetails.getPath().replaceAll("/", ".").replaceAll("\\.class$", "");
                HashCode classHash = hasher.hash(fileDetails.getFile());
                hashes.put(className, classHash);
            }
        });

        return new JarSnapshot(new JarSnapshotData(hash, hashes, analyzer.getAnalysis()));
    }
}
