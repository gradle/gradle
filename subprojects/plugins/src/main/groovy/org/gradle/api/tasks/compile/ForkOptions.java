/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

/**
 * Fork options for Java compilation.
 *
 * @author Hans Dockter
 */
public class ForkOptions extends AbstractOptions {
    private static final long serialVersionUID = 0;

    /**
     * The executable to use to fork the compiler.
     */
    @Input @Optional
    private String executable;

    public String getExecutable() {
        return executable;
    }

    public void setExecutable(String executable) {
        this.executable = executable;
    }

    /**
     * The initial heap size for the compiler process.
     */
    private String memoryInitialSize;

    public String getMemoryInitialSize() {
        return memoryInitialSize;
    }

    public void setMemoryInitialSize(String memoryInitialSize) {
        this.memoryInitialSize = memoryInitialSize;
    }

    /**
     * The maximum heap size for the compiler process.
     */
    private String memoryMaximumSize;

    public String getMemoryMaximumSize() {
        return memoryMaximumSize;
    }

    public void setMemoryMaximumSize(String memoryMaximumSize) {
        this.memoryMaximumSize = memoryMaximumSize;
    }

    /**
   * Directory for temporary files. Only used if compilation is done by an
   * underlying Ant javac task, happens in a forked process, and the command
   * line args length exceeds 4k. Defaults to <tt>java.io.tmpdir</tt>.
   */
    private String tempDir;

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    /**
     * Any additional JVM arguments for the compiler process.
     */
    private List<String> jvmArgs = Lists.newArrayList();

    public List<String> getJvmArgs() {
        return jvmArgs;
    }

    public void setJvmArgs(List<String> jvmArgs) {
        this.jvmArgs = jvmArgs;
    }

    public Map<String, String> fieldName2AntMap() {
        return ImmutableMap.of("tempDir", "tempdir");
    }

    public List<String> excludedFieldsFromOptionMap() {
        return ImmutableList.of("jvmArgs");
    }
}
