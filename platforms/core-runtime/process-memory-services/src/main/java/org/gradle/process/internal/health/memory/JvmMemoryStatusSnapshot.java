/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.health.memory;

public class JvmMemoryStatusSnapshot implements JvmMemoryStatus {
    private final long maximumMemory;
    private final long committedMemory;

    public JvmMemoryStatusSnapshot(long maximumMemory, long commitedMemory) {
        this.maximumMemory = maximumMemory;
        this.committedMemory = commitedMemory;
    }

    @Override
    public long getMaxMemory() {
        return maximumMemory;
    }

    @Override
    public long getCommittedMemory() {
        return committedMemory;
    }

    @Override
    public String toString() {
        return "{Maximum: " + maximumMemory + ", Committed: " + committedMemory + '}';
    }
}
