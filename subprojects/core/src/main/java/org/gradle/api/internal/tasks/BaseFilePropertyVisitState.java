/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks;

import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.operations.execution.FilePropertyVisitor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;

@NonNullApi
public abstract class BaseFilePropertyVisitState implements FilePropertyVisitor.VisitState, FileSystemSnapshotHierarchyVisitor {
    private final Map<String, InputFilePropertySpec> propertySpecsByName;
    private final Deque<DirectorySnapshot> unvisitedDirectories = new ArrayDeque<>();

    Map<String, FileSystemLocationFingerprint> fingerprints;
    String propertyName;
    HashCode propertyHash;
    String name;
    String path;
    HashCode hash;
    int depth;

    protected BaseFilePropertyVisitState(Map<String, InputFilePropertySpec> propertySpecsByName) {
        this.propertySpecsByName = propertySpecsByName;
    }

    protected abstract void preRoot();
    protected abstract void postRoot();
    protected abstract void preDirectory();
    protected abstract void preUnvisitedDirectory(DirectorySnapshot unvisited);
    protected abstract void postDirectory();
    protected abstract void file();

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public byte[] getPropertyHashBytes() {
        return propertyHash.toByteArray();
    }

    @Override
    public Set<String> getPropertyAttributes() {
        InputFilePropertySpec propertySpec = propertySpec(propertyName);
        return ImmutableSortedSet.of(
            SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute.fromNormalizer(propertySpec.getNormalizer()).name(),
            SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute.from(propertySpec.getDirectorySensitivity()).name(),
            SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute.from(propertySpec.getLineEndingNormalization()).name()
        );
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public byte[] getHashBytes() {
        return hash.toByteArray();
    }

    @Override
    public void enterDirectory(DirectorySnapshot physicalSnapshot) {
        this.path = physicalSnapshot.getAbsolutePath();
        this.name = physicalSnapshot.getName();
        this.hash = null;

        if (depth++ == 0) {
            preRoot();
        }

        FileSystemLocationFingerprint fingerprint = fingerprints.get(path);
        if (fingerprint == null) {
            // This directory is not part of the fingerprint.
            // Store it to visit later if it contains anything that was fingerprinted
            unvisitedDirectories.add(physicalSnapshot);
        } else {
            visitUnvisitedDirectories();
            preDirectory();
        }
    }

    @Override
    public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot) {
        if (snapshot.getType() == FileType.Directory) {
            return SnapshotVisitResult.CONTINUE;
        }

        FileSystemLocationFingerprint fingerprint = fingerprints.get(snapshot.getAbsolutePath());
        if (fingerprint == null) {
            return SnapshotVisitResult.CONTINUE;
        }

        visitUnvisitedDirectories();

        this.path = snapshot.getAbsolutePath();
        this.name = snapshot.getName();
        this.hash = fingerprint.getNormalizedContentHash();

        boolean isRoot = depth == 0;
        if (isRoot) {
            preRoot();
        }

        file();

        if (isRoot) {
            postRoot();
        }
        return SnapshotVisitResult.CONTINUE;
    }

    @Override
    public void leaveDirectory(DirectorySnapshot directorySnapshot) {
        DirectorySnapshot lastUnvisitedDirectory = unvisitedDirectories.pollLast();
        if (lastUnvisitedDirectory == null) {
            postDirectory();
        }

        if (--depth == 0) {
            postRoot();
        }
    }

    private void visitUnvisitedDirectories() {
        DirectorySnapshot unvisited;
        while ((unvisited = unvisitedDirectories.poll()) != null) {
            preUnvisitedDirectory(unvisited);
        }
    }

    protected InputFilePropertySpec propertySpec(String propertyName) {
        InputFilePropertySpec propertySpec = propertySpecsByName.get(propertyName);
        if (propertySpec == null) {
            throw new IllegalStateException("Unknown input property '" + propertyName + "' (known: " + propertySpecsByName.keySet() + ")");
        }
        return propertySpec;
    }

    protected static class DirectoryVisitState<T extends FilePropertyVisitor.VisitState> implements FilePropertyVisitor.VisitState {
        protected final T delegate;
        private final DirectorySnapshot directorySnapshot;

        public DirectoryVisitState(DirectorySnapshot unvisited, T delegate) {
            this.directorySnapshot = unvisited;
            this.delegate = delegate;
        }

        @Override
        public String getPath() {
            return directorySnapshot.getAbsolutePath();
        }

        @Override
        public String getName() {
            return directorySnapshot.getName();
        }

        @Override
        public byte[] getHashBytes() {
            throw new UnsupportedOperationException("Cannot query hash for directories");
        }

        @Override
        public String getPropertyName() {
            return delegate.getPropertyName();
        }

        @Override
        public byte[] getPropertyHashBytes() {
            return delegate.getPropertyHashBytes();
        }

        @Override
        public Set<String> getPropertyAttributes() {
            return delegate.getPropertyAttributes();
        }
    }
}
