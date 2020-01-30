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
package org.gradle.api.internal.filestore;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.resource.local.FileStoreException;
import org.gradle.internal.resource.local.LocallyAvailableResource;

import java.io.File;
import java.util.Collection;
import java.util.Set;

public class TwoStageArtifactIdentifierFileStore implements ArtifactIdentifierFileStore {
    private final ArtifactIdentifierFileStore readOnlyStore;
    private final ArtifactIdentifierFileStore writableStore;
    private final FileAccessTracker fileAccessTracker;

    public TwoStageArtifactIdentifierFileStore(ArtifactIdentifierFileStore readOnlyStore, ArtifactIdentifierFileStore writableStore) {
        this.readOnlyStore = readOnlyStore;
        this.writableStore = writableStore;
        this.fileAccessTracker = new DelegatingFileAccessTracker();
    }

    @Override
    public File whereIs(ModuleComponentArtifactIdentifier artifactId, String checksum) {
        File file = writableStore.whereIs(artifactId, checksum);
        if (file.exists()) {
            return file;
        }
        return readOnlyStore.whereIs(artifactId, checksum);
    }

    @Override
    public FileAccessTracker getFileAccessTracker() {
        return fileAccessTracker;
    }

    @Override
    public LocallyAvailableResource move(ModuleComponentArtifactIdentifier key, File source) throws FileStoreException {
        return writableStore.move(key, source);
    }

    @Override
    public LocallyAvailableResource add(ModuleComponentArtifactIdentifier key, Action<File> addAction) throws FileStoreException {
        return writableStore.add(key, addAction);
    }

    @Override
    public Set<? extends LocallyAvailableResource> search(ModuleComponentArtifactIdentifier key) {
        ImmutableSet.Builder<LocallyAvailableResource> builder = ImmutableSet.builder();
        builder.addAll(writableStore.search(key));
        builder.addAll(readOnlyStore.search(key));
        return builder.build();
    }

    private class DelegatingFileAccessTracker implements FileAccessTracker {
        @Override
        public void markAccessed(File file) {
            readOnlyStore.getFileAccessTracker().markAccessed(file);
            writableStore.getFileAccessTracker().markAccessed(file);
        }

        @Override
        public void markAccessed(Collection<File> files) {
            readOnlyStore.getFileAccessTracker().markAccessed(files);
            writableStore.getFileAccessTracker().markAccessed(files);
        }
    }
}
