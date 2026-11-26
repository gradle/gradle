/*
 * Copyright 2012 the original author or authors.
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

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Fork options for compilation. Only take effect if {@code fork}
 * is {@code true}.
 * @since 1.3
 */
public class BaseForkOptions implements Serializable {
    private static final long serialVersionUID = 0;

    private CompileOptionStorage<String> memoryInitialSize = CompileOptionStorage.inField(null);

    private CompileOptionStorage<String> memoryMaximumSize = CompileOptionStorage.inField(null);

    private CompileOptionStorage<List<String>> jvmArgs = CompileOptionStorage.inField(new ArrayList<>());

    @Nullable
    private BaseForkOptionsInputs lazyProperties;

    void attach(BaseForkOptionsInputs lazyProperties) {
        this.lazyProperties = lazyProperties;
        memoryInitialSize = memoryInitialSize.movedTo(lazyProperties.getMemoryInitialSize());
        memoryMaximumSize = memoryMaximumSize.movedTo(lazyProperties.getMemoryMaximumSize());
        jvmArgs = CompileOptionStorage.movedTo(jvmArgs, lazyProperties.getJvmArgs());
    }

    // Read through @BackedByLazyProperty. Null when the options are not attached, then a getter has no lazy property.
    @Nullable
    @SuppressWarnings("unused")
    private BaseForkOptionsInputs getLazyProperties() {
        return lazyProperties;
    }

    /**
     * Returns the initial heap size for the compiler process.
     * Defaults to {@code null}, in which case the JVM's default will be used.
     * @since 1.3
     */
    @Internal
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.memoryInitialSize")
    public String getMemoryInitialSize() {
        return memoryInitialSize.get();
    }

    /**
     * Sets the initial heap size for the compiler process.
     * Defaults to {@code null}, in which case the JVM's default will be used.
     * @since 1.3
     */
    public void setMemoryInitialSize(String memoryInitialSize) {
        this.memoryInitialSize.set(memoryInitialSize);
    }

    /**
     * Returns the maximum heap size for the compiler process.
     * Defaults to {@code null}, in which case the JVM's default will be used.
     * @since 1.3
     */
    @Internal
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.memoryMaximumSize")
    public String getMemoryMaximumSize() {
        return memoryMaximumSize.get();
    }

    /**
     * Sets the maximum heap size for the compiler process.
     * Defaults to {@code null}, in which case the JVM's default will be used.
     * @since 1.3
     */
    public void setMemoryMaximumSize(String memoryMaximumSize) {
        this.memoryMaximumSize.set(memoryMaximumSize);
    }

    /**
     * Returns any additional JVM arguments for the compiler process.
     * Defaults to the empty list.
     * @since 1.3
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.jvmArgs")
    public List<String> getJvmArgs() {
        return jvmArgs.get();
    }

    /**
     * Sets any additional JVM arguments for the compiler process.
     * Defaults to the empty list. Empty or null arguments are filtered out because they cause
     * JVM Launch to fail.
     * @since 1.3
     */
    public void setJvmArgs(@Nullable List<String> jvmArgs) {
        this.jvmArgs.set(jvmArgs == null ? null : jvmArgs.stream()
            .filter(Objects::nonNull)
            .filter(string -> !StringUtils.isBlank(string))
            .collect(Collectors.toList()));
    }
}
