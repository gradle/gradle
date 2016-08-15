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

import sun.management.counter.Counter;
import sun.management.counter.perf.PerfInstrumentation;
import sun.misc.Perf;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;

class PerformanceCounterDumper {
    final PerfInstrumentation perfInstrumentation;

    public PerformanceCounterDumper() throws IOException {
        perfInstrumentation = new PerfInstrumentation(Perf.getPerf().attach(findPid(), "r"));
    }

    private int findPid() {
        String vmName = ManagementFactory.getRuntimeMXBean().getName();
        return Integer.parseInt(vmName.substring(0, vmName.indexOf("@")));
    }

    public void writeToFile(File file) throws IOException {
        FileWriter writer = new FileWriter(file);
        try {
            for (Counter counter : perfInstrumentation.getAllCounters()) {
                writer.write(counter.getName());
                writer.write('=');
                writeValue(writer, counter.getValue());
                writer.write("\n");
            }
        } finally {
            writer.close();
        }
    }

    private void writeValue(Writer writer, Object value) throws IOException {
        if (value.getClass().isArray()) {
            Object[] arr = (Object[]) value;
            writer.write("[");
            for (int i = 0; i < arr.length; i++) {
                writer.write(String.valueOf(arr[i]));
                if (i != arr.length - 1) {
                    writer.write(',');
                }
            }
            writer.write("]");
        } else {
            writer.write(String.valueOf(value));
        }
    }
}
