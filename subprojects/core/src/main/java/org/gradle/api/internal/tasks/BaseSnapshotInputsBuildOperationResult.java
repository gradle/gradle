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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import org.gradle.api.NonNullApi;
import org.gradle.caching.BuildCacheKey;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.InputExecutionState;
import org.gradle.internal.execution.model.InputNormalizer;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.gradle.operations.execution.FilePropertyVisitor;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@NonNullApi
public abstract class BaseSnapshotInputsBuildOperationResult implements CustomOperationTraceSerialization {

    @VisibleForTesting
    final CachingState cachingState;

    public BaseSnapshotInputsBuildOperationResult(CachingState cachingState) {
        this.cachingState = cachingState;
    }

    protected abstract Map<String, Object> fileProperties();

    @Nullable
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

    @Nullable
    public byte[] getClassLoaderHashBytes() {
        return getBeforeExecutionState()
            .map(InputExecutionState::getImplementation)
            .map(BaseSnapshotInputsBuildOperationResult::getClassLoaderHashBytesOrNull)
            .orElse(null);
    }

    @Nullable
    public String getImplementationClassName() {
        return getBeforeExecutionState()
            .map(InputExecutionState::getImplementation)
            .map(ImplementationSnapshot::getClassIdentifier)
            .orElse(null);
    }

    @Nullable
    public List<byte[]> getActionClassLoaderHashesBytes() {
        return getBeforeExecutionState()
            .map(BeforeExecutionState::getAdditionalImplementations)
            .filter(additionalImplementation -> !additionalImplementation.isEmpty())
            .map(additionalImplementations -> additionalImplementations.stream()
                .map(BaseSnapshotInputsBuildOperationResult::getClassLoaderHashBytesOrNull) // preserve nulls
                .collect(Collectors.toList()))
            .orElse(null);
    }

    @Nullable
    private static byte[] getClassLoaderHashBytesOrNull(ImplementationSnapshot implementation) {
        HashCode hash = implementation.getClassLoaderHash();
        return hash == null ? null : hash.toByteArray();
    }


    @Nullable
    public List<String> getActionClassNames() {
        return getBeforeExecutionState()
            .map(BeforeExecutionState::getAdditionalImplementations)
            .filter(additionalImplementations -> !additionalImplementations.isEmpty())
            .map(additionalImplementations -> additionalImplementations.stream()
                .map(ImplementationSnapshot::getClassIdentifier)
                .collect(Collectors.toList())
            )
            .orElse(null);
    }

    @Nullable
    public List<String> getOutputPropertyNames() {
        return getBeforeExecutionState()
            .map(BeforeExecutionState::getOutputFileLocationSnapshots)
            .map(ImmutableSortedMap::keySet)
            .filter(outputPropertyNames -> !outputPropertyNames.isEmpty())
            .map(ImmutableSet::asList)
            .orElse(null);
    }

    @Nullable
    public byte[] getHashBytes() {
        return getKey()
            .map(BuildCacheKey::toByteArray)
            .orElse(null);
    }

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
        model.put("implementationClassName", getImplementationClassName());

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

