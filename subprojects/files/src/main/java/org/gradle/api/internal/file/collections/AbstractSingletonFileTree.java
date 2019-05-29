/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.file.collections;

import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

public abstract class AbstractSingletonFileTree implements SingletonFileTree, PatternFilterableFileTree, FileSystemMirroringFileTree {
    private final PatternSet patterns;

    protected AbstractSingletonFileTree(PatternSet patterns) {
        this.patterns = patterns;
    }

    protected abstract FileVisitDetails createFileVisitDetails();

    @Override
    public void visit(FileVisitor visitor) {
        FileVisitDetails fileVisitDetails = createFileVisitDetails();
        if (patterns.isEmpty() || patterns.getAsSpec().isSatisfiedBy(fileVisitDetails)) {
            visitor.visitFile(fileVisitDetails);
        }
    }

    @Override
    public PatternSet getPatterns() {
        return patterns;
    }

    @Override
    public void visitTreeOrBackingFile(FileVisitor visitor) {
        visit(visitor);
    }

    protected PatternSet filterPatternSet(PatternFilterable patterns) {
        PatternSet patternSet = this.patterns.intersect();
        patternSet.copyFrom(patterns);
        return patternSet;
    }

    @Override
    public DirectoryFileTree getMirror() {
        return new FileBackedDirectoryFileTree(getFile()).filter(patterns);
    }
}
