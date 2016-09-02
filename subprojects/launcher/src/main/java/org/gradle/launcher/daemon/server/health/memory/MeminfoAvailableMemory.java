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

package org.gradle.launcher.daemon.server.health.memory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MeminfoAvailableMemory implements AvailableMemory {
    // /proc/meminfo is in kB since Linux 4.0, see https://git.kernel.org/cgit/linux/kernel/git/torvalds/linux.git/tree/fs/proc/task_mmu.c?id=39a8804455fb23f09157341d3ba7db6d7ae6ee76#n22
    private static final Pattern MEMINFO_LINE_PATTERN = Pattern.compile("^\\D+(\\d+) kB$");
    private static final String MEMINFO_FILE_PATH = "/proc/meminfo";

    private final Matcher meminfoMatcher;

    public MeminfoAvailableMemory() {
        // Initialize Matchers once and then reset them for performance
        meminfoMatcher = MEMINFO_LINE_PATTERN.matcher("");
    }

    @Override
    public long get() throws UnsupportedOperationException {
        List<String> meminfoOutputLines;
        try {
            meminfoOutputLines = Files.readLines(new File(MEMINFO_FILE_PATH), Charset.defaultCharset());
        } catch (IOException e) {
            throw new UnsupportedOperationException("Unable to read free memory from " + MEMINFO_FILE_PATH, e);
        }
        long freeMemoryFromProcMeminfo = parseFreeMemoryFromMeminfo(meminfoOutputLines);
        if (freeMemoryFromProcMeminfo == -1) {
            throw new UnsupportedOperationException("Unable to get free memory from " + MEMINFO_FILE_PATH);
        }
        return freeMemoryFromProcMeminfo;
    }

    /**
     * Given output from /proc/meminfo, return available memory in bytes.
     */
    @VisibleForTesting
    long parseFreeMemoryFromMeminfo(final List<String> meminfoLines) {
        final Meminfo meminfo = new Meminfo();

        // Linux 4.x: MemAvailable
        // Linux 3.x: MemFree + Buffers + Cached + SReclaimable - Mapped
        for (String line : meminfoLines) {
            if (line.startsWith("MemAvailable")) {
                return parseMeminfoBytes(line);
            } else if (line.startsWith("MemFree")) {
                meminfo.setMemFree(parseMeminfoBytes(line));
            } else if (line.startsWith("Buffers")) {
                meminfo.setBuffers(parseMeminfoBytes(line));
            } else if (line.startsWith("Cached")) {
                meminfo.setCached(parseMeminfoBytes(line));
            } else if (line.startsWith("SReclaimable")) {
                meminfo.setReclaimable(parseMeminfoBytes(line));
            } else if (line.startsWith("Mapped")) {
                meminfo.setMapped(parseMeminfoBytes(line));
            }
        }

        return meminfo.getAvailableBytes();
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
        throw new UnsupportedOperationException("Unable to parse /proc/meminfo output to get available memory");
    }

    private class Meminfo {
        private long memFree = -1;
        private long buffers = -1;
        private long cached = -1;
        private long reclaimable = -1;
        private long mapped = -1;

        void setMemFree(long memFree) {
            this.memFree = memFree;
        }

        long getAvailableBytes() {
            if (memFree != -1 && buffers != -1 && cached != -1 && reclaimable != -1 && mapped != -1) {
                return memFree + buffers + cached + reclaimable - mapped;
            }
            return -1;
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
    }
}
