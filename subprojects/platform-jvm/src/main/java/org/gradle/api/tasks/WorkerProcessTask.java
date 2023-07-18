/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLauncher;

/**
 * A {@code WorkerProcessTask} is a task which starts one (or more) worker processes during execution.
 *
 * @since 8.4
 */
@Incubating
public interface WorkerProcessTask {
    /**
     * Java launcher used to start the worker process
     */
    @Nested
    Property<JavaLauncher> getJavaLauncher();

    /**
     * The minimum heap size for the worker process.  When unspecified, no minimum heap size is set.
     * 
     * Supports units like the command-line option {@code -Xms} such as {@code "1g"}.
     *
     * @return The minimum heap size. 
     */
    @Optional
    @Input
    Property<String> getMinHeapSize();

    /**
     * The maximum heap size for the worker process.  If unspecified, a maximum heap size will be provided by Gradle.
     * 
     * Supports units like the command-line option {@code -Xmx} such as {@code "1g"}.
     *
     * @return The maximum heap size. 
     */
    @Optional
    @Input
    Property<String> getMaxHeapSize();
}
