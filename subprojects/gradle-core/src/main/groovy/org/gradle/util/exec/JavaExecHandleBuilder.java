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
package org.gradle.util.exec;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.util.DefaultJavaForkOptions;
import org.gradle.api.tasks.util.JavaForkOptions;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;

public class JavaExecHandleBuilder extends ExecHandleBuilder implements JavaForkOptions {
    private String mainClass;
    private final List<String> applicationArgs = new ArrayList<String>();
    private final Set<File> classpath = new LinkedHashSet<File>();
    private final JavaForkOptions javaOptions;

    public JavaExecHandleBuilder() {
        this(null);
    }

    public JavaExecHandleBuilder(FileResolver resolver) {
        javaOptions = new DefaultJavaForkOptions(resolver);
        executable(javaOptions.getExecutable());
    }

    public List<String> getAllJvmArgs() {
        List<String> allArgs = new ArrayList<String>();
        allArgs.addAll(javaOptions.getAllJvmArgs());
        if (!classpath.isEmpty()) {
            allArgs.add("-cp");
            allArgs.add(GUtil.join(classpath, File.pathSeparator));
        }
        return allArgs;
    }

    public void setAllJvmArgs(Iterable<?> arguments) {
        throw new UnsupportedOperationException();
    }

    public List<String> getJvmArgs() {
        return javaOptions.getJvmArgs();
    }

    public void setJvmArgs(Iterable<?> arguments) {
        javaOptions.setJvmArgs(arguments);
    }

    public JavaExecHandleBuilder jvmArgs(Iterable<?> arguments) {
        javaOptions.jvmArgs(arguments);
        return this;
    }

    public JavaExecHandleBuilder jvmArgs(Object... arguments) {
        javaOptions.jvmArgs(arguments);
        return this;
    }

    public Map<String, String> getSystemProperties() {
        return javaOptions.getSystemProperties();
    }

    public void setSystemProperties(Map<String, ?> properties) {
        javaOptions.setSystemProperties(properties);
    }

    public JavaExecHandleBuilder systemProperties(Map<String, ?> properties) {
        javaOptions.systemProperties(properties);
        return this;
    }

    public JavaExecHandleBuilder systemProperty(String name, Object value) {
        javaOptions.systemProperty(name, value);
        return this;
    }

    public String getMaxHeapSize() {
        return javaOptions.getMaxHeapSize();
    }

    public void setMaxHeapSize(String heapSize) {
        javaOptions.setMaxHeapSize(heapSize);
    }

    public String getMainClass() {
        return mainClass;
    }

    public JavaExecHandleBuilder mainClass(String mainClassName) {
        this.mainClass = mainClassName;
        return this;
    }

    public List<String> getApplicationArgs() {
        return applicationArgs;
    }

    public JavaExecHandleBuilder applicationArgs(String... args) {
        applicationArgs.addAll(Arrays.asList(args));
        return this;
    }

    public JavaExecHandleBuilder classpath(File... classpath) {
        classpath(Arrays.asList(classpath));
        return this;
    }
    
    public JavaExecHandleBuilder classpath(Collection<File> classpath) {
        this.classpath.addAll(classpath);
        return this;
    }

    public Set<File> getClasspath() {
        return classpath;
    }

    @Override
    public List<String> getArguments() {
        List<String> arguments = new ArrayList<String>();
        arguments.addAll(getAllJvmArgs());
        arguments.add(mainClass);
        arguments.addAll(applicationArgs);
        return arguments;
    }

    @Override
    public ExecHandleBuilder setArguments(List<String> arguments) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExecHandleBuilder arguments(List<String> arguments) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExecHandleBuilder arguments(String... arguments) {
        throw new UnsupportedOperationException();
    }

    public JavaForkOptions copyTo(JavaForkOptions options) {
        throw new UnsupportedOperationException();
    }

    public ExecHandle build() {
        if (mainClass == null) {
            throw new IllegalStateException("No main class specified");
        }
        return super.build();
    }
}
