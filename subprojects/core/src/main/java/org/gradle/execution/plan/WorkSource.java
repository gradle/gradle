/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.plan;

import org.gradle.internal.Cast;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Represents some source of work items of type {@link T}. Implementations must be thread safe.
 */
@ThreadSafe
public interface WorkSource<T> {
    enum State {
        /**
         * There may be work ready to start. The worker thread should call {@link #selectNext()} to select the next item.
         * Note this does not mean that {@link #selectNext()} will necessarily return an item, only that it is likely to.
         * {@link #selectNext()} may not return an item, for example when some other worker thread takes the work.
         */
        MaybeWorkReadyToStart,
        /**
         * No work is ready to start, but there are items still queued to start. The worker thread should wait for a change and check again.
         */
        NoWorkReadyToStart,
        /**
         * All work has started (but not necessarily finished) and there are no further items to start. The worker thread should finish polling this source.
         * Note that this does not mean that all work has completed.
         */
        NoMoreWorkToStart
    }

    abstract class Selection<T> {
        private static final Selection<Object> NO_WORK_READY_TO_START = new Selection<Object>() {
            @Override
            public boolean isNoWorkReadyToStart() {
                return true;
            }
        };
        private static final Selection<Object> NO_MORE_WORK_TO_START = new Selection<Object>() {
            @Override
            public boolean isNoMoreWorkToStart() {
                return true;
            }
        };


        public static <S> Selection<S> of(S item) {
            return new Selection<S>() {
                @Override
                public S getItem() {
                    return item;
                }
            };
        }

        public static <S> Selection<S> noWorkReadyToStart() {
            return Cast.uncheckedCast(NO_WORK_READY_TO_START);
        }

        public static <S> Selection<S> noMoreWorkToStart() {
            return Cast.uncheckedCast(NO_MORE_WORK_TO_START);
        }

        public boolean isNoWorkReadyToStart() {
            return false;
        }

        public boolean isNoMoreWorkToStart() {
            return false;
        }

        public T getItem() {
            throw new IllegalStateException();
        }
    }

    /**
     * Some basic diagnostic information about the state of the work.
     */
    class Diagnostics {
        private final String displayName;
        private final boolean canMakeProgress;
        private final List<String> queuedItems;
        private final List<String> otherItems;

        public Diagnostics(String displayName, boolean canMakeProgress, List<String> queuedItems, List<String> otherItems) {
            this.displayName = displayName;
            this.canMakeProgress = canMakeProgress;
            this.queuedItems = queuedItems;
            this.otherItems = otherItems;
        }

        public Diagnostics(String displayName) {
            this(displayName, true, Collections.emptyList(), Collections.emptyList());
        }

        /**
         * Returns true when either all work is finished or there are further items that can be selected.
         * Returns false when there are items queued but none of them will be able to be selected, without some external change (eg completion of a task in an included build).
         */
        public boolean canMakeProgress() {
            return canMakeProgress;
        }

        public void describeTo(TreeFormatter formatter) {
            if (!queuedItems.isEmpty()) {
                formatter.node("Queued nodes for " + displayName);
                formatter.startChildren();
                for (String item : queuedItems) {
                    formatter.node(item);
                }
                formatter.endChildren();
            }
            if (!otherItems.isEmpty()) {
                formatter.node("Non-queued nodes for " + displayName);
                formatter.startChildren();
                for (String item : otherItems) {
                    formatter.node(item);
                }
                formatter.endChildren();
            }
        }
    }

    /**
     * Returns the current execution state of this plan.
     *
     * <p>Note: the caller does not need to hold a worker lease to call this method.</p>
     *
     * <p>The implementation of this method may prefer to return {@link State#MaybeWorkReadyToStart} in certain cases, to limit
     * the amount of work that happens in this method, which is called many, many times and should be fast.</p>
     */
    State executionState();

    /**
     * Selects a work item to start, returns {@link Selection#noWorkReadyToStart()} when there are no items that are ready to start (but some are queued for execution)
     * and {@link Selection#noMoreWorkToStart()} when there are no items remaining to start.
     *
     * <p>Note: the caller must hold a worker lease.</p>
     *
     * <p>The caller must call {@link #finishedExecuting(Object, Throwable)} when execution is complete.</p>
     */
    Selection<T> selectNext();

    void finishedExecuting(T item, @Nullable Throwable failure);

    void abortAllAndFail(Throwable t);

    void cancelExecution();

    /**
     * Has all execution completed?
     *
     * <p>When this method returns {@code true}, there is no further work to start and no work in progress.</p>
     *
     * <p>When this method returns {@code false}, there is further work yet to complete.</p>
     */
    boolean allExecutionComplete();

    /**
     * Collects the current set of work failures into the given collection.
     */
    void collectFailures(Collection<? super Throwable> failures);

    /**
     * Returns some diagnostic information about the state of this plan. This is used to monitor the health of the plan.
     */
    Diagnostics healthDiagnostics();
}