    protected Optional<BeforeExecutionState> getBeforeExecutionState() {
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

    @UsedByScanPlugin("The value names are used as part of build scan data and cannot be changed - new values can be added")
    enum FilePropertyAttribute {

        // When adding new values, be sure to comment which Gradle version started emitting the attribute.
        // Additionally, indicate any other relevant constraints with regard to other attributes or changes.

        // Every property has exactly one of the following
        FINGERPRINTING_STRATEGY_ABSOLUTE_PATH,
        FINGERPRINTING_STRATEGY_NAME_ONLY,
        FINGERPRINTING_STRATEGY_RELATIVE_PATH,
        FINGERPRINTING_STRATEGY_IGNORED_PATH,
        FINGERPRINTING_STRATEGY_CLASSPATH,
        FINGERPRINTING_STRATEGY_COMPILE_CLASSPATH,

        // Every property has exactly one of the following
        DIRECTORY_SENSITIVITY_DEFAULT,
        DIRECTORY_SENSITIVITY_IGNORE_DIRECTORIES,

        // Every property has exactly one of the following
        LINE_ENDING_SENSITIVITY_DEFAULT,
        LINE_ENDING_SENSITIVITY_NORMALIZE_LINE_ENDINGS;

        private static final Map<FileNormalizer, SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute> BY_NORMALIZER = ImmutableMap.<FileNormalizer, SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute>builder()
            .put(InputNormalizer.RUNTIME_CLASSPATH, FINGERPRINTING_STRATEGY_CLASSPATH)
            .put(InputNormalizer.COMPILE_CLASSPATH, FINGERPRINTING_STRATEGY_COMPILE_CLASSPATH)
            .put(InputNormalizer.ABSOLUTE_PATH, FINGERPRINTING_STRATEGY_ABSOLUTE_PATH)
            .put(InputNormalizer.RELATIVE_PATH, FINGERPRINTING_STRATEGY_RELATIVE_PATH)
            .put(InputNormalizer.NAME_ONLY, FINGERPRINTING_STRATEGY_NAME_ONLY)
            .put(InputNormalizer.IGNORE_PATH, FINGERPRINTING_STRATEGY_IGNORED_PATH)
            .build();

        private static final Map<DirectorySensitivity, SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute> BY_DIRECTORY_SENSITIVITY = Maps.immutableEnumMap(ImmutableMap.<DirectorySensitivity, SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute>builder()
            .put(DirectorySensitivity.DEFAULT, DIRECTORY_SENSITIVITY_DEFAULT)
            .put(DirectorySensitivity.IGNORE_DIRECTORIES, DIRECTORY_SENSITIVITY_IGNORE_DIRECTORIES)
            .build());

        private static final Map<LineEndingSensitivity, SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute> BY_LINE_ENDING_SENSITIVITY = Maps.immutableEnumMap(ImmutableMap.<LineEndingSensitivity, SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute>builder()
            .put(LineEndingSensitivity.DEFAULT, LINE_ENDING_SENSITIVITY_DEFAULT)
            .put(LineEndingSensitivity.NORMALIZE_LINE_ENDINGS, LINE_ENDING_SENSITIVITY_NORMALIZE_LINE_ENDINGS)
            .build());

        private static <T> SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute findFor(T value, Map<T, SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute> mapping) {
            SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute attribute = mapping.get(value);
            if (attribute == null) {
                throw new IllegalStateException("Did not find property attribute mapping for '" + value + "' (from: " + mapping.keySet() + ")");
            }

            return attribute;
        }

        static SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute fromNormalizer(FileNormalizer normalizer) {
            return findFor(normalizer, BY_NORMALIZER);
        }

        static SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute from(DirectorySensitivity directorySensitivity) {
            return findFor(directorySensitivity, BY_DIRECTORY_SENSITIVITY);
        }

        static SnapshotTaskInputsBuildOperationResult.FilePropertyAttribute from(LineEndingSensitivity lineEndingSensitivity) {
            return findFor(lineEndingSensitivity, BY_LINE_ENDING_SENSITIVITY);
        }

    }

    protected abstract static class BaseFilePropertyCollectingVisitor<STATE extends FilePropertyVisitor.VisitState> {

        private final Map<String, Object> fileProperties;
        Property property;
        final Deque<DirEntry> dirStack;

        public BaseFilePropertyCollectingVisitor() {
            this.fileProperties = new TreeMap<>();
            this.dirStack = new ArrayDeque<>();
        }

        public Map<String, Object> getFileProperties() {
            return fileProperties;
        }

        protected abstract Property createProperty(STATE state);

        protected static class Property {

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

        public abstract static class Entry {

            private final String path;

            public Entry(String path) {
                this.path = path;
            }

            public String getPath() {
                return path;
            }

        }

        static class FileEntry extends Entry {

            private final String hash;

            FileEntry(String path, String hash) {
                super(path);
                this.hash = hash;
            }

            public String getHash() {
                return hash;
            }
        }

        static class DirEntry extends Entry {

            private final List<Entry> children = new ArrayList<>();

            DirEntry(String path) {
                super(path);
            }

            public Collection<Entry> getChildren() {
                return children;
            }
        }

        public void preProperty(STATE state) {
            property = createProperty(state);
            fileProperties.put(state.getPropertyName(), property);
        }

        public void preRoot(STATE state) {

        }

        public void preDirectory(STATE state) {
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

        public void file(STATE state) {
            boolean isRoot = dirStack.isEmpty();
            FileEntry file = new FileEntry(isRoot ? state.getPath() : state.getName(), HashCode.fromBytes(state.getHashBytes()).toString());
            if (isRoot) {
                property.roots.add(file);
            } else {
                //noinspection ConstantConditions
                dirStack.peek().children.add(file);
            }
        }

        public void postDirectory() {
            dirStack.pop();
        }

        public void postRoot() {

        }

        public void postProperty() {

        }
    }
}
