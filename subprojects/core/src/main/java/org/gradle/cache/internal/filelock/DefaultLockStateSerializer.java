/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.cache.internal.filelock;

import org.gradle.cache.internal.FileLock;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Random;

public class DefaultLockStateSerializer implements LockStateSerializer {

    public int getSize() {
        return 16;
    }

    public byte getVersion() {
        return 3;
    }

    public LockState createInitialState() {
        long creationNumber = new Random().nextLong();
        return new SequenceNumberLockState(creationNumber, -1, 0);
    }

    public void write(DataOutput dataOutput, LockState lockState) throws IOException {
        SequenceNumberLockState state = (SequenceNumberLockState) lockState;
        dataOutput.writeLong(state.creationNumber);
        dataOutput.writeLong(state.sequenceNumber);
    }

    public LockState read(DataInput dataInput) throws IOException {
        long creationNumber = dataInput.readLong();
        long sequenceNumber = dataInput.readLong();
        return new SequenceNumberLockState(creationNumber, sequenceNumber, sequenceNumber);
    }

    private static class SequenceNumberLockState implements LockState {
        private final long creationNumber;
        private final long originalSequenceNumber;
        private final long sequenceNumber;

        private SequenceNumberLockState(long creationNumber, long originalSequenceNumber, long sequenceNumber) {
            this.creationNumber = creationNumber;
            this.originalSequenceNumber = originalSequenceNumber;
            this.sequenceNumber = sequenceNumber;
        }

        @Override
        public String toString() {
            return String.format("[%s,%s,%s]", creationNumber, sequenceNumber, isDirty());
        }

        public LockState beforeUpdate() {
            return new SequenceNumberLockState(creationNumber, originalSequenceNumber, 0);
        }

        public LockState completeUpdate() {
            long newSequenceNumber;
            if (isInInitialState()) {
                newSequenceNumber = 1;
            } else {
                newSequenceNumber = originalSequenceNumber + 1;
            }
            return new SequenceNumberLockState(creationNumber, newSequenceNumber, newSequenceNumber);
        }

        public boolean isDirty() {
            return sequenceNumber == 0 || sequenceNumber != originalSequenceNumber;
        }

        public boolean canDetectChanges() {
            return true;
        }

        public boolean isInInitialState() {
            return originalSequenceNumber <= 0;
        }

        public boolean hasBeenUpdatedSince(FileLock.State state) {
            SequenceNumberLockState other = (SequenceNumberLockState) state;
            return sequenceNumber != other.sequenceNumber || creationNumber != other.creationNumber;
        }
    }
}
