/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.JavaForkOptions;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class DefaultJavaForkOptions extends DefaultProcessForkOptions implements JavaForkOptions {
    private final JvmOptions options;

    public DefaultJavaForkOptions(FileResolver resolver) {
        this(resolver, Jvm.current());
    }

    public DefaultJavaForkOptions(FileResolver resolver, Jvm jvm) {
        super(resolver);
        options = new JvmOptions(resolver);
        setExecutable(jvm.getJavaExecutable());
    }

    public List<String> getAllJvmArgs() {
        return options.getAllJvmArgs();
    }

    public void setAllJvmArgs(Iterable<?> arguments) {
        options.setAllJvmArgs(arguments);
    }

    public List<String> getJvmArgs() {
        return options.getJvmArgs();
    }

    public void setJvmArgs(Iterable<?> arguments) {
        options.setJvmArgs(arguments);
    }

    public JavaForkOptions jvmArgs(Iterable<?> arguments) {
        options.jvmArgs(arguments);
        return this;
    }

    public JavaForkOptions jvmArgs(Object... arguments) {
        jvmArgs(Arrays.asList(arguments));
        return this;
    }

    public Map<String, Object> getSystemProperties() {
        return options.getSystemProperties();
    }

    public void setSystemProperties(Map<String, ?> properties) {
        options.setSystemProperties(properties);
    }

    public JavaForkOptions systemProperties(Map<String, ?> properties) {
        options.systemProperties(properties);
        return this;
    }

    public JavaForkOptions systemProperty(String name, Object value) {
        options.systemProperty(name, value);
        return this;
    }

    public FileCollection getBootstrapClasspath() {
        return options.getBootstrapClasspath();
    }

    public void setBootstrapClasspath(FileCollection classpath) {
        options.setBootstrapClasspath(classpath);
    }

    public JavaForkOptions bootstrapClasspath(Object... classpath) {
        options.bootstrapClasspath(classpath);
        return this;
    }

    public String getMinHeapSize() {
        return options.getMinHeapSize();
    }

    public void setMinHeapSize(String heapSize) {
        options.setMinHeapSize(heapSize);
    }

    public String getMaxHeapSize() {
        return options.getMaxHeapSize();
    }

    public void setMaxHeapSize(String heapSize) {
        options.setMaxHeapSize(heapSize);
    }

    public String getDefaultCharacterEncoding() {
        return options.getDefaultCharacterEncoding();
    }

    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        options.setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    public boolean getEnableAssertions() {
        return options.getEnableAssertions();
    }

    public void setEnableAssertions(boolean enabled) {
        options.setEnableAssertions(enabled);
    }

    public boolean getDebug() {
        return options.getDebug();
    }

    public void setDebug(boolean enabled) {
        options.setDebug(enabled);
    }

    public JavaForkOptions copyTo(JavaForkOptions target) {
        super.copyTo(target);
        options.copyTo(target);
        return this;
    }
}
