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
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker;
import org.gradle.internal.hash.ChecksumService;

import java.io.File;
import java.util.Set;

/**
 * A file store that stores items grouped by some provided function over the key and an SHA1 hash of the value. This means that files are only ever added and never modified once added, so a resource from this store can be used without locking. Locking is required to add entries.
 */
public class GroupedAndNamedUniqueFileStore<K> implements FileStore<K>, FileStoreSearcher<K> {

    protected static final int NUMBER_OF_CHECKSUM_DIRS = 1;

    private final PathKeyFileStore delegate;
    private final TemporaryFileProvider temporaryFileProvider;
    private final Grouper<K> grouper;
    private final Namer<K> namer;
    private final FileAccessTracker checksumDirAccessTracker;
    private final File baseDir;
    private final ChecksumService checksumService;

    public GroupedAndNamedUniqueFileStore(File baseDir, TemporaryFileProvider temporaryFileProvider, FileAccessTimeJournal fileAccessTimeJournal, Grouper<K> grouper, Namer<K> namer, ChecksumService checksumService) {
        this.delegate = new UniquePathKeyFileStore(checksumService, baseDir);
        this.temporaryFileProvider = temporaryFileProvider;
        this.grouper = grouper;
        this.namer = namer;
        this.checksumDirAccessTracker = new SingleDepthFileAccessTracker(fileAccessTimeJournal, baseDir, grouper.getNumberOfGroupingDirs() + NUMBER_OF_CHECKSUM_DIRS);
        this.baseDir = baseDir;
        this.checksumService = checksumService;
    }

    @Override
    public LocallyAvailableResource move(K key, File source) {
        return markAccessed(delegate.move(toPath(key, getChecksum(source)), source));
    }

    @Override
    public Set<? extends LocallyAvailableResource> search(K key) {
        return delegate.search(toPath(key, "*"));
    }

    public FileAccessTracker getFileAccessTracker() {
        return checksumDirAccessTracker;
    }

    private String toPath(K key, String checksumPart) {
        String group = grouper.determineGroup(key);
        String name = namer.determineName(key);

        return group + "/" + stripLeadingZeros(checksumPart) + "/" + name;
    }

    // We do this for backwards compatibility: older Gradle versions
    // used to store files in the binary cache with a checksum which removes
    // the leading zeros
    private String stripLeadingZeros(String checksumPart) {
        if (checksumPart.charAt(0) == '0') {
            int i = 1;
            while (checksumPart.charAt(i) == '0') {
                i++;
            }
            return checksumPart.substring(i);
        }
        return checksumPart;
    }

    private String getChecksum(File contentFile) {
        return checksumService.sha1(contentFile).toString();
    }

    private File getTempFile() {
        return temporaryFileProvider.createTemporaryFile("filestore", "bin");
    }

    public File whereIs(K key, String checksum) {
        return new File(baseDir, toPath(key, checksum));
    }

    @Override
    public LocallyAvailableResource add(K key, Action<File> addAction) {
        //We cannot just delegate to the add method as we need the file content for checksum calculation here
        //and reexecuting the action isn't acceptable
        final File tempFile = getTempFile();
        addAction.execute(tempFile);
        final String groupedAndNamedKey = toPath(key, getChecksum(tempFile));
        return markAccessed(delegate.move(groupedAndNamedKey, tempFile));
    }

    private LocallyAvailableResource markAccessed(LocallyAvailableResource resource) {
        checksumDirAccessTracker.markAccessed(resource.getFile());
        return resource;
    }

    public interface Grouper<K> {
        String determineGroup(K key);
        int getNumberOfGroupingDirs();
    }

}
