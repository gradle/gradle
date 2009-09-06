/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file;

import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.tasks.StopExecutionException;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import groovy.lang.Closure;

public abstract class AbstractFileTree extends AbstractFileCollection implements FileTree {
    public Set<File> getFiles() {
        final Set<File> files = new LinkedHashSet<File>();
        visit(new FileVisitor() {
            public void visitDir(FileVisitDetails dirDetails) {
            }

            public void visitFile(FileVisitDetails fileDetails) {
                files.add(fileDetails.getFile());
            }
        });
        return files;
    }

    public FileCollection stopExecutionIfEmpty() {
        final AtomicBoolean found = new AtomicBoolean();
        visit(new FileVisitor() {
            public void visitDir(FileVisitDetails dirDetails) {
            }

            public void visitFile(FileVisitDetails fileDetails) {
                found.set(true);
                fileDetails.stopVisiting();
            }
        });
        if (!found.get()) {
            throw new StopExecutionException(String.format("%s does not contain any files.", getCapDisplayName()));
        }
        return this;
    }

    @Override
    public FileTree getAsFileTree() {
        return this;
    }

    public FileTree plus(FileTree fileTree) {
        return new UnionFileTree(this, fileTree);
    }

    public FileTree visit(Closure closure) {
        return visit((FileVisitor) DefaultGroovyMethods.asType(closure, FileVisitor.class));
    }
}
