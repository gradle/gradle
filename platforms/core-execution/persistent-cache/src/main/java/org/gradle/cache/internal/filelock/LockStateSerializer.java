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

public interface LockStateSerializer {

    /**
     * size (bytes) of the data of this protocol.
     */
    int getSize();

    /**
     * single byte that describes the version.
     * an implementation protocol should increment the value when protocol changes in an incompatible way
     */
    byte getVersion();

    /**
     * Returns the initial state for a lock file with this format.
     */
    LockState createInitialState();

    /**
     * writes the state data
     */
    void write(DataOutput lockFileAccess, LockState lockState) throws IOException;

    /**
     * reads the state data
     */
    LockState read(DataInput lockFileAccess) throws IOException;
}
