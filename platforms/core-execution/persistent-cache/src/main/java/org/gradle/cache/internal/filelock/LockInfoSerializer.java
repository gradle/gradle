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

public class LockInfoSerializer {
    public static final int INFORMATION_REGION_DESCR_CHUNK_LIMIT = 340;

    public byte getVersion() {
        return 3;
    }

    public void write(DataOutput dataOutput, LockInfo lockInfo) throws IOException {
        dataOutput.writeInt(lockInfo.port);
        dataOutput.writeLong(lockInfo.lockId);
        dataOutput.writeUTF(trimIfNecessary(lockInfo.pid));
        dataOutput.writeUTF(trimIfNecessary(lockInfo.operation));
    }

    public LockInfo read(DataInput dataInput) throws IOException {
        LockInfo out = new LockInfo();
        out.port = dataInput.readInt();
        out.lockId = dataInput.readLong();
        out.pid = dataInput.readUTF();
        out.operation = dataInput.readUTF();
        return out;
    }

    private String trimIfNecessary(String inputString) {
        if (inputString.length() > INFORMATION_REGION_DESCR_CHUNK_LIMIT) {
            return inputString.substring(0, INFORMATION_REGION_DESCR_CHUNK_LIMIT);
        } else {
            return inputString;
        }
    }
}

