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

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class DefaultStateInfoProtocol implements StateInfoProtocol {

    public int getSize() {
        return 5;
    }

    public int getVersion() {
        return 2;
    }

    public void writeState(RandomAccessFile lockFileAccess, StateInfo stateInfo) throws IOException {
        lockFileAccess.writeInt(stateInfo.getPreviousOwnerId());
    }

    public StateInfo readState(RandomAccessFile lockFileAccess) throws IOException {
        int id;
        try {
            id = lockFileAccess.readInt();
        } catch (EOFException e) {
            // Process has crashed writing to lock file
            id = StateInfo.UNKNOWN_PREVIOUS_OWNER;
        }
        return new StateInfo(id, id == StateInfo.UNKNOWN_PREVIOUS_OWNER);
    }
}