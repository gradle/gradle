/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.api.NonNullApi;
import org.gradle.process.JavaForkOptions;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.gradle.process.internal.util.MergeOptionsUtil.containsAll;
import static org.gradle.process.internal.util.MergeOptionsUtil.getHeapSizeMb;
import static org.gradle.process.internal.util.MergeOptionsUtil.normalized;

/**
 * Represents effective options for forking a Java process.
 *
 * Strongly relates to {@link JavaForkOptions}.
 */
@NonNullApi
public class JvmForkOptions {

    private final JvmOptions jvmOptions;
    private final String executable;
    private final File workingDir;
    private final Map<String, Object> environment;

    public JvmForkOptions(String executable, File workingDir, Map<String, Object> environment, JvmOptions jvmOptions) {
        this.jvmOptions = jvmOptions;
        this.executable = executable;
        this.workingDir = workingDir;
        this.environment = new LinkedHashMap<>(environment);
    }

    public File getWorkingDir() {
        return workingDir;
    }

    public String getExecutable() {
        return executable;
    }

    public Map<String, Object> getEnvironment() {
        return environment;
    }

    public JvmOptions getJvmOptions() {
        return jvmOptions;
    }

    /**
     * Returns true if the given options are compatible with this set of options.
     */
    public boolean isCompatibleWith(JvmForkOptions forkOptions) {
        return jvmOptions.getDebug() == forkOptions.getJvmOptions().getDebug()
            && jvmOptions.getEnableAssertions() == forkOptions.getJvmOptions().getEnableAssertions()
            && normalized(executable).equals(normalized(forkOptions.getExecutable()))
            && workingDir.equals(forkOptions.getWorkingDir())
            && normalized(jvmOptions.getDefaultCharacterEncoding()).equals(normalized(forkOptions.getJvmOptions().getDefaultCharacterEncoding()))
            && getHeapSizeMb(jvmOptions.getMinHeapSize()) >= getHeapSizeMb(forkOptions.getJvmOptions().getMinHeapSize())
            && getHeapSizeMb(jvmOptions.getMaxHeapSize()) >= getHeapSizeMb(forkOptions.getJvmOptions().getMaxHeapSize())
            && normalized(jvmOptions.getJvmArgs()).containsAll(normalized(forkOptions.getJvmOptions().getJvmArgs()))
            && containsAll(jvmOptions.getMutableSystemProperties(), forkOptions.getJvmOptions().getMutableSystemProperties())
            && containsAll(environment, forkOptions.getEnvironment())
            && jvmOptions.getBootstrapClasspath().getFiles().containsAll(forkOptions.getJvmOptions().getBootstrapClasspath().getFiles());
    }

    public void copyTo(JavaForkOptions target) {
        target.setExecutable(executable);
        target.setWorkingDir(workingDir);
        target.setEnvironment(environment);
        jvmOptions.copyTo(target);
    }
}
