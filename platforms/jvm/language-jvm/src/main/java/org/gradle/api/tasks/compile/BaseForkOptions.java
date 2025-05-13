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
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Fork options for compilation. Only take effect if {@code fork}
 * is {@code true}.
 */
@SuppressWarnings("deprecation")
public class BaseForkOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private String memoryInitialSize;

    private String memoryMaximumSize;

    private List<String> jvmArgs = new ArrayList<>();

    /**
     * Returns the initial heap size for the compiler process.
     * Defaults to {@code null}, in which case the JVM's default will be used.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public String getMemoryInitialSize() {
        return memoryInitialSize;
    }

    /**
     * Sets the initial heap size for the compiler process.
     * Defaults to {@code null}, in which case the JVM's default will be used.
     */
    public void setMemoryInitialSize(String memoryInitialSize) {
        this.memoryInitialSize = memoryInitialSize;
    }

    /**
     * Returns the maximum heap size for the compiler process.
     * Defaults to {@code null}, in which case the JVM's default will be used.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public String getMemoryMaximumSize() {
        return memoryMaximumSize;
    }

    /**
     * Sets the maximum heap size for the compiler process.
     * Defaults to {@code null}, in which case the JVM's default will be used.
     */
    public void setMemoryMaximumSize(String memoryMaximumSize) {
        this.memoryMaximumSize = memoryMaximumSize;
    }

    /**
     * Returns any additional JVM arguments for the compiler process.
     * Defaults to the empty list.
     */
    @Nullable
    @Optional
    @Input
    @ToBeReplacedByLazyProperty
    public List<String> getJvmArgs() {
        return jvmArgs;
    }

    /**
     * Sets any additional JVM arguments for the compiler process.
     * Defaults to the empty list. Empty or null arguments are filtered out because they cause
     * JVM Launch to fail.
     */
    public void setJvmArgs(@Nullable List<String> jvmArgs) {
        this.jvmArgs = jvmArgs == null ? null : jvmArgs.stream()
            .filter(Objects::nonNull)
            .filter(string -> !StringUtils.isBlank(string))
            .collect(Collectors.toList());
    }
}
