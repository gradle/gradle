/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.SnapshotTaskInputsBuildOperationType;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
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

public class ResolveBuildCacheKeyExecuter implements TaskExecuter {

    private static final Logger LOGGER = Logging.getLogger(ResolveBuildCacheKeyExecuter.class);
    private static final String BUILD_OPERATION_NAME = "Snapshot task inputs";

    private final TaskExecuter delegate;
    private final BuildOperationExecutor buildOperationExecutor;
    private final boolean buildCacheDebugLogging;

    public ResolveBuildCacheKeyExecuter(TaskExecuter delegate, BuildOperationExecutor buildOperationExecutor, boolean buildCacheDebugLogging) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
        this.buildCacheDebugLogging = buildCacheDebugLogging;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        resolve(task, context);
        return delegate.execute(task, state, context);
    }

    private void resolve(final TaskInternal task, final TaskExecutionContext context) {
        /*
            This operation represents the work of analyzing the inputs.
            Therefore, it should encompass all of the file IO and compute necessary to do this.
            This effectively happens in the first call to context.getTaskArtifactState().getStates().
            If build caching is enabled or the build scan plugin is applied, this is the first time that this will be called so it effectively
            encapsulates this work.

            If build cache isn't enabled and the build scan plugin is not applied,
            this executer isn't in the mix and therefore the work of hashing
            the inputs will happen later in the executer chain, and therefore they aren't wrapped in an operation.
            We avoid adding this executer due to concerns of performance impact.

            So, later, we either need always have this executer in the mix or make the input hashing
            an explicit step that always happens earlier and wrap it.
            Regardless, it would be good to formalise the input work in some form so it doesn't just
            happen as a side effect of calling some method for the first time.
         */
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext buildOperationContext) {
                TaskOutputCachingBuildCacheKey cacheKey = doResolve(task, context);
                buildOperationContext.setResult(new OperationResultImpl(cacheKey));
                context.setBuildCacheKey(cacheKey);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor
                    .displayName(BUILD_OPERATION_NAME + " for " + task.getIdentityPath()).name(BUILD_OPERATION_NAME)
                    .details(OperationDetailsImpl.INSTANCE);
            }
        });
    }

    private TaskOutputCachingBuildCacheKey doResolve(TaskInternal task, TaskExecutionContext context) {
        TaskArtifactState taskState = context.getTaskArtifactState();
        TaskProperties taskProperties = context.getTaskProperties();
        TaskOutputCachingBuildCacheKey cacheKey = taskState.calculateCacheKey(taskProperties);
        if (taskProperties.hasDeclaredOutputs() && cacheKey.isValid()) { // A task with no outputs has no cache key.
            LogLevel logLevel = buildCacheDebugLogging ? LogLevel.LIFECYCLE : LogLevel.INFO;
            LOGGER.log(logLevel, "Build cache key for {} is {}", task, cacheKey.getHashCode());
        }
        return cacheKey;
    }

    private static class OperationDetailsImpl implements SnapshotTaskInputsBuildOperationType.Details {
        private static final OperationDetailsImpl INSTANCE = new OperationDetailsImpl();

    }

    @VisibleForTesting
    static class OperationResultImpl implements SnapshotTaskInputsBuildOperationType.Result, CustomOperationTraceSerialization {

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
