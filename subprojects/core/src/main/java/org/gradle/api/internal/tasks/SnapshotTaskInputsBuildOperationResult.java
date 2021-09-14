/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.api.tasks.ClasspathNormalizer;
import org.gradle.api.tasks.CompileClasspathNormalizer;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.caching.BuildCacheKey;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.InputExecutionState;
import org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep;
import org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.AbsolutePathInputNormalizer;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.fingerprint.IgnoredPathInputNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.fingerprint.NameOnlyInputNormalizer;
import org.gradle.internal.fingerprint.RelativePathInputNormalizer;
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.IgnoredPathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.NameOnlyFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.RelativePathFingerprintingStrategy;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * This operation represents the work of analyzing the task's inputs plus the calculating the cache key.
 *
 * <p>
 * These two operations should be captured separately, but for historical reasons we don't yet do that.
 * To reproduce this composite operation we capture across executors by starting an operation
 * in {@link MarkSnapshottingInputsStartedStep} and finished in {@link MarkSnapshottingInputsFinishedStep}.
 * </p>
 */
public class SnapshotTaskInputsBuildOperationResult implements SnapshotTaskInputsBuildOperationType.Result, CustomOperationTraceSerialization {

    static final String FINGERPRINTING_STRATEGY_PREFIX = "FINGERPRINTING_STRATEGY_";
    private static final String DIRECTORY_SENSITIVITY_PREFIX = "DIRECTORY_SENSITIVITY_";
    private static final String LINE_ENDING_SENSITIVITY_PREFIX = "LINE_ENDING_SENSITIVITY_";
    private static final Map<Class<? extends FileNormalizer>, String> FINGERPRINTING_STRATEGIES_BY_NORMALIZER = ImmutableMap.<Class<? extends FileNormalizer>, String>builder()
        .put(ClasspathNormalizer.class, FingerprintingStrategy.CLASSPATH_IDENTIFIER)
        .put(CompileClasspathNormalizer.class, FingerprintingStrategy.COMPILE_CLASSPATH_IDENTIFIER)
        .put(AbsolutePathInputNormalizer.class, AbsolutePathFingerprintingStrategy.IDENTIFIER)
        .put(RelativePathInputNormalizer.class, RelativePathFingerprintingStrategy.IDENTIFIER)
        .put(NameOnlyInputNormalizer.class, NameOnlyFingerprintingStrategy.IDENTIFIER)
        .put(IgnoredPathInputNormalizer.class, IgnoredPathFingerprintingStrategy.IDENTIFIER)
        .build();

    @VisibleForTesting
    final CachingState cachingState;
    private final Map<String, Set<PropertyAttribute>> propertyAttributesByPropertyName;

    public SnapshotTaskInputsBuildOperationResult(CachingState cachingState, Set<InputFilePropertySpec> inputFileProperties) {
        this.cachingState = cachingState;
        Map<String, Set<PropertyAttribute>> propertyAttributesByPropertyName = new HashMap<>();
        for (InputFilePropertySpec inputFileProperty : inputFileProperties) {
            Set<PropertyAttribute> propertyAttributes = new HashSet<>();
            propertyAttributes.add(PropertyAttribute.fromNormalizerClass(inputFileProperty.getNormalizer()));
            propertyAttributes.add(PropertyAttribute.fromDirectorySensitivity(inputFileProperty.getDirectorySensitivity()));
            propertyAttributes.add(PropertyAttribute.fromLineEndingSensitivity(inputFileProperty.getLineEndingNormalization()));
            propertyAttributesByPropertyName.put(inputFileProperty.getPropertyName(), propertyAttributes);
        }
        this.propertyAttributesByPropertyName = ImmutableMap.copyOf(propertyAttributesByPropertyName);
    }

    enum PropertyAttribute {

        FINGERPRINTING_STRATEGY_ABSOLUTE_PATH,
        FINGERPRINTING_STRATEGY_NAME_ONLY,
        FINGERPRINTING_STRATEGY_RELATIVE_PATH,
        FINGERPRINTING_STRATEGY_IGNORED_PATH,
        FINGERPRINTING_STRATEGY_CLASSPATH,
        FINGERPRINTING_STRATEGY_COMPILE_CLASSPATH,

