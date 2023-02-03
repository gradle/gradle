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

import org.apache.commons.lang.StringUtils;
import org.gradle.internal.Cast;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
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
        private final List<String> ordinalGroups;
        private final List<String> queuedNodes;
        private final List<String> readyToStartNodes;
        private final List<String> otherNodes;
        private final List<String> eventItems;

        public Diagnostics(
            String displayName,
            List<String> ordinalGroups,
            List<String> waitingToStartNodes,
            List<String> readyToStartNodes,
            List<String> otherWaitingNodes,
            List<String> events
        ) {
            this.displayName = displayName;
            this.ordinalGroups = ordinalGroups;
            this.queuedNodes = waitingToStartNodes;
            this.readyToStartNodes = readyToStartNodes;
            this.otherNodes = otherWaitingNodes;
            this.eventItems = events;
        }

        public void describeTo(TreeFormatter formatter) {
            formatter.node(StringUtils.capitalize(displayName));
            formatter.startChildren();
            if (!queuedNodes.isEmpty()) {
                formatter.node("Waiting for nodes");
                formatter.startChildren();
                for (String item : queuedNodes) {
                    formatter.node(item);
                }
                formatter.endChildren();
            }
            if (!readyToStartNodes.isEmpty()) {
                formatter.node("Nodes ready to start");
                formatter.startChildren();
                for (String item : readyToStartNodes) {
                    formatter.node(item);
                }
                formatter.endChildren();
            }
            if (!otherNodes.isEmpty()) {
                formatter.node("Reachable nodes");
                formatter.startChildren();
                for (String item : otherNodes) {
                    formatter.node(item);
                }
                formatter.endChildren();
            }
            if (!eventItems.isEmpty()) {
                formatter.node("Scheduling events");
                formatter.startChildren();
                for (String eventItem : eventItems) {
                    formatter.node(eventItem);
                }
                formatter.endChildren();
            }
            formatter.node("Ordinal groups");
            formatter.startChildren();
            for (String item : ordinalGroups) {
                formatter.node(item);
            }
            formatter.endChildren();
            formatter.endChildren();
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
     * Returns some diagnostic information about the state of this plan.
     *
     * <p>The implementation does not need to be particularly efficient, as it is called only when a fatal problem is detected.</p>
     */
    Diagnostics healthDiagnostics();
}
