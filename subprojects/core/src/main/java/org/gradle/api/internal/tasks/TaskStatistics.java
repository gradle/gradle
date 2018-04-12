/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks;

import com.google.common.collect.Maps;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskStatistics implements Closeable {
    private final AtomicInteger eagerTasks = new AtomicInteger();
    private final AtomicInteger lazyTasks = new AtomicInteger();
    private final AtomicInteger lazyRealizedTasks = new AtomicInteger();
    private final Map<Class, Integer> typeCounts = Maps.newHashMap();

    public void eagerTask(Class<?> type) {
        eagerTasks.incrementAndGet();
        synchronized (typeCounts) {
            Integer count = typeCounts.get(type);
            if (count == null) {
                count = 0;
            } else {
                count = count+1;
            }
            typeCounts.put(type, count);
        }
    }
    public void lazyTask() {
        lazyTasks.incrementAndGet();
    }
    public void lazyTaskRealized() {
        lazyRealizedTasks.incrementAndGet();
    }
    @Override
    public void close() throws IOException {
        System.out.printf("E %d L %d LR %d\n", eagerTasks.getAndSet(0), lazyTasks.getAndSet(0), lazyRealizedTasks.getAndSet(0));
        for (Map.Entry<Class, Integer> typeCount : typeCounts.entrySet()) {
            System.out.println(typeCount.getKey() + " " + typeCount.getValue());
        }
    }
}
