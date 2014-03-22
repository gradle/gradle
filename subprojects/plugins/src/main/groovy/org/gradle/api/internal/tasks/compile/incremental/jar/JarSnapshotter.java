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

import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependencyInfo;

import java.util.HashMap;
import java.util.Map;

public class JarSnapshotter {

    private final ClassSnapshotter classSnapshotFactory;

    public JarSnapshotter(ClassSnapshotter classSnapshotFactory) {
        this.classSnapshotFactory = classSnapshotFactory;
    }

    JarSnapshot createSnapshot(FileTree archivedClasses, final ClassDependencyInfo dependencyInfo) {
        final Map<String, ClassSnapshot> hashes = new HashMap<String, ClassSnapshot>();
        archivedClasses.visit(new FileVisitor() {
            public void visitDir(FileVisitDetails dirDetails) {}

            public void visitFile(FileVisitDetails fileDetails) {
                String className = fileDetails.getPath().replaceAll("/", ".").replaceAll("\\.class$", "");
                hashes.put(className, classSnapshotFactory.createSnapshot(className, fileDetails.getFile(), dependencyInfo));
            }
        });
        return new JarSnapshot(hashes);
    }
}