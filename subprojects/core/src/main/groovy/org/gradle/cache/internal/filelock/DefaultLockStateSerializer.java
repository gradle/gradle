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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class DefaultLockStateSerializer implements LockStateSerializer {

    public int getSize() {
        return 4;
    }

    public byte getVersion() {
        return 3;
    }

    public void write(DataOutput dataOutput, LockState lockState) throws IOException {
        dataOutput.writeInt(lockState.getPreviousOwnerId());
    }

    public LockState read(DataInput dataInput) throws IOException {
        int id = dataInput.readInt();
        return new LockState(id, id == LockState.UNKNOWN_PREVIOUS_OWNER);
    }
}