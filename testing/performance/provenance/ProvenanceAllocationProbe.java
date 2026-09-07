/*
 * Copyright 2026 Gradle and contributors.
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

import com.sun.management.ThreadMXBean;
import org.gradle.api.internal.provider.AbstractProperty;
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.PropertyHost;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;

/** Bounded allocation/layout probe; timings are raw observations, not a substitute for JMH. */
public class ProvenanceAllocationProbe {
    private static Instrumentation instrumentation;
    private static volatile Object sink;

    public static void premain(String args, Instrumentation instance) {
        instrumentation = instance;
    }

    public static void main(String[] args) throws Exception {
        DefaultProperty<String> property = new DefaultProperty<>(PropertyHost.NO_OP, String.class);
        property.set("value");
        Field state = AbstractProperty.class.getDeclaredField("state");
        state.setAccessible(true);
        layout("DefaultProperty", property);
        layout("mutable state", state.get(property));
        measure("construct", 100000, () -> sink = new DefaultProperty<>(PropertyHost.NO_OP, String.class));
        measure("bind", 100000, () -> property.set("value"));
        measure("read", 100000, () -> sink = property.get());
        property.finalizeValue();
        layout("finalized state", state.get(property));
        measure("finalized read", 100000, () -> sink = property.get());
    }

    static void layout(String name, Object object) {
        System.out.println("{\"layout\":\"" + name + "\",\"shallowBytes\":" + instrumentation.getObjectSize(object) + "}");
    }

    static void consume(Object object) {
        sink = object;
    }

    static void measure(String name, int repetitions, Runnable action) {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) {
            throw new IllegalStateException("Per-thread allocation measurement is unavailable.");
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        for (int warmup = 0; warmup < 5; warmup++) {
            for (int i = 0; i < repetitions; i++) {
                action.run();
            }
        }
        for (int sample = 0; sample < 5; sample++) {
            long bytes = bean.getThreadAllocatedBytes(thread);
            long nanos = System.nanoTime();
            for (int i = 0; i < repetitions; i++) {
                action.run();
            }
            nanos = System.nanoTime() - nanos;
            bytes = bean.getThreadAllocatedBytes(thread) - bytes;
            System.out.println("{\"workload\":\"" + name + "\",\"sample\":" + sample
                + ",\"repetitions\":" + repetitions + ",\"allocatedBytes\":" + bytes + ",\"nanos\":" + nanos + "}");
        }
    }
}
