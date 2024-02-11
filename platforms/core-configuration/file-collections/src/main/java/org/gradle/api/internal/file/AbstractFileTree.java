/*
 * Copyright 2010 the original author or authors.
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

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.MutableBoolean;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.gradle.util.internal.ConfigureUtil.configure;

public abstract class AbstractFileTree extends AbstractFileCollection implements FileTreeInternal {
    public AbstractFileTree() {
        super();
    }

    public AbstractFileTree(TaskDependencyFactory taskDependencyFactory, Factory<PatternSet> patternSetFactory) {
        super(taskDependencyFactory, patternSetFactory);
    }

    @Override
    public Set<File> getFiles() {
        final Set<File> files = new LinkedHashSet<File>();
        visit(new EmptyFileVisitor() {
            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                files.add(fileDetails.getFile());
            }
        });
        return files;
    }

    @Override
    public boolean isEmpty() {
        final MutableBoolean found = new MutableBoolean();
        visit(new EmptyFileVisitor() {
            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                found.set(true);
                fileDetails.stopVisiting();
            }
        });
        return !found.get();
    }

    @Override
    public FileTree matching(Closure filterConfigClosure) {
        return matching(configure(filterConfigClosure, patternSetFactory.create()));
    }

    @Override
    public FileTree matching(Action<? super PatternFilterable> filterConfigAction) {
        PatternSet patternSet = patternSetFactory.create();
        filterConfigAction.execute(patternSet);
        return matching(patternSet);
    }

    public Map<String, File> getAsMap() {
        final Map<String, File> map = new LinkedHashMap<String, File>();
        visit(new EmptyFileVisitor() {
            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                map.put(fileDetails.getRelativePath().getPathString(), fileDetails.getFile());
            }
        });
        return map;
    }

    @Override
    protected void addAsResourceCollection(Object builder, String nodeName) {
        new AntFileTreeBuilder(getAsMap()).addToAntBuilder(builder, nodeName);
    }

    @Override
    public FileTreeInternal getAsFileTree() {
        return this;
    }

    @Override
    public FileTree plus(FileTree fileTree) {
        return new UnionFileTree(taskDependencyFactory, this, Cast.cast(FileTreeInternal.class, fileTree));
    }

    @Override
    public FileTree visit(Closure closure) {
        return visit(fileVisitorFrom(closure));
    }

    static FileVisitor fileVisitorFrom(Closure closure) {
        return DefaultGroovyMethods.asType(closure, FileVisitor.class);
    }

    @Override
    public FileTree visit(final Action<? super FileVisitDetails> visitor) {
        return visit(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                visitor.execute(dirDetails);
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                visitor.execute(fileDetails);
            }
        });
    }
}
