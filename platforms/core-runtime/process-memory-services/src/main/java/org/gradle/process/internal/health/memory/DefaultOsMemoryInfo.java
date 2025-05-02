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

import org.gradle.internal.os.OperatingSystem;

public class DefaultOsMemoryInfo implements OsMemoryInfo {
    private final OsMemoryInfo delegate;

    public DefaultOsMemoryInfo() {
        OperatingSystem operatingSystem = OperatingSystem.current();
        if (operatingSystem.isMacOsX()) {
            delegate = new NativeOsMemoryInfo();
        } else if (operatingSystem.isLinux()) {
            delegate = getLinuxDelegate();
        } else if (operatingSystem.isWindows()) {
            delegate = new WindowsOsMemoryInfo();
        } else {
            delegate = new MBeanOsMemoryInfo(new DefaultMBeanAttributeProvider());
        }
    }

    private OsMemoryInfo getLinuxDelegate() {
        OsMemoryInfo cGroupDelegate = new CGroupMemoryInfo();
        OsMemoryInfo memInfoDelegate = new MemInfoOsMemoryInfo();

        OsMemoryStatus cGroupSnapshot;
        OsMemoryStatus memInfoSnapshot;

        try {
            cGroupSnapshot = cGroupDelegate.getOsSnapshot();
        } catch (UnsupportedOperationException e) {
            return memInfoDelegate;
        }

        try {
            memInfoSnapshot = memInfoDelegate.getOsSnapshot();
        } catch (UnsupportedOperationException e) {
            return cGroupDelegate;
        }

        long cGroupFreeMemory = cGroupSnapshot.getPhysicalMemory().getFree();
        long memInfoFreeMemory = memInfoSnapshot.getPhysicalMemory().getFree();

        return cGroupFreeMemory > memInfoFreeMemory ? memInfoDelegate : cGroupDelegate;
    }

    @Override
    public OsMemoryStatus getOsSnapshot() {
        return delegate.getOsSnapshot();
    }
}
