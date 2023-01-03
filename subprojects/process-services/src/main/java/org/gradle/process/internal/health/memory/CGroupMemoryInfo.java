/*
 * Copyright 2022 the original author or authors.
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

import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public class CGroupMemoryInfo implements OsMemoryInfo {
    private static final String CGROUP_MEM_USAGE_FILE = "/sys/fs/cgroup/memory/memory.usage_in_bytes";
    private static final String CGROUP_MEM_TOTAL_FILE = "/sys/fs/cgroup/memory/memory.limit_in_bytes";

    @Override
    public OsMemoryStatus getOsSnapshot() {
        long memUsage;
        long memTotal;
        long memAvailable;

        try {
            String memUsageString = Files.asCharSource(new File(CGROUP_MEM_USAGE_FILE), Charset.defaultCharset()).readFirstLine();
            String memTotalString = Files.asCharSource(new File(CGROUP_MEM_TOTAL_FILE), Charset.defaultCharset()).readFirstLine();
            memUsage = Long.parseLong(memUsageString);
            memTotal = Long.parseLong(memTotalString);
            memAvailable = Math.max(0, memTotal - memUsage);
        } catch (IOException e) {
            throw new UnsupportedOperationException("Unable to read system memory", e);
        } catch (NumberFormatException e) {
            throw new UnsupportedOperationException("Unable to read system memory", e);
        }

        return new OsMemoryStatusSnapshot(memTotal, memAvailable);
    }
}
