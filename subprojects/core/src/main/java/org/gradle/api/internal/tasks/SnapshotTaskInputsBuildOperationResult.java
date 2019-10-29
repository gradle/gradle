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
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.NonNullApi;
import org.gradle.caching.BuildCacheKey;
import org.gradle.internal.execution.caching.CachingInputs;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * This operation represents the work of analyzing the task's inputs plus the calculating the cache key.
 *
 * <p>
 * These two operations should be captured separately, but for historical reasons we don't yet do that.
 * To reproduce this composite operation we capture across executors by starting an operation
 * in {@link StartSnapshotTaskInputsBuildOperationTaskExecuter} and finished in {@link MarkSnapshottingInputsFinishedStep}.
 * </p>
 */
public class SnapshotTaskInputsBuildOperationResult implements SnapshotTaskInputsBuildOperationType.Result, CustomOperationTraceSerialization {

    @VisibleForTesting
    final CachingState cachingState;

    public SnapshotTaskInputsBuildOperationResult(CachingState cachingState) {
        this.cachingState = cachingState;
    }

    @Override
    public Map<String, byte[]> getInputValueHashesBytes() {
        return cachingState.getInputs()
            .map(new java.util.function.Function<CachingInputs, Map<String, byte[]>>() {
                @Nullable
                @Override
                public Map<String, byte[]> apply(CachingInputs cachingInputs) {
                    ImmutableSortedMap<String, HashCode> inputValueFingerprints = cachingInputs.getInputValueFingerprints();
                    if (inputValueFingerprints.isEmpty()) {
                        return null;
                    }
                    return Maps.transformValues(inputValueFingerprints, new Function<HashCode, byte[]>() {
                        @Override
                        public byte[] apply(HashCode input) {
                            return input.toByteArray();
                        }
                    });
                }
            })
            .orElse(null);
    }

    @NonNullApi
    private static class State implements VisitState, FileSystemSnapshotVisitor {

        final InputFilePropertyVisitor visitor;

        Map<String, FileSystemLocationFingerprint> fingerprints;
        String propertyName;
        HashCode propertyHash;
        String propertyNormalizationStrategyIdentifier;
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
        public String getPropertyNormalizationStrategyName() {
            return propertyNormalizationStrategyIdentifier;
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
        public boolean preVisitDirectory(CompleteDirectorySnapshot physicalSnapshot) {
            this.path = physicalSnapshot.getAbsolutePath();
            this.name = physicalSnapshot.getName();
            this.hash = null;

            if (depth++ == 0) {
                visitor.preRoot(this);
            }

            visitor.preDirectory(this);

            return true;
        }

        @Override
        public void visitFile(CompleteFileSystemLocationSnapshot snapshot) {
            this.path = snapshot.getAbsolutePath();
            this.name = snapshot.getName();

            FileSystemLocationFingerprint fingerprint = fingerprints.get(path);
            if (fingerprint == null) {
                return;
            }

            this.hash = fingerprint.getNormalizedContentHash();

            boolean isRoot = depth == 0;
            if (isRoot) {
                visitor.preRoot(this);
            }

            visitor.file(this);

            if (isRoot) {
                visitor.postRoot();
            }
        }

        @Override
        public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            visitor.postDirectory();
            if (--depth == 0) {
                visitor.postRoot();
            }
        }
    }

    @Override
    public void visitInputFileProperties(final InputFilePropertyVisitor visitor) {
        cachingState.getInputs().ifPresent(new Consumer<CachingInputs>() {
            @Override
            public void accept(CachingInputs inputs) {
                State state = new State(visitor);
                for (Map.Entry<String, CurrentFileCollectionFingerprint> entry : inputs.getInputFileFingerprints().entrySet()) {
                    CurrentFileCollectionFingerprint fingerprint = entry.getValue();

                    state.propertyName = entry.getKey();
                    state.propertyHash = fingerprint.getHash();
                    state.propertyNormalizationStrategyIdentifier = fingerprint.getStrategyIdentifier();
                    state.fingerprints = fingerprint.getFingerprints();

                    visitor.preProperty(state);
                    fingerprint.accept(state);
                    visitor.postProperty();
                }
            }
        });
    }

    @Nullable
    @Override
    public Set<String> getInputPropertiesLoadedByUnknownClassLoader() {
        return cachingState.getInputs()
            .map(new java.util.function.Function<CachingInputs, Set<String>>() {
                @Nullable
                @Override
                public Set<String> apply(CachingInputs inputs) {
                    ImmutableSortedSet<String> invalidInputProperties = inputs.getNonCacheableInputProperties();
                    if (invalidInputProperties.isEmpty()) {
                        return null;
                    }
                    return invalidInputProperties;
                }
            })
            .orElse(null);
    }


    @Override
    public byte[] getClassLoaderHashBytes() {
        return cachingState.getInputs()
            .map(new java.util.function.Function<CachingInputs, byte[]>() {
                @Nullable
                @Override
                public byte[] apply(CachingInputs inputs) {
                    ImplementationSnapshot implementation = inputs.getImplementation();
                    if (implementation.getClassLoaderHash() == null) {
                        return null;
                    }
                    return implementation.getClassLoaderHash().toByteArray();
                }
            })
            .orElse(null);
    }

