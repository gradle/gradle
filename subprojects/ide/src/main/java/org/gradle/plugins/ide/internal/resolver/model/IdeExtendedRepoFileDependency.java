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

package org.gradle.plugins.ide.internal.resolver.model;

import java.io.File;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class IdeExtendedRepoFileDependency extends IdeRepoFileDependency {
    private static final Comparator<File> FILE_COMPARATOR = new FileNameComparator();
    private final SortedSet<File> sourceFiles = new TreeSet<File>(FILE_COMPARATOR);
    private final SortedSet<File> javadocFiles = new TreeSet<File>(FILE_COMPARATOR);

    public IdeExtendedRepoFileDependency(File file) {
        super(file);
    }

    public File getSourceFile() {
        return sourceFiles.isEmpty() ? null : sourceFiles.first();
    }

    public Set<File> getSourceFiles() {
        return sourceFiles;
    }

    public void addSourceFile(File sourceFile) {
        sourceFiles.add(sourceFile);
    }

    public File getJavadocFile() {
        return javadocFiles.isEmpty() ? null : javadocFiles.first();
    }

    public Set<File> getJavadocFiles() {
        return javadocFiles;
    }

    public void addJavadocFile(File javadocFile) {
        javadocFiles.add(javadocFile);
    }

    private static class FileNameComparator implements Comparator<File> {
        public int compare(File o1, File o2) {
            return o1.getName().compareTo(o2.getName());
        }
    }
}
