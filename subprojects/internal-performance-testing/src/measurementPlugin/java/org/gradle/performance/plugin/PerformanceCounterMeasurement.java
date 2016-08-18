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

import java.io.File;
import java.lang.reflect.Method;

class PerformanceCounterMeasurement {
    static Object performanceCounterDumper;
    static Method writeToFileMethod;

    static {
        Class<?> clazz = ReflectionUtil.loadClassIfAvailable("org.gradle.performance.plugin.PerformanceCounterDumper");
        if (clazz != null) {
            try {
                performanceCounterDumper = clazz.newInstance();
                writeToFileMethod = ReflectionUtil.findMethod(clazz, "writeToFile", File.class);
            } catch (Exception e) {
                System.err.println("Unable to record performance counters on this JVM.");
                e.printStackTrace(System.err);
            }
        }
    }

    private final File destinationDirectory;

    public PerformanceCounterMeasurement(File destinationDirectory) {
        this.destinationDirectory = destinationDirectory;
        destinationDirectory.mkdirs();
    }

    public void recordStart() {
        if (isEnabled()) {
            writeToFile(new File(destinationDirectory, "perf_counters_start.txt"));
        }
    }

    public void recordFinish() {
        if (isEnabled()) {
            writeToFile(new File(destinationDirectory, "perf_counters_finish.txt"));
        }
    }

    private void writeToFile(File file) {
        ReflectionUtil.invokeMethod(performanceCounterDumper, writeToFileMethod, file);
    }

    public boolean isEnabled() {
        return performanceCounterDumper != null && writeToFileMethod != null;
    }
}
