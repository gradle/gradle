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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.process.JavaForkOptions;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.gradle.process.internal.util.MergeOptionsUtil.containsAll;
import static org.gradle.process.internal.util.MergeOptionsUtil.getHeapSizeMb;
import static org.gradle.process.internal.util.MergeOptionsUtil.normalized;

/**
 * Represents effective options for forking a Java process.
 * It intentionally does not expose JvmOptions directly, as JvmOptions is not immutable yet.
 *
 * Strongly relates to {@link JavaForkOptions}.
 */
@NonNullApi
public class EffectiveJavaForkOptions {

    private final String executable;
    private final File workingDir;
    private final Map<String, Object> environment;
    private final ReadOnlyJvmOptions jvmOptions;

    public EffectiveJavaForkOptions(String executable, File workingDir, Map<String, Object> environment, JvmOptions jvmOptions) {
        this.executable = executable;
        this.workingDir = workingDir;
        this.environment = ImmutableMap.copyOf(environment);
        this.jvmOptions = new ReadOnlyJvmOptions(jvmOptions);
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

    public ReadOnlyJvmOptions getJvmOptions() {
        return jvmOptions;
    }

    /**
     * Returns true if the given options are compatible with this set of options.
     */
    public boolean isCompatibleWith(EffectiveJavaForkOptions forkOptions) {
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

    public void copyTo(JavaExecHandleBuilder target) {
        target.setExecutable(executable);
        target.setWorkingDir(workingDir);
        target.setEnvironment(environment);
        target.copyJavaForkOptions(jvmOptions);
    }

    @Override
    public String toString() {
        return "EffectiveJavaForkOptions{" +
            "executable='" + executable + '\'' +
            ", workingDir=" + workingDir +
            ", environment=" + environment +
            ", jvmOptions=" + jvmOptions +
            '}';
    }

    @NonNullApi
    public static class ReadOnlyJvmOptions {
        private final JvmOptions delegate;

        public ReadOnlyJvmOptions(JvmOptions delegate) {
            this.delegate = delegate;
        }

        public String getMinHeapSize() {
            return delegate.getMinHeapSize();
        }

        public String getMaxHeapSize() {
            return delegate.getMaxHeapSize();
        }

        public boolean getDebug() {
            return delegate.getDebug();
        }

        public boolean getEnableAssertions() {
            return delegate.getEnableAssertions();
        }

        public String getDefaultCharacterEncoding() {
            return delegate.getDefaultCharacterEncoding();
        }

        public FileCollection getBootstrapClasspath() {
            return delegate.getBootstrapClasspath();
        }

        public List<String> getJvmArgs() {
            return ImmutableList.copyOf(delegate.getJvmArgs());
        }

        public List<String> getAllJvmArgs() {
            return ImmutableList.copyOf(delegate.getAllJvmArgs());
        }

        public Map<String, Object> getMutableSystemProperties() {
            return ImmutableMap.copyOf(delegate.getMutableSystemProperties());
        }

        public void copyTo(JavaForkOptions target) {
            this.delegate.copyTo(target);
        }
    }
}
