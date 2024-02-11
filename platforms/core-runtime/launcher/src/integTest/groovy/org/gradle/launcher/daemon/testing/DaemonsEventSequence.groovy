/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.testing

import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.concurrent.Stoppable

import java.util.concurrent.LinkedBlockingQueue

/**
 * Models an expected series of state checkpoints (which may have attached actions).
 * <p>
 * This works by watching a daemon registry in a background thread, recording each state change.
 * As the state changes, it is tested against the next checkpoint. If the state of the checkpoint matches,
 * then the process repeats with the next checkpoint (until all checkpoints are passed).
 *
 * If there is no state change detected in a specified interval, an assertion error will be thrown.
 *
 * @see org.gradle.launcher.daemon.DaemonLifecycleSpec
 */
class DaemonsEventSequence implements Stoppable, Runnable {

    private final int pollRegistryMs
    private final int timeoutBetweenStateChangeMs
    private Date runStartedAt
    private Date lastStateChangeAt

    private final DaemonRegistry registry

    private final List<DaemonsStateCheckpoint> allCheckpoints
    private final List<DaemonsStateCheckpoint> remainingCheckpoints

    private DaemonsState lastDaemonsState = new DaemonsState(0, 0)
    private final Map<Long, DaemonsState> pastStateChanges = new LinkedHashMap<Long, DaemonsState>() // processed changes
    private final Queue<Holder> changeQueue = new LinkedBlockingQueue() // unprocessed changes

    private final ManagedExecutor executor
    private boolean stop = false

    private AssertionError timeoutError

    // wrapper object for the queue, to enable a null sentinel
    private class Holder {
        final value
        Holder(value) {
            this.value = value
        }
    }

    DaemonsEventSequence(int pollRegistryMs, int timeoutBetweenStateChangeMs, DaemonRegistry registry, DaemonsStateCheckpoint... checkpoints) {
        this.pollRegistryMs = pollRegistryMs
        this.timeoutBetweenStateChangeMs = timeoutBetweenStateChangeMs
        this.registry = registry
        this.allCheckpoints = Arrays.asList(checkpoints).asImmutable()
        this.remainingCheckpoints = new LinkedList(allCheckpoints)

        this.executor = new DefaultExecutorFactory().create("DaemonsEventSequence Consumer")
    }

    void run() {
        runStartedAt = new Date()
        executor.execute {
            try {
                putOnChangeQueue(lastDaemonsState) // always start with no daemons
                lastStateChangeAt = runStartedAt
                while (!stop) {
                    checkForDaemonsStateChange()
                    sleep(pollRegistryMs)
                }
            } catch (Exception e) {
                e.printStackTrace()
            } finally {
                putOnChangeQueue(null) // sentinel that no more is coming
            }
        }

        processChanges()
    }

    private checkForDaemonsStateChange() {
        def busy = registry.notIdle.size()
        def idle = registry.idle.size()

        def currentState = new DaemonsState(busy, idle)
        if (!lastDaemonsState.matches(currentState)) {
            putOnChangeQueue(currentState)
            lastDaemonsState = currentState
            lastStateChangeAt = new Date()
        }

        if (lastStateChangeAt.time + timeoutBetweenStateChangeMs < new Date().time) {
            def nextCheckpoint = remainingCheckpoints.first()

            def timeoutMessage = "timeoutBetweenStateChangeMs of $timeoutBetweenStateChangeMs"
            def checkpointMessage = "hit at checkpoint num $currentActionNum (expecting: $nextCheckpoint.expectedState)"
            def changesMessage = "processed state changes: $pastStateChanges, queued state changes: $changeQueue"

            timeoutError = new AssertionError("$timeoutMessage $checkpointMessage $changesMessage")
            stop = true
        }
    }

    private processChanges() {
        while (!remainingCheckpoints.empty) {
            def daemonsState = takeFromChangeQueue()
            if (timeoutError) {
                throw timeoutError
            }

            if (daemonsState == null) { return }

            Long timeSinceStart = lastStateChangeAt == null ? 0 : lastStateChangeAt.time - runStartedAt.time
            pastStateChanges[timeSinceStart] = daemonsState
            def nextCheckpoint = remainingCheckpoints.first()

            if (nextCheckpoint.test(daemonsState)) {
                remainingCheckpoints.remove(0)
            }
        }

        stop()
    }

    private putOnChangeQueue(value) {
        changeQueue.put(new Holder(value))
    }

    private takeFromChangeQueue() {
        changeQueue.take().value
    }

    void stop() {
        stop = true
        executor.stop()
    }

    int getCurrentActionNum() {
        remainingCheckpoints.empty ? -1 : allCheckpoints.size() - remainingCheckpoints.size()
    }
}
