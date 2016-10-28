/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.Lists;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DefaultClasspathSnapshotter extends AbstractFileCollectionSnapshotter implements ClasspathSnapshotter {
    private static final Comparator<DefaultFileDetails> FILE_DETAILS_COMPARATOR = new Comparator<DefaultFileDetails>() {
        @Override
        public int compare(DefaultFileDetails o1, DefaultFileDetails o2) {
            return o1.getPath().compareTo(o2.getPath());
        }
    };

    public DefaultClasspathSnapshotter(FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory) {
        super(hasher, stringInterner, fileSystem, directoryFileTreeFactory);
    }

    @Override
    public Class<? extends FileCollectionSnapshotter> getRegisteredType() {
        return ClasspathSnapshotter.class;
    }

    @Override
    protected void visitTreeOrBackingFile(FileTreeInternal fileTree, List<DefaultFileDetails> fileTreeElements) {
        // Sort non-root elements as their order is not important
        List<DefaultFileDetails> subElements = Lists.newArrayList();
        super.visitTreeOrBackingFile(fileTree, subElements);
        Collections.sort(subElements, FILE_DETAILS_COMPARATOR);
        fileTreeElements.addAll(subElements);
    }

    @Override
    protected void visitDirectoryTree(DirectoryFileTree directoryTree, List<DefaultFileDetails> fileTreeElements) {
        // Sort non-root elements as their order is not important
        List<DefaultFileDetails> subElements = Lists.newArrayList();
        super.visitDirectoryTree(directoryTree, subElements);
        Collections.sort(subElements, FILE_DETAILS_COMPARATOR);
        fileTreeElements.addAll(subElements);
    }
}
