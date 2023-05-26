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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.PropertySpec;
import org.gradle.internal.execution.model.InputNormalizer;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.IgnoredPathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.NameOnlyFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.RelativePathFingerprintingStrategy;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;

@NonNullApi
public class SnapshotInputsResultFilePropertiesVisitState implements SnapshotTaskInputsBuildOperationType.Result.VisitState, FileSystemSnapshotHierarchyVisitor {
    private static final Map<FileNormalizer, String> FINGERPRINTING_STRATEGIES_BY_NORMALIZER = ImmutableMap.<FileNormalizer, String>builder()
        .put(InputNormalizer.RUNTIME_CLASSPATH, FingerprintingStrategy.CLASSPATH_IDENTIFIER)
        .put(InputNormalizer.COMPILE_CLASSPATH, FingerprintingStrategy.COMPILE_CLASSPATH_IDENTIFIER)
        .put(InputNormalizer.ABSOLUTE_PATH, AbsolutePathFingerprintingStrategy.IDENTIFIER)
        .put(InputNormalizer.RELATIVE_PATH, RelativePathFingerprintingStrategy.IDENTIFIER)
        .put(InputNormalizer.NAME_ONLY, NameOnlyFingerprintingStrategy.IDENTIFIER)
        .put(InputNormalizer.IGNORE_PATH, IgnoredPathFingerprintingStrategy.IDENTIFIER)
        .build();

    public static void visitInputFileProperties(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties,  SnapshotTaskInputsBuildOperationType.Result.InputFilePropertyVisitor visitor, Set<InputFilePropertySpec> inputFilePropertySpecs) {
        ImmutableMap<String, InputFilePropertySpec> propertySpecsByName = Maps.uniqueIndex(inputFilePropertySpecs, PropertySpec::getPropertyName);
        SnapshotInputsResultFilePropertiesVisitState state = new SnapshotInputsResultFilePropertiesVisitState(visitor, propertySpecsByName);
        for (Map.Entry<String, CurrentFileCollectionFingerprint> entry : inputFileProperties.entrySet()) {
            CurrentFileCollectionFingerprint fingerprint = entry.getValue();

            state.propertyName = entry.getKey();
            state.propertyHash = fingerprint.getHash();
            state.fingerprints = fingerprint.getFingerprints();

            visitor.preProperty(state);
            fingerprint.getSnapshot().accept(state);
            visitor.postProperty();
        }
    }

    private final SnapshotTaskInputsBuildOperationType.Result.InputFilePropertyVisitor visitor;
    private final Map<String, InputFilePropertySpec> propertySpecsByName;
    private final Deque<DirectorySnapshot> unvisitedDirectories = new ArrayDeque<>();

    Map<String, FileSystemLocationFingerprint> fingerprints;
    String propertyName;
    HashCode propertyHash;
    String name;
    String path;
    HashCode hash;
    int depth;

    public SnapshotInputsResultFilePropertiesVisitState(SnapshotTaskInputsBuildOperationType.Result.InputFilePropertyVisitor visitor, Map<String, InputFilePropertySpec> propertySpecsByName) {
        this.visitor = visitor;
        this.propertySpecsByName = propertySpecsByName;
    }

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
    @Deprecated
    public String getPropertyNormalizationStrategyName() {
        InputFilePropertySpec propertySpec = propertySpec(propertyName);
        FileNormalizer normalizer = propertySpec.getNormalizer();
        String normalizationStrategy = FINGERPRINTING_STRATEGIES_BY_NORMALIZER.get(normalizer);
        if (normalizationStrategy == null) {
            throw new IllegalStateException("No strategy name for " + normalizer);
        }
        return normalizationStrategy;
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
            visitor.preRoot(this);
        }

        FileSystemLocationFingerprint fingerprint = fingerprints.get(path);
        if (fingerprint == null) {
            // This directory is not part of the fingerprint.
            // Store it to visit later if it contains anything that was fingerprinted
            unvisitedDirectories.add(physicalSnapshot);
        } else {
            visitUnvisitedDirectories();
            visitor.preDirectory(this);
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
            visitor.preRoot(this);
        }

        visitor.file(this);

        if (isRoot) {
            visitor.postRoot();
        }
        return SnapshotVisitResult.CONTINUE;
    }

    @Override
    public void leaveDirectory(DirectorySnapshot directorySnapshot) {
        DirectorySnapshot lastUnvisitedDirectory = unvisitedDirectories.pollLast();
        if (lastUnvisitedDirectory == null) {
            visitor.postDirectory();
        }

        if (--depth == 0) {
            visitor.postRoot();
        }
    }

    private void visitUnvisitedDirectories() {
        DirectorySnapshot unvisited;
        while ((unvisited = unvisitedDirectories.poll()) != null) {
            visitor.preDirectory(new DirectoryVisitState(unvisited, this));
        }
    }

    private InputFilePropertySpec propertySpec(String propertyName) {
        InputFilePropertySpec propertySpec = propertySpecsByName.get(propertyName);
        if (propertySpec == null) {
            throw new IllegalStateException("Unknown input property '" + propertyName + "' (known: " + propertySpecsByName.keySet() + ")");
        }
        return propertySpec;
    }

    private static class DirectoryVisitState implements SnapshotTaskInputsBuildOperationType.Result.VisitState {
        private final SnapshotTaskInputsBuildOperationType.Result.VisitState delegate;
        private final DirectorySnapshot directorySnapshot;

        public DirectoryVisitState(DirectorySnapshot unvisited, SnapshotTaskInputsBuildOperationType.Result.VisitState delegate) {
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

        @SuppressWarnings("deprecation")
        @Override
        public String getPropertyNormalizationStrategyName() {
            return delegate.getPropertyNormalizationStrategyName();
        }

        @Override
        public Set<String> getPropertyAttributes() {
            return delegate.getPropertyAttributes();
        }
    }
}
