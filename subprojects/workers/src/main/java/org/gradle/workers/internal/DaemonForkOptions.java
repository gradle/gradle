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

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsInternal;

public class DaemonForkOptions {
    private final JavaForkOptionsInternal forkOptions;
    private final KeepAliveMode keepAliveMode;
    private final ClassLoaderStructure classLoaderStructure;

    DaemonForkOptions(JavaForkOptionsInternal forkOptions,
                      KeepAliveMode keepAliveMode,
                      ClassLoaderStructure classLoaderStructure) {
        this.forkOptions = forkOptions;
        this.keepAliveMode = keepAliveMode;
        this.classLoaderStructure = classLoaderStructure;
    }

    public KeepAliveMode getKeepAliveMode() {
        return keepAliveMode;
    }

    public JavaForkOptions getJavaForkOptions() {
        return forkOptions;
    }

    public ClassLoaderStructure getClassLoaderStructure() {
        return classLoaderStructure;
    }

    public boolean isCompatibleWith(DaemonForkOptions other) {
        return forkOptions.isCompatibleWith(other.forkOptions)
                && keepAliveMode == other.getKeepAliveMode()
                && Objects.equal(classLoaderStructure, other.getClassLoaderStructure());
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("executable", forkOptions.getExecutable())
                .add("minHeapSize", forkOptions.getMinHeapSize())
                .add("maxHeapSize", forkOptions.getMaxHeapSize())
                .add("jvmArgs", forkOptions.getJvmArgs())
                .add("keepAliveMode", keepAliveMode)
                .toString();
    }
}