    @Override
    public List<byte[]> getActionClassLoaderHashesBytes() {
        return cachingState.getInputs()
            .map(new java.util.function.Function<CachingInputs, List<byte[]>>() {
                @Nullable
                @Override
                public List<byte[]> apply(CachingInputs inputs) {
                    List<ImplementationSnapshot> additionalImplementations = inputs.getAdditionalImplementations();
                    if (additionalImplementations.isEmpty()) {
                        return null;
                    }
                    return Lists.transform(additionalImplementations, new Function<ImplementationSnapshot, byte[]>() {
                        @Override
                        public byte[] apply(ImplementationSnapshot input) {
                            return input.getClassLoaderHash() == null ? null : input.getClassLoaderHash().toByteArray();
                        }
                    });
                }
            })
            .orElse(null);
    }

    @Nullable
    @Override
    public List<String> getActionClassNames() {
        return cachingState.getInputs()
            .map(new java.util.function.Function<CachingInputs, List<String>>() {
                @Nullable
                @Override
                public List<String> apply(CachingInputs inputs) {
                    List<ImplementationSnapshot> additionalImplementations = inputs.getAdditionalImplementations();
                    if (additionalImplementations.isEmpty()) {
                        return null;
                    }
                    return Lists.transform(additionalImplementations, new Function<ImplementationSnapshot, String>() {
                        @Override
                        public String apply(ImplementationSnapshot input) {
                            return input.getTypeName();
                        }
                    });
                }
            })
            .orElse(null);
    }

    @Nullable
    @Override
    public List<String> getOutputPropertyNames() {
        return cachingState.getInputs()
            .map(new java.util.function.Function<CachingInputs, List<String>>() {
                @Nullable
                @Override
                public List<String> apply(CachingInputs inputs) {
                    ImmutableSortedSet<String> outputPropertyNames = inputs.getOutputProperties();
                    if (outputPropertyNames.isEmpty()) {
                        return null;
                    }
                    return outputPropertyNames.asList();
                }
            })
            .orElse(null);
    }

    @Override
    public byte[] getHashBytes() {
        return cachingState.getKey()
            .map(new java.util.function.Function<BuildCacheKey, byte[]>() {
                @Override
                public byte[] apply(BuildCacheKey cacheKey) {
                    return cacheKey.toByteArray();
                }
            })
            .orElse(null);
    }

    @Override
    public Object getCustomOperationTraceSerializableModel() {
        Map<String, Object> model = new TreeMap<String, Object>();

        final Function<byte[], String> bytesToString = new Function<byte[], String>() {
            @Nullable
            @Override
            public String apply(@Nullable byte[] input) {
                if (input == null) {
                    return null;
                }
                return HashCode.fromBytes(input).toString();
            }
        };

        List<byte[]> actionClassLoaderHashesBytes = getActionClassLoaderHashesBytes();
        if (actionClassLoaderHashesBytes != null) {
            model.put("actionClassLoaderHashes", Lists.transform(getActionClassLoaderHashesBytes(), bytesToString));
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

        model.put("inputPropertiesLoadedByUnknownClassLoader", getInputPropertiesLoadedByUnknownClassLoader());

        Map<String, byte[]> inputValueHashesBytes = getInputValueHashesBytes();
        if (inputValueHashesBytes != null) {
            model.put("inputValueHashes", Maps.transformEntries(inputValueHashesBytes, new Maps.EntryTransformer<String, byte[], String>() {
                @Nullable
                @Override
                public String transformEntry(@Nullable String key, @Nullable byte[] value) {
                    if (value == null) {
                        return null;
                    }
                    return HashCode.fromBytes(value).toString();
                }
            }));
        } else {
            model.put("inputValueHashes", null);
        }

        model.put("outputPropertyNames", getOutputPropertyNames());

        return model;
    }

    protected Map<String, Object> fileProperties() {
        final Map<String, Object> fileProperties = new TreeMap<String, Object>();
        visitInputFileProperties(new InputFilePropertyVisitor() {
            Property property;
            Deque<DirEntry> dirStack = new ArrayDeque<DirEntry>();

            class Property {
                private final String hash;
                private final String normalization;
                private final List<Entry> roots = new ArrayList<Entry>();

                public Property(String hash, String normalization) {
                    this.hash = hash;
                    this.normalization = normalization;
                }

                public String getHash() {
                    return hash;
                }

                public String getNormalization() {
                    return normalization;
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
                private final List<Entry> children = new ArrayList<Entry>();

                DirEntry(String path) {
                    super(path);
                }

                public Collection<Entry> getChildren() {
                    return children;
                }
            }

            @Override
            public void preProperty(VisitState state) {
                property = new Property(HashCode.fromBytes(state.getPropertyHashBytes()).toString(), state.getPropertyNormalizationStrategyName());
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

}
