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
import com.google.common.collect.Lists;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.process.internal.DefaultExecActionFactory;
import org.gradle.process.internal.ExecHandleBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse /usr/bin/vm_stat output to calculate available memory.
 */
public class VmstatAvailableMemory implements AvailableMemory {
    private static final Pattern VMSTAT_LINE_PATTERN = Pattern.compile("^\\D+(\\d+)\\D+$");
    private static final String VMSTAT_EXECUTABLE_PATH = "/usr/bin/vm_stat";

    private final Matcher vmstatMatcher;

    public VmstatAvailableMemory() {
        // Initialize Matchers once and then reset them for performance
        vmstatMatcher = VMSTAT_LINE_PATTERN.matcher("");
    }

    @Override
    public long get() throws UnsupportedOperationException {
        long freeMemoryFromVmstat = parseFreeMemoryFromVmstat(getVmstatOutput());
        if (freeMemoryFromVmstat == -1) {
            throw new UnsupportedOperationException("Unable to get free memory from " + VMSTAT_EXECUTABLE_PATH);
        }
        return freeMemoryFromVmstat;
    }

    private List<String> getVmstatOutput() {
        try {
            StreamByteBuffer buffer = new StreamByteBuffer();
            ExecHandleBuilder builder = new DefaultExecActionFactory(new IdentityFileResolver()).newExec();
            builder.setWorkingDir(new File(".").getAbsolutePath());
            builder.setCommandLine(VMSTAT_EXECUTABLE_PATH);
            builder.setStandardOutput(buffer.getOutputStream());
            builder.build().start().waitForFinish().assertNormalExitValue();

            BufferedReader reader = new BufferedReader(new InputStreamReader(buffer.getInputStream()));
            List<String> lines = Lists.newArrayList();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        } catch (Exception e) {
            throw new UnsupportedOperationException("Unable to read memory info from " + VMSTAT_EXECUTABLE_PATH, e);
        }
    }

    /**
     * Given a file referencing /usr/bin/vm_stat, return available memory in bytes.
     */
    @VisibleForTesting
    long parseFreeMemoryFromVmstat(final List<String> vmstatLines) {
        final VmstatOutput vmstatOutput = new VmstatOutput();

        if (!vmstatLines.isEmpty()) {
            long pageSize = parseVmstatBytes(vmstatLines.get(0));
            for (String line : vmstatLines) {
                if (line.startsWith("Pages free")) {
                    vmstatOutput.setFreeBytes(parseVmstatBytes(line) * pageSize);
                } else if (line.startsWith("File-backed pages")) {
                    vmstatOutput.setReclaimableBytes(parseVmstatBytes(line) * pageSize);
                }
            }
        }

        return vmstatOutput.getAvailableBytes();
    }

    private long parseVmstatBytes(final String line) {
        Matcher matcher = vmstatMatcher.reset(line);
        if (matcher.matches()) {
            return Long.parseLong(matcher.group(1));
        }
        throw new UnsupportedOperationException("Unable to parse vm_stat output to get available memory");
    }

    private class VmstatOutput {
        private long freeBytes = -1;
        private long reclaimableBytes = -1;

        void setFreeBytes(long freeBytes) {
            this.freeBytes = freeBytes;
        }

        void setReclaimableBytes(long reclaimableBytes) {
            this.reclaimableBytes = reclaimableBytes;
        }

        long getAvailableBytes() {
            if (freeBytes != -1 && reclaimableBytes != -1) {
                return freeBytes + reclaimableBytes;
            }
            return -1;
        }
    }
}
