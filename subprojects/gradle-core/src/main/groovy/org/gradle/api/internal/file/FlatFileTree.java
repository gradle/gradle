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

import groovy.lang.Closure;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FlatFileTree extends AbstractFileTree {
    private final List<File> files;

    public FlatFileTree(File... files) {
        this.files = Arrays.asList(files);
    }

    @Override
    public String getDisplayName() {
        return "file tree";
    }

    public FileTree matching(Closure filterConfigClosure) {
        PatternSet patternSet = new PatternSet();
        ConfigureUtil.configure(filterConfigClosure, patternSet);
        return new FilteredFlatFileTree(this, patternSet.getAsSpec());
    }

    public FileTree matching(PatternFilterable patterns) {
        PatternSet patternSet = new PatternSet();
        patternSet.copyFrom(patterns);
        return new FilteredFlatFileTree(this, patternSet.getAsSpec());
    }

    @Override
    public Set<File> getFiles() {
        return Specs.filterIterable(files, new Spec<File>() {
            public boolean isSatisfiedBy(File element) {
                return element.exists();
            }
        });
    }

    public FileTree visit(FileVisitor visitor) {
        for (final File file : getFiles()) {
            visitor.visitFile(new FileVisitDetails() {
                public File getFile() {
                    return file;
                }

                public RelativePath getRelativePath() {
                    return new RelativePath(true, file.getName());
                }

                public void stopVisiting() {
                    throw new UnsupportedOperationException();
                }
            });
        }
        return this;
    }

    private static class FilteredFlatFileTree extends FlatFileTree {
        private final FileTree fileTree;
        private final Spec<RelativePath> spec;

        public FilteredFlatFileTree(FileTree fileTree, Spec<RelativePath> spec) {
            this.fileTree = fileTree;
            this.spec = spec;
        }

        @Override
        public Set<File> getFiles() {
            Set<File> matches = new LinkedHashSet<File>();
            for (File file : fileTree.getFiles()) {
                if (spec.isSatisfiedBy(new RelativePath(true, file.getName()))) {
                    matches.add(file);
                }
            }
            return matches;
        }
    }
}
