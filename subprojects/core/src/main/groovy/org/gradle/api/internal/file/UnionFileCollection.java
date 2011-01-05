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

import org.gradle.api.file.FileCollection;
import org.gradle.util.GUtil;

import java.util.*;

public class UnionFileCollection extends CompositeFileCollection {
    private final Set<FileCollection> sourceCollections;

    public UnionFileCollection(FileCollection... sourceCollections) {
        this(Arrays.asList(sourceCollections));
    }

    public UnionFileCollection(Iterable<? extends FileCollection> sourceCollections) {
        this.sourceCollections = GUtil.addToCollection(new LinkedHashSet<FileCollection>(), sourceCollections);
    }

    public String getDisplayName() {
        return "file collection";
    }

    @Override
    public FileCollection add(FileCollection collection) throws UnsupportedOperationException {
        sourceCollections.add(collection);
        return this;
    }

    @Override
    protected void addSourceCollections(Collection<FileCollection> sources) {
        sources.addAll(sourceCollections);
    }
}
