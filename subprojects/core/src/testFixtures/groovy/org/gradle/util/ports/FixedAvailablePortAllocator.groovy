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

class FixedAvailablePortAllocator extends AvailablePortAllocator {
    static final String MAX_FORKS_SYSTEM_PROPERTY = "org.gradle.test.maxParallelForks"
    static final String WORKER_ID_SYS_PROPERTY = "org.gradle.test.worker";
    static final String AGENT_NUM_SYS_PROPERTY = "org.gradle.ci.agentNum";
    static final String TOTAL_AGENTS_SYS_PROPERTY = "org.gradle.ci.agentCount";
    private static final FixedAvailablePortAllocator INSTANCE = new FixedAvailablePortAllocator()
    int workerId
    int agentNum
    int maxForks
    int totalAgents

    private FixedAvailablePortAllocator() {
        maxForks = Integer.getInteger(MAX_FORKS_SYSTEM_PROPERTY, 1)
        totalAgents = Integer.getInteger(TOTAL_AGENTS_SYS_PROPERTY, 1)
        agentNum = Integer.getInteger(AGENT_NUM_SYS_PROPERTY, 1)
        workerId = Integer.getInteger(WORKER_ID_SYS_PROPERTY, -1)
        rangeCount = maxForks * totalAgents
        rangeSize = (MAX_PRIVATE_PORT - MIN_PRIVATE_PORT) / rangeCount
    }

    FixedAvailablePortAllocator(int maxForks, int workerId, int agentNum, int totalAgents) {
        this.agentNum = agentNum
        this.workerId = workerId
        this.maxForks = maxForks
        this.totalAgents = totalAgents
        this.rangeCount = maxForks * totalAgents
        rangeSize = (MAX_PRIVATE_PORT - MIN_PRIVATE_PORT) / rangeCount
    }

    public static FixedAvailablePortAllocator getInstance() {
        return INSTANCE
    }

    @Override
    protected ReservedPortRange reservePortRange() {
        if (reservations.size() >= 1) {
            throw new NoSuchElementException("A fixed port range has already been assigned - cannot assign a new range.")
        }

        int fixedRange = 0
        if (rangeCount > 1) {
            if (workerId != -1) {
                fixedRange = ((workerId - 1) % maxForks) + ((agentNum - 1) * maxForks)
            } else {
                throw new IllegalStateException("${MAX_FORKS_SYSTEM_PROPERTY} is set, but ${WORKER_ID_SYS_PROPERTY} was not!")
            }
        }

        return reservePortRange(fixedRange)
    }
}
