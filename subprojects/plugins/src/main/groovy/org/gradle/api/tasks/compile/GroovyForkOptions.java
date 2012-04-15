/*
 * Copyright 2007-2008 the original author or authors.
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

import java.util.List;

/**
 * Fork options for Groovy compilation.
 *
 * @author Hans Dockter
 */
public class GroovyForkOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    private String memoryInitialSize;

    private String memoryMaximumSize;

    /**
     * Any additional JVM arguments for the compiler process.
     */
    private List<String> jvmArgs = Lists.newArrayList();

    public String getMemoryInitialSize() {
        return memoryInitialSize;
    }

    public void setMemoryInitialSize(String memoryInitialSize) {
        this.memoryInitialSize = memoryInitialSize;
    }

    public String getMemoryMaximumSize() {
        return memoryMaximumSize;
    }

    public void setMemoryMaximumSize(String memoryMaximumSize) {
        this.memoryMaximumSize = memoryMaximumSize;
    }

    public List<String> getJvmArgs() {
        return jvmArgs;
    }

    public void setJvmArgs(List<String> jvmArgs) {
        this.jvmArgs = jvmArgs;
    }

    public List<String> excludedFieldsFromOptionMap() {
        return ImmutableList.of("jvmArgs");
    }
}
