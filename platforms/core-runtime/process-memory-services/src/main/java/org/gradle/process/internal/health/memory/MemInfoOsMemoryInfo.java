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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MemInfoOsMemoryInfo implements OsMemoryInfo {
    // /proc/meminfo is in kB since Linux 4.0, see https://git.kernel.org/cgit/linux/kernel/git/torvalds/linux.git/tree/fs/proc/task_mmu.c?id=39a8804455fb23f09157341d3ba7db6d7ae6ee76#n22
    private static final Pattern MEMINFO_LINE_PATTERN = Pattern.compile("^\\D+(\\d+) kB$");
    private static final String MEMINFO_FILE_PATH = "/proc/meminfo";

    private final Matcher meminfoMatcher;

    public MemInfoOsMemoryInfo() {
        // Initialize Matchers once and then reset them for performance
        meminfoMatcher = MEMINFO_LINE_PATTERN.matcher("");
    }

    @Override
    public synchronized OsMemoryStatus getOsSnapshot() {
        // NOTE: meminfoMatcher is _not_ thread safe and access needs to be limited to a single thread.
        List<String> meminfoOutputLines;
        try {
            meminfoOutputLines = Files.readLines(new File(MEMINFO_FILE_PATH), Charset.defaultCharset());
        } catch (IOException e) {
            throw new UnsupportedOperationException("Unable to read system memory from " + MEMINFO_FILE_PATH, e);
        }

        return getOsSnapshotFromMemInfo(meminfoOutputLines);
    }


    /**
     * Given output from /proc/meminfo, return a system memory snapshot.
     */
    @VisibleForTesting
    OsMemoryStatusSnapshot getOsSnapshotFromMemInfo(final List<String> meminfoLines) {
        final Meminfo meminfo = new Meminfo();

        for (String line : meminfoLines) {
            if (line.startsWith("MemAvailable")) {
                meminfo.setAvailable(parseMeminfoBytes(line));
            } else if (line.startsWith("MemFree")) {
                meminfo.setFree(parseMeminfoBytes(line));
            } else if (line.startsWith("Buffers")) {
                meminfo.setBuffers(parseMeminfoBytes(line));
            } else if (line.startsWith("Cached")) {
                meminfo.setCached(parseMeminfoBytes(line));
            } else if (line.startsWith("SReclaimable")) {
                meminfo.setReclaimable(parseMeminfoBytes(line));
            } else if (line.startsWith("Mapped")) {
                meminfo.setMapped(parseMeminfoBytes(line));
            } else if (line.startsWith("MemTotal")) {
                meminfo.setTotal(parseMeminfoBytes(line));
            }
        }

        if (meminfo.getAvailable() < 0 || meminfo.getTotal() < 0) {
            throw new UnsupportedOperationException("Unable to read system memory from " + MEMINFO_FILE_PATH);
        }

        return new OsMemoryStatusSnapshot(meminfo.getTotal(), meminfo.getAvailable());
    }

    /**
     * Given a line from /proc/meminfo, return number value representing number of bytes.
     *
     * @param line String line from /proc/meminfo. Example: "MemAvailable:    2109560 kB"
     * @return number from value transformed to bytes or -1 if unparsable. Example: 2_160_189_440
     */
    private long parseMeminfoBytes(final String line) {
        Matcher matcher = meminfoMatcher.reset(line);
        if (matcher.matches()) {
            return Long.parseLong(matcher.group(1)) * 1024;
        }
        throw new UnsupportedOperationException("Unable to parse /proc/meminfo output to get system memory");
    }

    private static class Meminfo {
        private long total = -1;
        private long available = -1;
        private long free = -1;
        private long buffers = -1;
        private long cached = -1;
        private long reclaimable = -1;
        private long mapped = -1;

        public long getTotal() {
            return total;
        }

        /*
         * Linux 4.x: MemAvailable
         * Linux 3.x: MemFree + Buffers + Cached + SReclaimable - Mapped
         */
        long getAvailable() {
            if (available != -1) {
                return available;
            }
            if (free != -1 && buffers != -1 && cached != -1 && reclaimable != -1 && mapped != -1) {
                return free + buffers + cached + reclaimable - mapped;
            }
            return -1;
        }

        void setFree(long memFree) {
            this.free = memFree;
        }

        public void setBuffers(long buffers) {
            this.buffers = buffers;
        }

        public void setCached(long cached) {
            this.cached = cached;
        }

        void setReclaimable(long reclaimable) {
            this.reclaimable = reclaimable;
        }

        public void setMapped(long mapped) {
            this.mapped = mapped;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public void setAvailable(long available) {
            this.available = available;
        }
    }
}
