/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import java.io.File;
import java.util.Iterator;
import java.util.Set;

/**
 * A base class for {@link org.gradle.api.file.FileCollection} implementations that are not composed from other file collections.
 */
public abstract class AbstractOpaqueFileCollection extends AbstractFileCollection {
    public AbstractOpaqueFileCollection() {
    }

    public AbstractOpaqueFileCollection(Factory<PatternSet> patternSetFactory) {
        super(patternSetFactory);
    }

    /**
     * This is final - override {@link #getIntrinsicFiles()} instead.
     */
    @Override
    public final Set<File> getFiles() {
        return getIntrinsicFiles();
    }

    /**
     * This is final - override {@link #getIntrinsicFiles()} instead.
     */
    @Override
    public final Iterator<File> iterator() {
        return getIntrinsicFiles().iterator();
    }

    @Override
    protected void visitContents(FileCollectionStructureVisitor visitor) {
        visitor.visitCollection(OTHER, this);
    }

    abstract protected Set<File> getIntrinsicFiles();
}
