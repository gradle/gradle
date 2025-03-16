/*
 * Copyright 2017 the original author or authors.
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

import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.memory.Memory;
import net.rubygrapefruit.platform.memory.MemoryInfo;
import org.gradle.internal.nativeintegration.NativeIntegrationException;
import org.gradle.internal.nativeintegration.services.NativeServices;

public class NativeOsMemoryInfo implements OsMemoryInfo {

    @Override
    public OsMemoryStatus getOsSnapshot() {
        try {
            Memory memory = NativeServices.getInstance().get(Memory.class);
            MemoryInfo memoryInfo = memory.getMemoryInfo();
            return new OsMemoryStatusSnapshot(memoryInfo.getTotalPhysicalMemory(), memoryInfo.getAvailablePhysicalMemory());
        } catch (NativeException ex) {
            throw new UnsupportedOperationException("Unable to get system memory", ex);
        } catch (NativeIntegrationException ex) {
            throw new UnsupportedOperationException("Unable to get system memory", ex);
        }
    }
}
