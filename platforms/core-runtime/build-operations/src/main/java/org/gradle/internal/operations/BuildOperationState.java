/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.operations;

import org.gradle.internal.time.Timestamp;

import java.io.ObjectStreamException;
import java.util.concurrent.atomic.AtomicBoolean;

public class BuildOperationState implements BuildOperationRef {
    private final BuildOperationDescriptor description;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Timestamp startTime;

    public BuildOperationState(BuildOperationDescriptor descriptor, Timestamp startTime) {
        this.description = descriptor;
        this.startTime = startTime;
    }

    public BuildOperationDescriptor getDescription() {
        return description;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void setRunning(boolean running) {
        this.running.set(running);
    }

    public Timestamp getStartTime() {
        return startTime;
    }

    @Override
    public OperationIdentifier getId() {
        return description.getId();
    }

    @Override
    public OperationIdentifier getParentId() {
        return description.getParentId();
    }

    @Override
    public String toString() {
        return getDescription().getDisplayName();
    }

    private Object writeReplace() throws ObjectStreamException {
        return new DefaultBuildOperationRef(description.getId(), description.getParentId());
    }
}
