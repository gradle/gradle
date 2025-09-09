/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.fixtures

trait BuildLogicGcTrigger {

    /**
     * Declares a best-effort method to trigger a garbage collection in the build logic.
     * The implementation is borrowed from JMH sources, see
     * <a href="https://github.com/openjdk/jmh/blob/master/jmh-core/src/main/java/org/openjdk/jmh/profile/GCProfiler.java">GCProfiler.java</a>.
     */
    def withGcTriggerDeclaration(waitingForGcTimeoutMillis = 300) {
        """
            import java.lang.management.GarbageCollectorMXBean
            import java.lang.management.ManagementFactory

            private long getCurrentGcCount() {
                long sum = 0
                for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
                    long count = b.getCollectionCount()
                    if (count != -1) {
                        sum += count;
                    }
                }
                return sum
            }

            void requireGc() {
                long before = getCurrentGcCount()
                System.gc()
                long start = System.currentTimeMillis()
                while (getCurrentGcCount() == before) {
                    if (System.currentTimeMillis() - start > $waitingForGcTimeoutMillis) {
                        throw new RuntimeException("Timeout waiting for GC to happen")
                    }
                    Thread.sleep(10)
                }
            }
    """
    }
}
