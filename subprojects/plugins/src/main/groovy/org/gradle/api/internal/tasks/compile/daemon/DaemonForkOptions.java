/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.base.Objects;
import com.google.common.collect.Sets;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class DaemonForkOptions {
    private final String minHeapSize;
    private final String maxHeapSize;
    private final Iterable<String> jvmArgs;
    private final Iterable<File> classpath;
    private final Iterable<String> sharedPackages;

    public DaemonForkOptions(@Nullable String minHeapSize, @Nullable String maxHeapSize, Iterable<String> jvmArgs) {
        this(minHeapSize, maxHeapSize, jvmArgs, Collections.<File>emptyList(), Collections.<String>emptyList());
    }
    
    public DaemonForkOptions(@Nullable String minHeapSize, @Nullable String maxHeapSize, Iterable<String> jvmArgs, Iterable<File> classpath,
                             Iterable<String> sharedPackages) {
        this.minHeapSize = minHeapSize;
        this.maxHeapSize = maxHeapSize;
        this.jvmArgs = jvmArgs;
        this.classpath = classpath;
        this.sharedPackages = sharedPackages;
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

    public boolean isCompatibleWith(DaemonForkOptions other) {
        return getHeapSizeMb(minHeapSize) >= getHeapSizeMb(other.getMinHeapSize())
                && getHeapSizeMb(maxHeapSize) >= getHeapSizeMb(other.getMaxHeapSize())
                && getNormalizedJvmArgs(jvmArgs).containsAll(getNormalizedJvmArgs(other.getJvmArgs()))
                && getNormalizedClasspath(classpath).containsAll(getNormalizedClasspath(other.getClasspath()))
                && getNormalizedSharedPackages(sharedPackages).containsAll(getNormalizedSharedPackages(other.sharedPackages));
    }

    // one way to merge fork options, good for current use case
    public DaemonForkOptions mergeWith(DaemonForkOptions other) {
        String mergedMinHeapSize = mergeHeapSize(minHeapSize, other.minHeapSize);
        String mergedMaxHeapSize = mergeHeapSize(maxHeapSize, other.maxHeapSize);
        Set<String> mergedJvmArgs = getNormalizedJvmArgs(jvmArgs);
        mergedJvmArgs.addAll(getNormalizedJvmArgs(other.getJvmArgs()));
        Set<File> mergedClasspath = getNormalizedClasspath(classpath);
        mergedClasspath.addAll(getNormalizedClasspath(other.classpath));
        Set<String> mergedAllowedPackages = getNormalizedSharedPackages(sharedPackages);
        mergedAllowedPackages.addAll(getNormalizedSharedPackages(other.sharedPackages));
        return new DaemonForkOptions(mergedMinHeapSize, mergedMaxHeapSize, mergedJvmArgs, mergedClasspath, mergedAllowedPackages);
    }

    private int getHeapSizeMb(String heapSize) {
        if (heapSize == null) {
            return -1; // unspecified
        }

        String normalized = heapSize.trim().toLowerCase();
        try {
            if (normalized.endsWith("m")) {
                return Integer.parseInt(normalized.substring(0, normalized.length() - 1));
            }
            if (normalized.endsWith("g")) {
                return Integer.parseInt(normalized.substring(0, normalized.length() - 1)) * 1024;
            }
        } catch (NumberFormatException e) {
            throw new InvalidUserDataException("Cannot parse heap size: " + heapSize, e);
        }
        throw new InvalidUserDataException("Cannot parse heap size: " + heapSize);
    }
    
    private String mergeHeapSize(String heapSize1, String heapSize2) {
        int mergedHeapSizeMb = Math.max(getHeapSizeMb(heapSize1), getHeapSizeMb(heapSize2));
        return mergedHeapSizeMb == -1 ? null : String.valueOf(mergedHeapSizeMb) + "m";
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
