/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.testing.execution.control.refork;

import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * @author Tom Eyckmans
 */
public class ForkMemoryLowData implements Serializable {
    private long totalMemory = -1;
    private long maxMemory = -1;
    private long freeMemory = -1;

    public ForkMemoryLowData() {
    }

    public long getTotalMemory() {
        return totalMemory;
    }

    public void setTotalMemory(long totalMemory) {
        if ( totalMemory <= 0 ) {
            throw new IllegalArgumentException("totalMemory can't be lower or equal to zero!");
        }

        this.totalMemory = totalMemory;
    }

    public long getMaxMemory() {
        return maxMemory;
    }

    public void setMaxMemory(long maxMemory) {
        if ( maxMemory <= 0 ) {
            throw new IllegalArgumentException("maxMemory can't be lower or equal to zero!");
        }

        this.maxMemory = maxMemory;
    }

    public long getFreeMemory() {
        return freeMemory;
    }

    public void setFreeMemory(long freeMemory) {
        if ( freeMemory < 0 ) {
            throw new IllegalArgumentException("freeMemory can't be lower than zero!");
        }

        this.freeMemory = freeMemory;
    }

    public double getCurrentUsagePercentage()
    {
        if ( freeMemory == -1 ) {
            return 0;
        }
        else {
            return ((double) (maxMemory-freeMemory) / maxMemory) * 100;
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeLong(totalMemory);
        out.writeLong(maxMemory);
        out.writeLong(freeMemory);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        totalMemory = in.readLong();
        maxMemory = in.readLong();
        freeMemory = in.readLong();
    }
}