        DIRECTORY_SENSITIVITY_DEFAULT,
        DIRECTORY_SENSITIVITY_IGNORE_DIRECTORIES,

        LINE_ENDING_SENSITIVITY_DEFAULT,
        LINE_ENDING_SENSITIVITY_NORMALIZE_LINE_ENDINGS;

        static PropertyAttribute fromNormalizerClass(Class<? extends FileNormalizer> normalizerClass) {
            String fingerprintingStrategy = FINGERPRINTING_STRATEGIES_BY_NORMALIZER.get(normalizerClass);
            if (fingerprintingStrategy == null) {
                throw new IllegalStateException("Could not find a fingerprinting strategy for normalizer " + normalizerClass.getSimpleName());
            }
            return PropertyAttribute.valueOf(FINGERPRINTING_STRATEGY_PREFIX + fingerprintingStrategy);
        }

        static PropertyAttribute fromDirectorySensitivity(DirectorySensitivity directorySensitivity) {
            return PropertyAttribute.valueOf(DIRECTORY_SENSITIVITY_PREFIX + directorySensitivity.name());
        }

        static PropertyAttribute fromLineEndingSensitivity(LineEndingSensitivity lineEndingSensitivity) {
            return PropertyAttribute.valueOf(LINE_ENDING_SENSITIVITY_PREFIX + lineEndingSensitivity.name());
        }

    }

    @Override
    public Map<String, byte[]> getInputValueHashesBytes() {
        return getBeforeExecutionState()
            .map(InputExecutionState::getInputProperties)
            .filter(inputValueFingerprints -> !inputValueFingerprints.isEmpty())
            .map(inputValueFingerprints -> inputValueFingerprints.entrySet().stream()
                .collect(toLinkedHashMap(valueSnapshot -> {
                    Hasher hasher = Hashing.newHasher();
                    valueSnapshot.appendToHasher(hasher);
                    return hasher.hash().toByteArray();
                })))
            .orElse(null);
    }

    @NonNullApi
    private final class State implements VisitState, FileSystemSnapshotHierarchyVisitor {

        final InputFilePropertyVisitor visitor;
        private final List<DirectorySnapshot> replayDirectorySnapshotList = new ArrayList<>();
        private final Set<DirectorySnapshot> visitedPreDirectories = new HashSet<>();

        Map<String, FileSystemLocationFingerprint> fingerprints;
        String propertyName;
        HashCode propertyHash;
        String name;
        String path;
        HashCode hash;
        int depth;

        public State(InputFilePropertyVisitor visitor) {
            this.visitor = visitor;
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
            return propertyAttributesByPropertyName.get(propertyName).stream().map(Enum::name).sorted().collect(Collectors.toCollection(LinkedHashSet::new));
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
                // We are not interested in fingerprinting directories.
                // Do not visit it now (but maybe later if it contains at some point a file)
                replayDirectorySnapshotList.add(physicalSnapshot);
            } else {
                visitor.preDirectory(this);
                visitedPreDirectories.add(physicalSnapshot);
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
            // Replay parent directory stack in order, since we now have a file in the hierarchy
            clearAndMaybeReplayPreDirectories(true);

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
            FileSystemLocationFingerprint fingerprint = fingerprints.get(directorySnapshot.getAbsolutePath());
            clearAndMaybeReplayPreDirectories(fingerprint != null);
            if (visitedPreDirectories.remove(directorySnapshot)) {
                visitor.postDirectory();
            }
            if (--depth == 0) {
                visitor.postRoot();
            }
        }

