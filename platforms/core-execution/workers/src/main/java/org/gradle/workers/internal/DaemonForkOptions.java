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
import org.gradle.process.internal.EffectiveJavaForkOptions;
import org.gradle.process.internal.EffectiveJavaForkOptions.ReadOnlyJvmOptions;
import org.gradle.process.internal.JavaExecHandleBuilder;

import java.io.File;
import java.util.Map;

public class DaemonForkOptions {
    private final EffectiveJavaForkOptions forkOptions;
    private final KeepAliveMode keepAliveMode;
    private final ClassLoaderStructure classLoaderStructure;

    DaemonForkOptions(
        EffectiveJavaForkOptions forkOptions,
        KeepAliveMode keepAliveMode,
        ClassLoaderStructure classLoaderStructure
    ) {
        this.forkOptions = forkOptions;
        this.keepAliveMode = keepAliveMode;
        this.classLoaderStructure = classLoaderStructure;
    }

    public KeepAliveMode getKeepAliveMode() {
        return keepAliveMode;
    }

    public ReadOnlyJvmOptions getJvmOptions() {
        return forkOptions.getJvmOptions();
    }

    public String getExecutable() {
        return forkOptions.getExecutable();
    }

    public File getWorkingDir() {
        return forkOptions.getWorkingDir();
    }

    public Map<String, Object> getEnvironment() {
        return forkOptions.getEnvironment();
    }

    public ClassLoaderStructure getClassLoaderStructure() {
        return classLoaderStructure;
    }

    public void copyTo(JavaExecHandleBuilder target) {
        forkOptions.copyTo(target);
    }

    public boolean isCompatibleWith(DaemonForkOptions other) {
        return forkOptions.isCompatibleWith(other.forkOptions)
            && keepAliveMode == other.getKeepAliveMode()
            && Objects.equal(classLoaderStructure, other.getClassLoaderStructure());
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("executable", getExecutable())
            .add("minHeapSize", getJvmOptions().getMinHeapSize())
            .add("maxHeapSize", getJvmOptions().getMaxHeapSize())
            .add("jvmArgs", getJvmOptions().getJvmArgs())
            .add("keepAliveMode", getKeepAliveMode())
            .toString();
    }
}
