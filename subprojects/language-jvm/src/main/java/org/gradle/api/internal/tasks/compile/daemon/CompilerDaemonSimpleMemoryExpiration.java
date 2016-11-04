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

package org.gradle.api.internal.tasks.compile.daemon;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.internal.daemon.health.memory.MemoryInfo;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

/**
 * Simple max heap based compiler daemon expiration strategy.
 */
public class CompilerDaemonSimpleMemoryExpiration {

    // Reasonable default threshold bounds: between 384M and 1G
    private static final long MIN_THRESHOLD_BYTES = 384 * 1024 * 1024;
    private static final long MAX_THRESHOLD_BYTES = 1024 * 1024 * 1024;

    private final MemoryInfo memoryInfo;
    private final long memoryThresholdInBytes;

    public CompilerDaemonSimpleMemoryExpiration(MemoryInfo memoryInfo, double minFreeMemoryPercentage) {
        Preconditions.checkArgument(minFreeMemoryPercentage >= 0, "Free memory percentage must be >= 0");
        Preconditions.checkArgument(minFreeMemoryPercentage <= 1, "Free memory percentage must be <= 1");
        this.memoryInfo = Preconditions.checkNotNull(memoryInfo);
        this.memoryThresholdInBytes = Math.min(MAX_THRESHOLD_BYTES, Math.max(MIN_THRESHOLD_BYTES, (long) (memoryInfo.getTotalPhysicalMemory() * minFreeMemoryPercentage)));
    }

    public void eventuallyExpireDaemons(DaemonForkOptions requiredForkOptions, List<CompilerDaemonClient> idleClients, List<CompilerDaemonClient> allClients) {
        long requiredMaxHeapSize = parseHeapSize(memoryInfo, requiredForkOptions.getMaxHeapSize());
        long anticipatedFreeMemory = memoryInfo.getFreePhysicalMemory() - requiredMaxHeapSize;
        if (anticipatedFreeMemory < memoryThresholdInBytes) {
            if (expireDuplicateCompatibles(idleClients, allClients)) {
                anticipatedFreeMemory = memoryInfo.getFreePhysicalMemory() - requiredMaxHeapSize;
            }
            if (anticipatedFreeMemory < memoryThresholdInBytes) {
                expireLeastRecentlyUsedUntilEnoughFreeMemory(idleClients, allClients, anticipatedFreeMemory);
            }
        }
    }

    private boolean expireDuplicateCompatibles(List<CompilerDaemonClient> idleClients, List<CompilerDaemonClient> allClients) {
        boolean expired = false;
        ListIterator<CompilerDaemonClient> it = idleClients.listIterator(idleClients.size());
        List<CompilerDaemonClient> compatibilityUniques = Lists.newArrayListWithCapacity(idleClients.size());
        while (it.hasPrevious()) {
            final CompilerDaemonClient client = it.previous();
            boolean already = Iterables.any(compatibilityUniques, new Predicate<CompilerDaemonClient>() {
                @Override
                public boolean apply(CompilerDaemonClient candidate) {
                    return candidate.isCompatibleWith(client.getForkOptions());
                }
            });
            if (already) {
                allClients.remove(client);
                it.remove();
                client.stop();
                expired = true;
            } else {
                compatibilityUniques.add(client);
            }
        }
        return expired;
    }

    private void expireLeastRecentlyUsedUntilEnoughFreeMemory(List<CompilerDaemonClient> idleClients, List<CompilerDaemonClient> allClients, long anticipatedFreeMemory) {
        Iterator<CompilerDaemonClient> it = idleClients.iterator();
        while (it.hasNext()) {
            CompilerDaemonClient client = it.next();
            allClients.remove(client);
            it.remove();
            client.stop();
            anticipatedFreeMemory += parseHeapSize(memoryInfo, client.getForkOptions().getMaxHeapSize());
            if (anticipatedFreeMemory >= memoryThresholdInBytes) {
                break;
            }
        }
    }

    @VisibleForTesting
    static long parseHeapSize(MemoryInfo memoryInfo, String heapSize) {
        if (heapSize == null) {
            return getDefaultMaxHeap(memoryInfo);
        }
        String normalized = heapSize.toLowerCase(Locale.US).trim();
        if (normalized.isEmpty()) {
            return getDefaultMaxHeap(memoryInfo);
        }
        try {
            if (normalized.endsWith("m")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 1024 * 1024;
            }
            if (normalized.endsWith("g")) {
                return Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 1024 * 1024 * 1024;
            }
        } catch (NumberFormatException ex) {
            throw new InvalidUserDataException("Cannot parse heap size: " + heapSize, ex);
        }
        throw new InvalidUserDataException("Cannot parse heap size: " + heapSize);
    }

    /**
     * Get the default maximum heap.
     * Different JVMs on different systems may use a different default for maximum heap when unset.
     * This is a best effort approximation, not taking into account rules for low memory systems (<192MB total RAM).
     * https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/parallel.html#default_heap_size
     * http://www.ibm.com/support/knowledgecenter/SSYKE2_8.0.0/com.ibm.java.lnx.80.doc/diag/appendixes/defaults.html
     */
    @VisibleForTesting
    static long getDefaultMaxHeap(MemoryInfo memoryInfo) {
        long totalPhysicalMemory = memoryInfo.getTotalPhysicalMemory();

        if (Jvm.current().isIbmJvm()) {
            long totalMemoryHalf = totalPhysicalMemory / 2;
            long halfGB = parseHeapSize(memoryInfo, "512m");
            return totalMemoryHalf > halfGB ? halfGB : totalMemoryHalf;
        }

        long totalMemoryFourth = totalPhysicalMemory / 4;
        long oneGB = parseHeapSize(memoryInfo, "1g");
        switch (getJvmBitmode()) {
            case 32:
                return totalMemoryFourth > oneGB ? oneGB : totalMemoryFourth;
            case 64:
            default:
                if (isServerJvm()) {
                    long thirtyTwoGB = parseHeapSize(memoryInfo, "32g");
                    return totalMemoryFourth > thirtyTwoGB ? thirtyTwoGB : totalMemoryFourth;
                }
                return totalMemoryFourth > oneGB ? oneGB : totalMemoryFourth;
        }
    }

    private static int getJvmBitmode() {
        for (String property : Arrays.asList("sun.arch.data.model", "com.ibm.vm.bitmode", "os.arch")) {
            String value = System.getProperty(property);
            if (value != null) {
                if (value.contains("64")) {
                    return 64;
                }
            }
        }
        return 32;
    }

    private static boolean isServerJvm() {
        return !System.getProperty("java.vm.name").toLowerCase(Locale.US).contains("client");
    }
}
