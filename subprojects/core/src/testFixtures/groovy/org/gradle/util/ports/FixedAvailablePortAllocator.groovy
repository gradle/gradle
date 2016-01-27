/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.util.ports

class FixedAvailablePortAllocator extends AbstractAvailablePortAllocator {
    static final String WORKER_ID_SYS_PROPERTY = "org.gradle.test.worker"
    static final String AGENT_NUM_SYS_PROPERTY = "org.gradle.ci.agentNum"
    static final String TOTAL_AGENTS_SYS_PROPERTY = "org.gradle.ci.agentCount"
    static final int DEFAULT_RANGE_SIZE = 20
    private static FixedAvailablePortAllocator instance
    final int workerId
    final int agentNum
    final int totalAgents
    final int bucketsPerAgent
    final int rangeCount
    final int rangeSize

    FixedAvailablePortAllocator(int workerId, int agentNum, int totalAgents) {
        this.agentNum = agentNum
        this.workerId = workerId
        this.totalAgents = totalAgents
        this.rangeSize = DEFAULT_RANGE_SIZE
        this.bucketsPerAgent = (MAX_PRIVATE_PORT - MIN_PRIVATE_PORT) / (rangeSize * totalAgents)
        this.rangeCount = bucketsPerAgent * totalAgents
    }

    public static FixedAvailablePortAllocator getInstance() {
        if (instance == null) {
            int totalAgents = Integer.getInteger(TOTAL_AGENTS_SYS_PROPERTY, 1)
            int agentNum = Integer.getInteger(AGENT_NUM_SYS_PROPERTY, 1)
            int workerId = Integer.getInteger(WORKER_ID_SYS_PROPERTY, -1)
            instance = new FixedAvailablePortAllocator(workerId, agentNum, totalAgents)
        }
        return instance
    }

    @Override
    protected ReservedPortRange reservePortRange() {
        if (reservations.size() >= 1) {
            throw new NoSuchElementException("All available ports in the fixed port range for agent ${agentNum}, worker ${workerId} have been exhausted.")
        }

        if (agentNum > totalAgents) {
            throw new IllegalArgumentException("Agent number was set to ${agentNum} but totalAgents was set to ${totalAgents}.")
        }

        int fixedRange = 0

        if (workerId != -1) {
            fixedRange = ((workerId - 1) % bucketsPerAgent) + ((agentNum - 1) * bucketsPerAgent)
        }

        int startPort = MIN_PRIVATE_PORT + (fixedRange * rangeSize)
        int endPort = startPort + rangeSize - 1
        return reservePortRange(startPort, endPort)
    }
}