        private void clearAndMaybeReplayPreDirectories(boolean replayPreDirectory) {
            Iterator<DirectorySnapshot> iterator = replayDirectorySnapshotList.iterator();
            while (iterator.hasNext()) {
                DirectorySnapshot directorySnapshot = iterator.next();
                if (replayPreDirectory) {
                    this.path = directorySnapshot.getAbsolutePath();
                    this.name = directorySnapshot.getName();
                    this.hash = null;
                    visitor.preDirectory(this);
                    visitedPreDirectories.add(directorySnapshot);
                }
                iterator.remove();
            }
        }

    }

    @Override
    public void visitInputFileProperties(final InputFilePropertyVisitor visitor) {
        getBeforeExecutionState()
            .map(BeforeExecutionState::getInputFileProperties)
            .ifPresent(inputFileProperties -> {
                State state = new State(visitor);
                for (Map.Entry<String, CurrentFileCollectionFingerprint> entry : inputFileProperties.entrySet()) {
                    CurrentFileCollectionFingerprint fingerprint = entry.getValue();

                    state.propertyName = entry.getKey();
                    state.propertyHash = fingerprint.getHash();
                    state.fingerprints = fingerprint.getFingerprints();

                    visitor.preProperty(state);
                    fingerprint.getSnapshot().accept(state);
                    visitor.postProperty();
                }
            });
    }

    @Override
    public byte[] getClassLoaderHashBytes() {
        return getBeforeExecutionState()
            .map(InputExecutionState::getImplementation)
            .map(ImplementationSnapshot::getClassLoaderHash)
            .map(HashCode::toByteArray)
            .orElse(null);
    }

    @Override
    public List<byte[]> getActionClassLoaderHashesBytes() {
        return getBeforeExecutionState()
            .map(BeforeExecutionState::getAdditionalImplementations)
            .filter(additionalImplementation -> !additionalImplementation.isEmpty())
            .map(additionalImplementations -> additionalImplementations.stream()
                .map(input -> input.getClassLoaderHash() == null ? null : input.getClassLoaderHash().toByteArray())
                .collect(Collectors.toList()))
            .orElse(null);
    }

    @Nullable
    @Override
    public List<String> getActionClassNames() {
        return getBeforeExecutionState()
            .map(BeforeExecutionState::getAdditionalImplementations)
            .filter(additionalImplementations -> !additionalImplementations.isEmpty())
            .map(additionalImplementations -> additionalImplementations.stream()
                .map(ImplementationSnapshot::getTypeName)
                .collect(Collectors.toList())
            )
            .orElse(null);
    }

    @Nullable
    @Override
    public List<String> getOutputPropertyNames() {
        return getBeforeExecutionState()
            .map(BeforeExecutionState::getOutputFileLocationSnapshots)
            .map(ImmutableSortedMap::keySet)
            .filter(outputPropertyNames -> !outputPropertyNames.isEmpty())
            .map(ImmutableSet::asList)
            .orElse(null);
    }

