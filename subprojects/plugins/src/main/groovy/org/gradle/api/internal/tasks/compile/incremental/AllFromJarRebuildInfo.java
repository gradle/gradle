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

package org.gradle.api.internal.tasks.compile.incremental;

import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;

import java.util.HashSet;
import java.util.Set;

public class AllFromJarRebuildInfo extends DefaultRebuildInfo {
    AllFromJarRebuildInfo(JarArchive jarContents) {
        super(false, classesInJar(jarContents.contents));
    }

    private static Set<String> classesInJar(FileTree jarContents) {
        final Set<String> out = new HashSet<String>();
        jarContents.visit(new FileVisitor() {
            public void visitDir(FileVisitDetails dirDetails) {}
            public void visitFile(FileVisitDetails fileDetails) {
                out.add(fileDetails.getPath().replaceAll("/", ".").replaceAll("\\.class$", ""));
            }
        });
        return out;
    }
}