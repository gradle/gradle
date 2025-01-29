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

package org.gradle.api.internal.tasks.compile;

import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;

import java.util.ArrayList;
import java.util.Set;

import static org.gradle.process.internal.util.MergeOptionsUtil.mergeHeapSize;
import static org.gradle.process.internal.util.MergeOptionsUtil.normalized;

public class BaseForkOptionsConverter {

    private final JavaForkOptionsFactory forkOptionsFactory;

    public BaseForkOptionsConverter(JavaForkOptionsFactory forkOptionsFactory) {
        this.forkOptionsFactory = forkOptionsFactory;
    }

    public JavaForkOptions transform(MinimalCompilerDaemonForkOptions baseForkOptions) {
        JavaForkOptions javaForkOptions = forkOptionsFactory.newJavaForkOptions();
        javaForkOptions.setMinHeapSize(baseForkOptions.getMemoryInitialSize());
        javaForkOptions.setMaxHeapSize(baseForkOptions.getMemoryMaximumSize());
        javaForkOptions.setJvmArgs(baseForkOptions.getJvmArgs());
        return javaForkOptions;
    }

    public JavaForkOptions transform(MinimalCompilerDaemonForkOptions left, MinimalCompilerDaemonForkOptions right) {
        String memoryInitialSize = mergeHeapSize(left.getMemoryInitialSize(), right.getMemoryInitialSize());
        String memoryMaximumSize = mergeHeapSize(left.getMemoryMaximumSize(), right.getMemoryMaximumSize());

        Set<String> mergedJvmArgs = normalized(left.getJvmArgs());
        mergedJvmArgs.addAll(normalized(right.getJvmArgs()));

        MinimalCompilerDaemonForkOptions merged = new MinimalCompilerDaemonForkOptions(
            memoryInitialSize,
            memoryMaximumSize,
            new ArrayList<>(mergedJvmArgs)
        );

        return transform(merged);
    }

}
