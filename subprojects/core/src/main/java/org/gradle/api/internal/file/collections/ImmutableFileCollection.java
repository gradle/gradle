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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.internal.file.PathToFileResolver;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ImmutableFileCollection extends AbstractFileCollection {
    private static final String DEFAULT_DISPLAY_NAME = "immutable file collection";
    private static final EmptyImmutableFileCollection EMPTY = new EmptyImmutableFileCollection();

    private final Set<Object> files;
    private final String displayName;
    private final PathToFileResolver resolver;

    public static ImmutableFileCollection of(Object... files) {
        return of(Arrays.asList(files));
    }

    public static ImmutableFileCollection of(Collection<?> files) {
        return usingResolver(new IdentityFileResolver(), files);
    }

    public static ImmutableFileCollection usingResolver(PathToFileResolver fileResolver, Object[] files) {
        return usingResolver(fileResolver, Arrays.asList(files));
    }

    public static ImmutableFileCollection usingResolver(PathToFileResolver fileResolver, Collection<?> files) {
        if (files.isEmpty()) {
            return EMPTY;
        }
        return new ImmutableFileCollection(DEFAULT_DISPLAY_NAME, fileResolver, files);
    }

    private ImmutableFileCollection(String displayName, PathToFileResolver fileResolver, Collection<?> files) {
        this.displayName = displayName;
        this.resolver = fileResolver;
        ImmutableSet.Builder<Object> filesBuilder = ImmutableSet.builder();
        if (files != null) {
            filesBuilder.addAll(files);
        }
        this.files = filesBuilder.build();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Set<File> getFiles() {
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(new IdentityFileResolver());
        FileCollectionResolveContext nested = context.push(resolver);
        nested.add(files);

        List<Set<File>> fileSets = new LinkedList<Set<File>>();
        int fileCount = 0;
        for (FileCollection collection : context.resolveAsFileCollections()) {
            Set<File> files = collection.getFiles();
            fileCount += files.size();
            fileSets.add(files);
        }
        Set<File> allFiles = new LinkedHashSet<File>(fileCount);
        for (Set<File> fileSet : fileSets) {
            allFiles.addAll(fileSet);
        }
        return allFiles;
    }

    @Override
    public FileCollection add(FileCollection collection) throws UnsupportedOperationException {
        throw new UnsupportedOperationException(String.format("%s does not allow modification.", getCapDisplayName()));
    }

    private static class EmptyImmutableFileCollection extends ImmutableFileCollection {
        public EmptyImmutableFileCollection() {
            super(DEFAULT_DISPLAY_NAME, null, null);
        }

        @Override
        public Set<File> getFiles() {
            return ImmutableSet.of();
        }
    }
}
