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
import net.rubygrapefruit.platform.memory.WindowsMemory;
import net.rubygrapefruit.platform.memory.WindowsMemoryInfo;
import org.gradle.api.NonNullApi;
import org.gradle.internal.nativeintegration.NativeIntegrationException;
import org.gradle.internal.nativeintegration.services.NativeServices;

@NonNullApi
public class WindowsOsMemoryInfo implements OsMemoryInfo {

    @Override
    public OsMemoryStatus getOsSnapshot() {
        try {
            WindowsMemory memory = NativeServices.getInstance().get(WindowsMemory.class);
            WindowsMemoryInfo memoryInfo = memory.getMemoryInfo();
            return new OsMemoryStatusSnapshot(
                memoryInfo.getTotalPhysicalMemory(), memoryInfo.getAvailablePhysicalMemory(),
                // Note: the commit limit is usually less than the hard limit of the commit peak, but I think it would be prudent
                // for us to not force the user's OS to allocate more page file space, so we'll use the commit limit here.
                memoryInfo.getCommitLimit(), memoryInfo.getCommitTotal()
            );
        } catch (NativeException ex) {
            throw new UnsupportedOperationException("Unable to get system memory", ex);
        } catch (NativeIntegrationException ex) {
            throw new UnsupportedOperationException("Unable to get system memory", ex);
        }
    }
}
