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

import org.gradle.api.internal.file.FileOperations;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class ClasspathJarFinder {
    private final FileOperations fileOperations;

    public ClasspathJarFinder(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    public Iterable<JarArchive> findJarArchives(Iterable<File> classpath) {
        List<JarArchive> out = new LinkedList<JarArchive>();
        for (File file : classpath) {
            if (file.getName().endsWith(".jar")) {
                out.add(new JarArchive(file, fileOperations.zipTree(file))); //TODO SF only create zip tree when needed, limit usages of JarArchive
            }
        }
        return out;
    }
}
