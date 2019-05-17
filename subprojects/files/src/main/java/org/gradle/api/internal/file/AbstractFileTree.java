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
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.PatternSets;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.MutableBoolean;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.gradle.util.ConfigureUtil.configure;

public abstract class AbstractFileTree extends AbstractFileCollection implements FileTreeInternal {

    protected final Factory<PatternSet> patternSetFactory;

    public AbstractFileTree() {
        this(PatternSets.getNonCachingPatternSetFactory());
    }

    public AbstractFileTree(Factory<PatternSet> patternSetFactory) {
        this.patternSetFactory = patternSetFactory;
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

    @Override
    public FileTree matching(PatternFilterable patterns) {
        PatternSet patternSet = (PatternSet) patterns;
        return new FilteredFileTreeImpl(this, patternSet.getAsSpec());
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

    /**
     * Visits all the files of this tree.
     */
    protected boolean visitAll() {
        final MutableBoolean hasContent = new MutableBoolean();
        visit(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                dirDetails.getFile();
                hasContent.set(true);
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                fileDetails.getFile();
                hasContent.set(true);
            }
        });
        return hasContent.get();
    }

    @Override
    public FileTree getAsFileTree() {
        return this;
    }

    @Override
    public FileTree plus(FileTree fileTree) {
        return new UnionFileTree(this, Cast.cast(FileTreeInternal.class, fileTree));
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

    @Override
    public void visitTreeOrBackingFile(FileVisitor visitor) {
        visit(visitor);
    }

    @Override
    public void visitLeafCollections(FileCollectionLeafVisitor visitor) {
        visitor.visitGenericFileTree(this);
    }

    private static class FilteredFileTreeImpl extends AbstractFileTree {
        private final AbstractFileTree fileTree;
        private final Spec<FileTreeElement> spec;

        public FilteredFileTreeImpl(AbstractFileTree fileTree, Spec<FileTreeElement> spec) {
            this.fileTree = fileTree;
            this.spec = spec;
        }

        @Override
        public String getDisplayName() {
            return fileTree.getDisplayName();
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return fileTree.getBuildDependencies();
        }

        @Override
        public FileTree visit(final FileVisitor visitor) {
            fileTree.visit(new FileVisitor() {
                @Override
                public void visitDir(FileVisitDetails dirDetails) {
                    if (spec.isSatisfiedBy(dirDetails)) {
                        visitor.visitDir(dirDetails);
                    }
                }

                @Override
                public void visitFile(FileVisitDetails fileDetails) {
                    if (spec.isSatisfiedBy(fileDetails)) {
                        visitor.visitFile(fileDetails);
                    }
                }
            });
            return this;
        }

        @Override
        public void registerWatchPoints(FileSystemSubset.Builder builder) {
            // TODO: we aren't considering the filter
            fileTree.registerWatchPoints(builder);
        }

        @Override
        public void visitTreeOrBackingFile(FileVisitor visitor) {
            fileTree.visitTreeOrBackingFile(visitor);
        }
    }
}
