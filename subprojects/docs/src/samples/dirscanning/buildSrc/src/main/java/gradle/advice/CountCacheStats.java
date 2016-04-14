
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

package gradle.advice;

import net.bytebuddy.asm.Advice;
import org.gradle.api.internal.file.collections.DirectoryFileTree;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CountCacheStats {
    public static Map<File, Integer> HITS = new HashMap<File, Integer>();
    public static Map<File, Integer> MISSES = new HashMap<File, Integer>();


    public static class Hits {
        @Advice.OnMethodEnter
        public synchronized static void recordCacheHit(@Advice.Argument(0) DirectoryFileTree directoryFileTree) {
            incrementHit(directoryFileTree);
        }
    }

    public static void incrementHit(DirectoryFileTree directoryFileTree) {
        incrementCounter(directoryFileTree, HITS);
    }

    public static class Misses {
        @Advice.OnMethodEnter
        public synchronized static void recordCacheMiss(@Advice.Argument(0) DirectoryFileTree directoryFileTree) {
            incrementMiss(directoryFileTree);
        }
    }

    public static void incrementMiss(DirectoryFileTree directoryFileTree) {
        incrementCounter(directoryFileTree, MISSES);
    }

    private static synchronized void incrementCounter(DirectoryFileTree directoryFileTree, Map<File, Integer> counters) {
        File key = directoryFileTree.getDir().getAbsoluteFile();
        Integer count = counters.get(key);
        counters.put(key, count != null ? count + 1 : 1);
    }

    public synchronized static void reset() {
        HITS.clear();
        MISSES.clear();
    }
}
