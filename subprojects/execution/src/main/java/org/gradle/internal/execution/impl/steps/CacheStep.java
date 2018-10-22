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

package org.gradle.internal.execution.impl.steps;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.command.BuildCacheCommandFactory;
import org.gradle.caching.internal.command.BuildCacheLoadListener;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.packaging.CacheableTree;
import org.gradle.caching.internal.packaging.UnrecoverableUnpackingException;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.ExecutionResult;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

public class CacheStep<C extends CachingContext> implements Step<C> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheStep.class);

    private final BuildCacheController buildCache;
    private final OutputChangeListener outputChangeListener;
    private final BuildCacheCommandFactory commandFactory;
    private final Step<? super C> delegate;

    public CacheStep(
            BuildCacheController buildCache,
            OutputChangeListener outputChangeListener,
            BuildCacheCommandFactory commandFactory,
            Step<? super C> delegate
    ) {
        this.buildCache = buildCache;
        this.outputChangeListener = outputChangeListener;
        this.commandFactory = commandFactory;
        this.delegate = delegate;
    }

    @Override
    public org.gradle.internal.execution.ExecutionResult execute(C context) {
        return context.getCacheHandler()
            .load(cacheKey -> load(context.getWork(), cacheKey))
            .map(loadResult -> (ExecutionResult) new ExecutionResult() {
                @Override
                public ExecutionOutcome getOutcome() {
                    return ExecutionOutcome.FROM_CACHE;
                }

                @Override
                public OriginMetadata getOriginMetadata() {
                    return loadResult.getOriginMetadata();
                }

                @Override
                public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getFinalOutputs() {
                    return loadResult.getResultingSnapshots();
                }

                @Nullable
                @Override
                public Throwable getFailure() {
                    return null;
                }
            })
            .orElseGet(() -> {
                ExecutionResult executionResult = executeWithoutCache(context);
                if (executionResult.getFailure() == null) {
                    context.getCacheHandler().store(cacheKey -> store(context.getWork(), cacheKey, executionResult));
                } else {
                    LOGGER.debug("Not storing result of {} in cache because the execution failed", context.getWork().getDisplayName());
                }
                return executionResult;
            });
    }

    @Nullable
    private BuildCacheCommandFactory.LoadMetadata load(UnitOfWork work, BuildCacheKey cacheKey) {
        try {
            ImmutableSortedSet<OutputTree> outputProperties = resolveTrees(work);
            return buildCache.load(
                    commandFactory.createLoad(cacheKey, outputProperties, work, work.getLocalState(), new BuildCacheLoadListener() {
                        @Override
                        public void beforeLoad() {
                            outputChangeListener.beforeOutputChange();
                        }

                        @Override
                        public void afterLoadFailedAndWasCleanedUp(Throwable error) {
                            work.afterOutputsRemovedBeforeTask();
                        }
                    })
            );
        } catch (UnrecoverableUnpackingException e) {
            // We didn't manage to recover from the unpacking error, there might be leftover
            // garbage among the task's outputs, thus we must fail the build
            throw e;
        } catch (Exception e) {
            // There was a failure during downloading, previous task outputs should bu unaffected
            LOGGER.warn("Failed to load cache entry for {}, falling back to executing task", work.getDisplayName(), e);
            return null;
        }
    }

    private void store(UnitOfWork work, BuildCacheKey cacheKey, ExecutionResult result) {
        try {
            ImmutableSortedSet<OutputTree> outputProperties = resolveTrees(work);
            // TODO This could send in the whole origin metadata
            buildCache.store(commandFactory.createStore(cacheKey, outputProperties, result.getFinalOutputs(), work, result.getOriginMetadata().getExecutionTime()));
        } catch (Exception e) {
            LOGGER.warn("Failed to store cache entry {}", cacheKey.getDisplayName(), e);
        }
    }

    private static ImmutableSortedSet<OutputTree> resolveTrees(UnitOfWork work) {
        ImmutableSortedSet.Builder<OutputTree> builder = ImmutableSortedSet.naturalOrder();
        work.visitOutputs((name, type, files, root) -> {
            CacheableTree.Type treeType;
            switch (type) {
                case FILE:
                    treeType = CacheableTree.Type.FILE;
                    break;
                case DIRECTORY:
                    treeType = CacheableTree.Type.DIRECTORY;
                    break;
                default:
                    throw new AssertionError();
            }
            // TODO Probably should have some kind of visitable thing here, too
            assert root != null;
            builder.add(new OutputTree(name, treeType, root));
        });
        return builder.build();
    }

    private ExecutionResult executeWithoutCache(C context) {
        return delegate.execute(context);
    }

    private static class OutputTree implements CacheableTree {
        private final String name;
        private final Type type;
        private final File root;

        public OutputTree(String name, Type type, File root) {
            this.name = name;
            this.type = type;
            this.root = root;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Type getType() {
            return type;
        }

        @Nullable
        @Override
        public File getRoot() {
            return root;
        }

        @Override
        public int compareTo(@Nonnull CacheableTree o) {
            return getName().compareTo(o.getName());
        }
    }
}
