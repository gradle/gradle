/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.resource.local;

import org.gradle.api.Action;
import org.gradle.api.Namer;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.internal.hash.HashUtil;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A file store that stores items grouped by some provided function over the key and an SHA1 hash of the value. This means that files are only ever added and never modified once added, so a resource from this store can be used without locking. Locking is required to add entries.
 */
public class GroupedAndNamedUniqueFileStore<K> implements FileStore<K>, FileStoreSearcher<K> {

    private final PathKeyFileStore delegate;
    private final TemporaryFileProvider temporaryFileProvider;
    private final Grouper<K> grouper;
    private final Namer<K> namer;
    private final FileAccessTracker checksumDirAccessTracker;

    public GroupedAndNamedUniqueFileStore(File baseDir, TemporaryFileProvider temporaryFileProvider, Grouper<K> grouper, Namer<K> namer) {
        this.delegate = new UniquePathKeyFileStore(baseDir);
        this.temporaryFileProvider = temporaryFileProvider;
        this.grouper = grouper;
        this.namer = namer;
        this.checksumDirAccessTracker = new TouchingFileAccessTracker(baseDir, grouper.getDepth() + 1);
    }

    public LocallyAvailableResource move(K key, File source) {
        return delegate.move(toPath(key, getChecksum(source)), source);
    }

    public Set<? extends LocallyAvailableResource> search(K key) {
        Set<? extends LocallyAvailableResource> result = delegate.search(toPath(key, "*"));
        markAccessed(result);
        return result;
    }

    private void markAccessed(Set<? extends LocallyAvailableResource> resources) {
        List<File> files = CollectionUtils.collect(resources, new ArrayList<File>(resources.size()), LocallyAvailableResource.TO_FILE);
        checksumDirAccessTracker.markAccessed(files);
    }

    private String toPath(K key, String checksumPart) {
        String group = grouper.determineGroup(key);
        String name = namer.determineName(key);

        return group + "/" + checksumPart + "/" + name;
    }

    private String getChecksum(File contentFile) {
        return HashUtil.createHash(contentFile, "SHA1").asHexString();
    }

    private File getTempFile() {
        return temporaryFileProvider.createTemporaryFile("filestore", "bin");
    }

    public LocallyAvailableResource add(K key, Action<File> addAction) {
        //We cannot just delegate to the add method as we need the file content for checksum calculation here
        //and reexecuting the action isn't acceptable
        final File tempFile = getTempFile();
        addAction.execute(tempFile);
        final String groupedAndNamedKey = toPath(key, getChecksum(tempFile));
        return delegate.move(groupedAndNamedKey, tempFile);
    }

    public interface Grouper<K> {
        String determineGroup(K key);
        int getDepth();
    }

}
