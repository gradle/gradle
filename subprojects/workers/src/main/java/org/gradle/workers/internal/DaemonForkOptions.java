/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;

import org.gradle.api.Nullable;
import org.gradle.process.internal.health.memory.MemoryAmount;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class DaemonForkOptions {
    private final String minHeapSize;
    private final String maxHeapSize;
    private final Iterable<String> jvmArgs;
    private final Iterable<File> classpath;
    private final Iterable<String> sharedPackages;
    private final KeepAliveMode keepAliveMode;

    public DaemonForkOptions(@Nullable String minHeapSize, @Nullable String maxHeapSize, Iterable<String> jvmArgs, KeepAliveMode keepAliveMode) {
        this(minHeapSize, maxHeapSize, jvmArgs, Collections.<File>emptyList(), Collections.<String>emptyList(), keepAliveMode);
    }

    public DaemonForkOptions(@Nullable String minHeapSize, @Nullable String maxHeapSize, Iterable<String> jvmArgs, Iterable<File> classpath,
                             Iterable<String> sharedPackages, KeepAliveMode keepAliveMode) {
        this.minHeapSize = minHeapSize;
        this.maxHeapSize = maxHeapSize;
        this.jvmArgs = jvmArgs;
        this.classpath = classpath;
        this.sharedPackages = sharedPackages;
        this.keepAliveMode = keepAliveMode;
    }

    public String getMinHeapSize() {
        return minHeapSize;
    }

    public String getMaxHeapSize() {
        return maxHeapSize;
    }

    public Iterable<String> getJvmArgs() {
        return jvmArgs;
    }

    public Iterable<File> getClasspath() {
        return classpath;
    }

    public Iterable<String> getSharedPackages() {
        return sharedPackages;
    }

    public KeepAliveMode getKeepAliveMode() {
        return keepAliveMode;
    }

    public boolean isCompatibleWith(DaemonForkOptions other) {
        return MemoryAmount.parseNotation(minHeapSize) >= MemoryAmount.parseNotation(other.getMinHeapSize())
            && MemoryAmount.parseNotation(maxHeapSize) >= MemoryAmount.parseNotation(other.getMaxHeapSize())
            && getNormalizedJvmArgs(jvmArgs).containsAll(getNormalizedJvmArgs(other.getJvmArgs()))
            && getNormalizedClasspath(classpath).containsAll(getNormalizedClasspath(other.getClasspath()))
            && getNormalizedSharedPackages(sharedPackages).containsAll(getNormalizedSharedPackages(other.sharedPackages))
            && keepAliveMode == other.getKeepAliveMode();
    }

    private Set<String> getNormalizedJvmArgs(Iterable<String> jvmArgs) {
        Set<String> normalized = Sets.newLinkedHashSet();
        for (String jvmArg : jvmArgs) {
            normalized.add(jvmArg.trim());
        }
        return normalized;
    }

    private Set<File> getNormalizedClasspath(Iterable<File> classpath) {
        return Sets.newLinkedHashSet(classpath);
    }

    private Set<String> getNormalizedSharedPackages(Iterable<String> allowedPackages) {
        return Sets.newLinkedHashSet(allowedPackages);
    }

    public String toString() {
        return Objects.toStringHelper(this).add("minHeapSize", minHeapSize).add("maxHeapSize", maxHeapSize).add("jvmArgs", jvmArgs).add("classpath", classpath).toString();
    }
}
