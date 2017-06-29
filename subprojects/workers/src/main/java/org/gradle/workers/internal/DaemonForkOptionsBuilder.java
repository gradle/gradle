/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;
import org.gradle.process.internal.DefaultJavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsInternal;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DaemonForkOptionsBuilder {
    private final JavaForkOptionsInternal javaForkOptions;
    private final FileResolver fileResolver;
    private Iterable<File> classpath = Collections.emptyList();
    private Iterable<String> sharedPackages = Collections.emptyList();
    private KeepAliveMode keepAliveMode = KeepAliveMode.DAEMON;

    public DaemonForkOptionsBuilder(FileResolver resolver) {
        this.fileResolver = resolver;
        javaForkOptions = new DefaultJavaForkOptions(resolver);
    }

    public DaemonForkOptionsBuilder classpath(Iterable<File> classpath) {
        this.classpath = classpath;
        return this;
    }

    public DaemonForkOptionsBuilder sharedPackages(Iterable<String> sharedPackages) {
        this.sharedPackages = sharedPackages;
        return this;
    }

    public DaemonForkOptionsBuilder keepAliveMode(KeepAliveMode keepAliveMode) {
        this.keepAliveMode = keepAliveMode;
        return this;
    }

    public DaemonForkOptionsBuilder javaForkOptions(JavaForkOptions javaForkOptions) {
        javaForkOptions.copyTo(this.javaForkOptions);
        return this;
    }

    public DaemonForkOptions build() {
        return new DaemonForkOptions(buildJavaForkOptions(), classpath, sharedPackages, keepAliveMode);
    }

    private ImmutableJavaForkOptions buildJavaForkOptions() {
        JavaForkOptionsInternal delegate = new DefaultJavaForkOptions(fileResolver);
        javaForkOptions.copyTo(delegate);
        return new ImmutableJavaForkOptions(delegate);
    }

    private static class ImmutableJavaForkOptions implements JavaForkOptionsInternal {
        private final JavaForkOptionsInternal delegate;

        public ImmutableJavaForkOptions(JavaForkOptionsInternal delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getExecutable() {
            return delegate.getExecutable();
        }

        @Override
        public void setExecutable(String executable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> getSystemProperties() {
            return ImmutableMap.copyOf(delegate.getSystemProperties());
        }

        @Override
        public void setExecutable(Object executable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSystemProperties(Map<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessForkOptions executable(Object executable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JavaForkOptions systemProperties(Map<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getWorkingDir() {
            return delegate.getWorkingDir();
        }

        @Override
        public void setWorkingDir(File dir) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JavaForkOptions systemProperty(String name, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setWorkingDir(Object dir) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDefaultCharacterEncoding() {
            return delegate.getDefaultCharacterEncoding();
        }

        @Override
        public ProcessForkOptions workingDir(Object dir) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> getEnvironment() {
            return ImmutableMap.copyOf(delegate.getEnvironment());
        }

        @Override
        public void setEnvironment(Map<String, ?> environmentVariables) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessForkOptions environment(Map<String, ?> environmentVariables) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getMinHeapSize() {
            return delegate.getMinHeapSize();
        }

        @Override
        public void setMinHeapSize(String heapSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessForkOptions environment(String name, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessForkOptions copyTo(ProcessForkOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getMaxHeapSize() {
            return delegate.getMaxHeapSize();
        }

        @Override
        public void setMaxHeapSize(String heapSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> getJvmArgs() {
            return ImmutableList.copyOf(delegate.getJvmArgs());
        }

        @Override
        public void setJvmArgs(List<String> arguments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setJvmArgs(Iterable<?> arguments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JavaForkOptions jvmArgs(Iterable<?> arguments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JavaForkOptions jvmArgs(Object... arguments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileCollection getBootstrapClasspath() {
            return delegate.getBootstrapClasspath();
        }

        @Override
        public void setBootstrapClasspath(FileCollection classpath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JavaForkOptions bootstrapClasspath(Object... classpath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getEnableAssertions() {
            return delegate.getEnableAssertions();
        }

        @Override
        public void setEnableAssertions(boolean enabled) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getDebug() {
            return delegate.getDebug();
        }

        @Override
        public void setDebug(boolean enabled) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> getAllJvmArgs() {
            return ImmutableList.copyOf(delegate.getAllJvmArgs());
        }

        @Override
        public void setAllJvmArgs(List<String> arguments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setAllJvmArgs(Iterable<?> arguments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JavaForkOptions copyTo(JavaForkOptions options) {
            return delegate.copyTo(options);
        }

        @Override
        public JavaForkOptionsInternal mergeWith(JavaForkOptions options) {
            return new ImmutableJavaForkOptions(delegate.mergeWith(options));
        }

        @Override
        public boolean isCompatibleWith(JavaForkOptions options) {
            return delegate.isCompatibleWith(options);
        }
    }
}
