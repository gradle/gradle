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

package org.gradle.api.internal.tasks.execution;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
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
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The {@link OperationResultImpl} operation represents the work of analyzing the task's inputs
 * plus the calculating the cache key.
 *
 * <p>
 * These two operations should be captured separately, but for historical reasons we don't yet do that.
 * To reproduce this composite operation why capture across executors by starting an operation
 * in {@link StartSnapshotTaskInputsBuildOperationTaskExecuter} and finished in {@link FinishSnapshotTaskInputsBuildOperationTaskExecuter}.
 * </p>
 */
public interface SnapshotTaskInputsMeasuringTaskExecuter extends TaskExecuter {
    OperationDetailsImpl DETAILS_INSTANCE = new OperationDetailsImpl();

    class OperationDetailsImpl implements SnapshotTaskInputsBuildOperationType.Details {}

    class OperationResultImpl implements SnapshotTaskInputsBuildOperationType.Result, CustomOperationTraceSerialization {

        @VisibleForTesting
        final TaskOutputCachingBuildCacheKey key;

        OperationResultImpl(TaskOutputCachingBuildCacheKey key) {
            this.key = key;
        }

        @Override
        public Map<String, byte[]> getInputValueHashesBytes() {
            ImmutableSortedMap<String, HashCode> inputHashes = key.getInputs().getInputValueHashes();
            if (inputHashes == null || inputHashes.isEmpty()) {
                return null;
            } else {
                return Maps.transformValues(inputHashes, new Function<HashCode, byte[]>() {
                    @Override
                    public byte[] apply(HashCode input) {
                        return input.toByteArray();
                    }
                });
            }
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
            public boolean preVisitDirectory(DirectorySnapshot physicalSnapshot) {
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
            public void visit(FileSystemLocationSnapshot snapshot) {
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
            public void postVisitDirectory(DirectorySnapshot directorySnapshot) {
                visitor.postDirectory();
                if (--depth == 0) {
                    visitor.postRoot();
                }
            }
        }

        @Override
        public void visitInputFileProperties(InputFilePropertyVisitor visitor) {
            State state = new State(visitor);
            ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFiles = key.getInputs().getInputFiles();
            if (inputFiles == null) {
                return;
            }
            for (Map.Entry<String, CurrentFileCollectionFingerprint> entry : inputFiles.entrySet()) {
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

        @Nullable
        @Override
        public Set<String> getInputPropertiesLoadedByUnknownClassLoader() {
            SortedMap<String, String> invalidInputProperties = key.getInputs().getNonCacheableInputProperties();
            if (invalidInputProperties == null || invalidInputProperties.isEmpty()) {
                return null;
            }
            return invalidInputProperties.keySet();
        }


        @Override
        public byte[] getClassLoaderHashBytes() {
            ImplementationSnapshot taskImplementation = key.getInputs().getTaskImplementation();
            if (taskImplementation == null || taskImplementation.getClassLoaderHash() == null) {
                return null;
            }
            return taskImplementation.getClassLoaderHash().toByteArray();
        }

        @Override
        public List<byte[]> getActionClassLoaderHashesBytes() {
            List<ImplementationSnapshot> actionImplementations = key.getInputs().getActionImplementations();
            if (actionImplementations == null || actionImplementations.isEmpty()) {
                return null;
            } else {
                return Lists.transform(actionImplementations, new Function<ImplementationSnapshot, byte[]>() {
                    @Override
                    public byte[] apply(ImplementationSnapshot input) {
                        return input.getClassLoaderHash() == null ? null : input.getClassLoaderHash().toByteArray();
                    }
                });
            }
        }

        @Nullable
        @Override
        public List<String> getActionClassNames() {
            List<ImplementationSnapshot> actionImplementations = key.getInputs().getActionImplementations();
            if (actionImplementations == null || actionImplementations.isEmpty()) {
                return null;
            } else {
                return Lists.transform(actionImplementations, new Function<ImplementationSnapshot, String>() {
                    @Override
                    public String apply(ImplementationSnapshot input) {
                        return input.getTypeName();
                    }
                });
            }
        }

        @Nullable
        @Override
        public List<String> getOutputPropertyNames() {
            // Copy should be a NOOP as this is an immutable sorted set upstream.
            ImmutableSortedSet<String> outputPropertyNames = key.getInputs().getOutputPropertyNames();
            if (outputPropertyNames == null || outputPropertyNames.isEmpty()) {
                return null;
            } else {
                return ImmutableSortedSet.copyOf(outputPropertyNames).asList();
            }
        }

        @Override
        public byte[] getHashBytes() {
            return key.isValid() ? key.getHashCodeBytes() : null;
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

}

