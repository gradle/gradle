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

package org.gradle.api.tasks.compile;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Incubating;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.util.internal.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Fork options for compilation that can accept user-defined {@link CommandLineArgumentProvider} objects.
 *
 * Only take effect if {@code fork} is {@code true}.
 *
 * @since 7.1
 */
@Incubating
public abstract class ProviderAwareCompilerDaemonForkOptions extends BaseForkOptions {

    private final List<CommandLineArgumentProvider> jvmArgumentProviders = new ArrayList<>();

    /**
     * Returns any additional JVM argument providers for the compiler process.
     *
     */
    @Optional
    @Nested
    @ToBeReplacedByLazyProperty
    public List<CommandLineArgumentProvider> getJvmArgumentProviders() {
        return jvmArgumentProviders;
    }

    /**
     * Returns the full set of arguments to use to launch the JVM for the compiler process. This includes arguments to define
     * system properties, the minimum/maximum heap size, and the bootstrap classpath.
     *
     * @return The immutable list of arguments. Returns an empty list if there are no arguments.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public List<String> getAllJvmArgs() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.addAll(CollectionUtils.stringize(getJvmArgs().get()));
        for (CommandLineArgumentProvider argumentProvider : getJvmArgumentProviders()) {
            builder.addAll(CollectionUtils.toStringList(argumentProvider.asArguments()));
        }
        return builder.build();
    }
}
