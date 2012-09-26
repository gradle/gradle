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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import java.util.List;

/**
 * Fork options for Groovy compilation. Only take effect if {@code GroovyCompileOptions.fork}
 * is {@code true}.
 *
 * @author Hans Dockter
 */
public class BaseForkOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private String memoryInitialSize;

    private String memoryMaximumSize;

    private List<String> jvmArgs = Lists.newArrayList();

    /**
     * Returns the initial heap size for the compiler process.
     * Defaults to {@code null}, in which case the JVM's default will be used.
     */
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
    @Input
    @Optional
    public List<String> getJvmArgs() {
        return jvmArgs;
    }

    /**
     * Sets any additional JVM arguments for the compiler process.
     * Defaults to the empty list.
     */
    public void setJvmArgs(List<String> jvmArgs) {
        this.jvmArgs = jvmArgs;
    }

    /**
     * Internal method.
     */
    protected List<String> excludedFieldsFromOptionMap() {
        return ImmutableList.of("jvmArgs");
    }
}