    @Override
    public byte[] getHashBytes() {
        return getKey()
            .map(BuildCacheKey::toByteArray)
            .orElse(null);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Object getCustomOperationTraceSerializableModel() {
        Map<String, Object> model = new TreeMap<>();

        List<byte[]> actionClassLoaderHashesBytes = getActionClassLoaderHashesBytes();
        if (actionClassLoaderHashesBytes != null) {
            List<String> actionClassloaderHashes = getActionClassLoaderHashesBytes().stream()
                .map(hash -> hash == null ? null : HashCode.fromBytes(hash).toString())
                .collect(Collectors.toList());
            model.put("actionClassLoaderHashes", actionClassloaderHashes);
        } else {
            model.put("actionClassLoaderHashes", null);
        }

        model.put("actionClassNames", getActionClassNames());

        byte[] hashBytes = getHashBytes();
        if (hashBytes != null) {
            model.put("hash", HashCode.fromBytes(hashBytes).toString());
        } else {
            model.put("hash", null);
        }

        byte[] classLoaderHashBytes = getClassLoaderHashBytes();
        if (classLoaderHashBytes != null) {
            model.put("classLoaderHash", HashCode.fromBytes(classLoaderHashBytes).toString());
        } else {
            model.put("classLoaderHash", null);
        }


        model.put("inputFileProperties", fileProperties());

        Map<String, byte[]> inputValueHashesBytes = getInputValueHashesBytes();
        if (inputValueHashesBytes != null) {
            Map<String, String> inputValueHashes = inputValueHashesBytes.entrySet().stream()
                .collect(toLinkedHashMap(value -> value == null ? null : HashCode.fromBytes(value).toString()));
            model.put("inputValueHashes", inputValueHashes);
        } else {
            model.put("inputValueHashes", null);
        }

        model.put("outputPropertyNames", getOutputPropertyNames());

        return model;
    }

    private static <K, V, U> Collector<Map.Entry<K, V>, ?, LinkedHashMap<K, U>> toLinkedHashMap(Function<? super V, ? extends U> valueMapper) {
        return Collectors.toMap(
            Map.Entry::getKey,
            entry -> valueMapper.apply(entry.getValue()),
            (a, b) -> b,
            LinkedHashMap::new
        );
    }

    protected Map<String, Object> fileProperties() {
        final Map<String, Object> fileProperties = new TreeMap<>();
        visitInputFileProperties(new InputFilePropertyVisitor() {
            Property property;
            final Deque<DirEntry> dirStack = new ArrayDeque<>();

            class Property {
                private final String hash;
                private final Set<String> attributes;
                private final List<Entry> roots = new ArrayList<>();

                public Property(String hash, Set<String> attributes) {
                    this.hash = hash;
                    this.attributes = attributes;
                }

                public String getHash() {
                    return hash;
                }

                public Set<String> getAttributes() {
                    return attributes;
                }

                public Collection<Entry> getRoots() {
                    return roots;
                }
            }

            abstract class Entry {
                private final String path;

                public Entry(String path) {
                    this.path = path;
                }

                public String getPath() {
                    return path;
                }

            }

            class FileEntry extends Entry {
                private final String hash;

                FileEntry(String path, String hash) {
                    super(path);
                    this.hash = hash;
                }

                public String getHash() {
                    return hash;
                }
            }

            class DirEntry extends Entry {
                private final List<Entry> children = new ArrayList<>();

                DirEntry(String path) {
                    super(path);
                }

                public Collection<Entry> getChildren() {
                    return children;
                }
            }

            @Override
            public void preProperty(VisitState state) {
                property = new Property(HashCode.fromBytes(state.getPropertyHashBytes()).toString(), state.getPropertyAttributes());
                fileProperties.put(state.getPropertyName(), property);
            }

            @Override
            public void preRoot(VisitState state) {

            }

            @Override
            public void preDirectory(VisitState state) {
                boolean isRoot = dirStack.isEmpty();
                DirEntry dir = new DirEntry(isRoot ? state.getPath() : state.getName());
                if (isRoot) {
                    property.roots.add(dir);
                } else {
                    //noinspection ConstantConditions
                    dirStack.peek().children.add(dir);
                }
                dirStack.push(dir);
            }

            @Override
            public void file(VisitState state) {
                boolean isRoot = dirStack.isEmpty();
                FileEntry file = new FileEntry(isRoot ? state.getPath() : state.getName(), HashCode.fromBytes(state.getHashBytes()).toString());
                if (isRoot) {
                    property.roots.add(file);
                } else {
                    //noinspection ConstantConditions
                    dirStack.peek().children.add(file);
                }
            }

            @Override
            public void postDirectory() {
                dirStack.pop();
            }

            @Override
            public void postRoot() {

            }

            @Override
            public void postProperty() {

            }
        });
        return fileProperties;
    }

    private Optional<BeforeExecutionState> getBeforeExecutionState() {
        return cachingState.fold(
            enabled -> Optional.of(enabled.getBeforeExecutionState()),
            CachingState.Disabled::getBeforeExecutionState
        );
    }

    private Optional<BuildCacheKey> getKey() {
        return cachingState.fold(
            enabled -> Optional.of(enabled.getKey()),
            CachingState.Disabled::getKey
        );
    }

}
