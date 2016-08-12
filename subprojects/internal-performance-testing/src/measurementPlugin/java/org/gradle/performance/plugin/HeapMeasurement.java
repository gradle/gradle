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

package org.gradle.performance.plugin;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.math.BigDecimal;

class HeapMeasurement {
    void handle(Project project, Logger logger) {
        final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
        logger.lifecycle("BEFORE GC");
        logHeap(logger, heap, nonHeap);

        memoryMXBean.gc();

        heap = memoryMXBean.getHeapMemoryUsage();
        nonHeap = memoryMXBean.getNonHeapMemoryUsage();
        logger.lifecycle("AFTER GC");
        logHeap(logger, heap, nonHeap);

        storeTotalMemoryUsed(project, heap);
    }

    private void storeTotalMemoryUsed(Project project, MemoryUsage heap) {
        project.getBuildDir().mkdirs();
        File totalMemoryUsedFile = new File(project.getBuildDir(), "totalMemoryUsed.txt");
        try {
            FileWriter writer = new FileWriter(totalMemoryUsedFile);
            try {
                writer.write(String.valueOf(heap.getUsed()));
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void logHeap(Logger logger, MemoryUsage heap, MemoryUsage nonHeap) {
        logger.lifecycle("heap: " + formatBytes(heap.getUsed()) + " (initial " + formatBytes(heap.getInit()) + ", committed " + formatBytes(heap.getCommitted()) + ", max " + formatBytes(heap.getMax()));
        logger.lifecycle("nonHeap: " + formatBytes(nonHeap.getUsed()) + " (initial " + formatBytes(nonHeap.getInit()) + ", committed " + formatBytes(nonHeap.getCommitted()) + ", max " + formatBytes(nonHeap.getMax()));
    }

    private String formatBytes(long bytesValue) {
        BigDecimal divisor = new BigDecimal(1024 * 1024);
        BigDecimal megabytes = new BigDecimal(bytesValue).divide(divisor).setScale(4, BigDecimal.ROUND_DOWN);
        return megabytes.toString() + "MB";
    }
}
