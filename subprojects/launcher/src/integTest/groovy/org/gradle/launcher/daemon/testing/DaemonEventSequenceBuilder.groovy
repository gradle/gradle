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

class DaemonEventSequenceBuilder {

    int pollRegistryMs = 50
    final int stateTransitionTimeoutMs

    DaemonsState currentState = null
    List<Runnable> actions = []

    private final List<DaemonsStateCheckpoint> checkpoints = []

    int numDaemons = 1

    DaemonEventSequenceBuilder(int stateTransitionTimeoutMs) {
        this.stateTransitionTimeoutMs = stateTransitionTimeoutMs
    }

    DaemonsEventSequence build(DaemonRegistry registry) {
        finishCheckpoint()
        new DaemonsEventSequence(pollRegistryMs, stateTransitionTimeoutMs, registry, *checkpoints)
    }

    void run(Closure action) {
        actions << action
    }

    void busy() {
        busy(numDaemons)
    }

    void busy(int busy) {
        state(busy, numDaemons - busy)
    }

    void idle() {
        idle(numDaemons)
    }

    void idle(int idle) {
        state(numDaemons - idle, idle)
    }

    void stopped() {
        numDaemons = 0
        state(0, 0)
    }

    void state(int busy, int idle) {
        state(new DaemonsState(busy, idle))
    }

    void state(DaemonsState checkpointState) {
        finishCheckpoint()
        currentState = checkpointState
    }

    void numDaemons(int numDaemons) {
        this.numDaemons = numDaemons
    }

    private finishCheckpoint() {
        if (currentState == null) {
            if (!actions.empty) {
                currentState = DaemonsState.getWildcardState()
                finishCheckpoint()
            }
        } else {
            checkpoints << new DaemonsStateCheckpoint(currentState, *actions)
            actions.clear()
        }
    }
}
