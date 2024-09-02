/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.api.tasks.compile.ProviderAwareCompilerDaemonForkOptions;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class and its subclasses exist so that we have an isolatable instance
 * of the fork options that can be passed along with the compilation spec to a
 * worker executor.  Since {@link ProviderAwareCompilerDaemonForkOptions}
 * and its subclasses can accept user-defined {@link org.gradle.process.CommandLineArgumentProvider}
 * instances, these objects may contain references to mutable objects in the
 * Gradle model or other non-isolatable objects.
 *
 * Subclasses should be sure to collapse any {@link org.gradle.process.CommandLineArgumentProvider}
 * arguments into {@link #getJvmArgs()} in order to capture the user-provided
 * command line arguments.
 */
public class MinimalCompilerDaemonForkOptions implements Serializable {

    private static final long serialVersionUID = 0;

    private String memoryInitialSize;
    private String memoryMaximumSize;
    private List<String> jvmArgs = new ArrayList<>();

    public MinimalCompilerDaemonForkOptions() {
    }

    public MinimalCompilerDaemonForkOptions(BaseForkOptions forkOptions) {
        setJvmArgs(forkOptions.getJvmArgs().get());
        setMemoryInitialSize(forkOptions.getMemoryInitialSize().getOrNull());
        setMemoryMaximumSize(forkOptions.getMemoryMaximumSize().getOrNull());
    }

    @Nullable
    public List<String> getJvmArgs() {
        return jvmArgs;
    }

    public void setJvmArgs(@Nullable List<String> jvmArgs) {
        // This was moved from the BaseForkOptions with Provider API migration,
        // original change: https://github.com/gradle/gradle/pull/11555
        this.jvmArgs = jvmArgs == null ? null : jvmArgs.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(string -> !string.isEmpty())
            .collect(Collectors.toList());
    }

    @Nullable
    public String getMemoryInitialSize() {
        return memoryInitialSize;
    }

    public void setMemoryInitialSize(@Nullable String memoryInitialSize) {
        this.memoryInitialSize = memoryInitialSize;
    }

    @Nullable
    public String getMemoryMaximumSize() {
        return memoryMaximumSize;
    }

    public void setMemoryMaximumSize(@Nullable String memoryMaximumSize) {
        this.memoryMaximumSize = memoryMaximumSize;
    }
}
