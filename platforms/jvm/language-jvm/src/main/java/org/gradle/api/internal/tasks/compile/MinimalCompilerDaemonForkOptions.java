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

/**
 * This class and its subclasses exist so that we have an isolatable instance
 * of the fork options that can be passed along with the compilation spec to a
 * worker executor.  Since {@link ProviderAwareCompilerDaemonForkOptions}
 * and its subclasses can accept user-defined {@link org.gradle.process.CommandLineArgumentProvider}
 * instances, these objects may contain references to mutable objects in the
 * Gradle model or other non-isolatable objects.
 *
 * Subclasses should be sure to collapse any {@link org.gradle.process.CommandLineArgumentProvider}
 * arguments into {@link #jvmArgs} in order to capture the user-provided
 * command line arguments.
 */
public class MinimalCompilerDaemonForkOptions extends BaseForkOptions {
    public MinimalCompilerDaemonForkOptions(BaseForkOptions forkOptions) {
        setJvmArgs(forkOptions.getJvmArgs());
        setMemoryInitialSize(forkOptions.getMemoryInitialSize());
        setMemoryMaximumSize(forkOptions.getMemoryMaximumSize());
    }
}
