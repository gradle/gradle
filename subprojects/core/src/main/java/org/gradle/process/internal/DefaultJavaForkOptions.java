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

import com.google.common.collect.Maps;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.jvm.Jvm;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaForkOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.process.internal.util.MergeOptionsUtil.canBeMerged;
import static org.gradle.process.internal.util.MergeOptionsUtil.containsAll;
import static org.gradle.process.internal.util.MergeOptionsUtil.getHeapSizeMb;
import static org.gradle.process.internal.util.MergeOptionsUtil.mergeHeapSize;
import static org.gradle.process.internal.util.MergeOptionsUtil.normalized;

public class DefaultJavaForkOptions extends DefaultProcessForkOptions implements JavaForkOptionsInternal {
    private final PathToFileResolver resolver;
    private final JvmOptions options;
    private List<CommandLineArgumentProvider> jvmArgumentProviders;

    public DefaultJavaForkOptions(PathToFileResolver resolver) {
        this(resolver, Jvm.current());
    }

    public DefaultJavaForkOptions(PathToFileResolver resolver, Jvm jvm) {
        super(resolver);
        this.resolver = resolver;
        options = new JvmOptions(resolver);
        setExecutable(jvm.getJavaExecutable());
    }

    public List<String> getAllJvmArgs() {
        if (hasJvmArgumentProviders(this)) {
            JvmOptions copy = options.createCopy();
            for (CommandLineArgumentProvider jvmArgumentProvider : jvmArgumentProviders) {
                copy.jvmArgs(jvmArgumentProvider.asArguments());
            }
            return copy.getAllJvmArgs();
        } else {
            return options.getAllJvmArgs();
        }
    }

    public void setAllJvmArgs(List<String> arguments) {
        options.setAllJvmArgs(arguments);
        if (hasJvmArgumentProviders(this)) {
            jvmArgumentProviders.clear();
        }
    }

    public void setAllJvmArgs(Iterable<?> arguments) {
        options.setAllJvmArgs(arguments);
        if (hasJvmArgumentProviders(this)) {
            jvmArgumentProviders.clear();
        }
    }

    public List<String> getJvmArgs() {
        return options.getJvmArgs();
    }

    public void setJvmArgs(List<String> arguments) {
        options.setJvmArgs(arguments);
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

    @Override
    public List<CommandLineArgumentProvider> getJvmArgumentProviders() {
        if (jvmArgumentProviders == null) {
            jvmArgumentProviders = new ArrayList<CommandLineArgumentProvider>();
        }
        return jvmArgumentProviders;
    }

    public Map<String, Object> getSystemProperties() {
        return options.getMutableSystemProperties();
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
        if (jvmArgumentProviders != null) {
            for (CommandLineArgumentProvider jvmArgumentProvider : jvmArgumentProviders) {
                target.jvmArgs(jvmArgumentProvider.asArguments());
            }
        }
        return this;
    }

    @Override
    public JavaForkOptionsInternal mergeWith(JavaForkOptions options) {
        if (hasJvmArgumentProviders(this) || hasJvmArgumentProviders(options)) {
            throw new UnsupportedOperationException("Cannot merge options with jvmArgumentProviders.");
        }
        JavaForkOptionsInternal mergedOptions = new DefaultJavaForkOptions(resolver);

        if (!canBeMerged(getExecutable(), options.getExecutable())) {
            throw new IllegalArgumentException("Cannot merge a fork options object with a different executable (this: " + getExecutable() + ", other: " + options.getExecutable() + ").");
        } else {
            mergedOptions.setExecutable(getExecutable() != null ? getExecutable() : options.getExecutable());
        }

        if (!canBeMerged(getWorkingDir(), options.getWorkingDir())) {
            throw new IllegalArgumentException("Cannot merge a fork options object with a different working directory (this: " + getWorkingDir() + ", other: " + options.getWorkingDir() + ").");
        } else {
            mergedOptions.setWorkingDir(getWorkingDir() != null ? getWorkingDir() : options.getWorkingDir());
        }

        if (!canBeMerged(getDefaultCharacterEncoding(), options.getDefaultCharacterEncoding())) {
            throw new IllegalArgumentException("Cannot merge a fork options object with a different default character encoding (this: " + getDefaultCharacterEncoding() + ", other: " + options.getDefaultCharacterEncoding() + ").");
        } else {
            mergedOptions.setDefaultCharacterEncoding(getDefaultCharacterEncoding() != null ? getDefaultCharacterEncoding() : options.getDefaultCharacterEncoding());
        }

        mergedOptions.setDebug(getDebug() || options.getDebug());
        mergedOptions.setEnableAssertions(getEnableAssertions() || options.getEnableAssertions());

        mergedOptions.setMinHeapSize(mergeHeapSize(getMinHeapSize(), options.getMinHeapSize()));
        mergedOptions.setMaxHeapSize(mergeHeapSize(getMaxHeapSize(), options.getMaxHeapSize()));

        Set<String> mergedJvmArgs = normalized(getJvmArgs());
        mergedJvmArgs.addAll(normalized(options.getJvmArgs()));
        mergedOptions.setJvmArgs(mergedJvmArgs);

        Map<String, Object> mergedEnvironment = Maps.newHashMap(getEnvironment());
        mergedEnvironment.putAll(options.getEnvironment());
        mergedOptions.setEnvironment(mergedEnvironment);

        Map<String, Object> mergedSystemProperties = Maps.newHashMap(getSystemProperties());
        mergedSystemProperties.putAll(options.getSystemProperties());
        mergedOptions.setSystemProperties(mergedSystemProperties);

        mergedOptions.setBootstrapClasspath(new UnionFileCollection(getBootstrapClasspath(), options.getBootstrapClasspath()));

        return mergedOptions;
    }

    @Override
    public boolean isCompatibleWith(JavaForkOptions options) {
        if (hasJvmArgumentProviders(this) || hasJvmArgumentProviders(options)) {
            throw new UnsupportedOperationException("Cannot compare options with jvmArgumentProviders.");
        }
        return getDebug() == options.getDebug()
                && getEnableAssertions() == options.getEnableAssertions()
                && normalized(getExecutable()).equals(normalized(options.getExecutable()))
                && getWorkingDir().equals(options.getWorkingDir())
                && normalized(getDefaultCharacterEncoding()).equals(normalized(options.getDefaultCharacterEncoding()))
                && getHeapSizeMb(getMinHeapSize()) >= getHeapSizeMb(options.getMinHeapSize())
                && getHeapSizeMb(getMaxHeapSize()) >= getHeapSizeMb(options.getMaxHeapSize())
                && normalized(getJvmArgs()).containsAll(normalized(options.getJvmArgs()))
                && containsAll(getSystemProperties(), options.getSystemProperties())
                && containsAll(getEnvironment(), options.getEnvironment())
                && getBootstrapClasspath().getFiles().containsAll(options.getBootstrapClasspath().getFiles());
    }

    private static boolean hasJvmArgumentProviders(JavaForkOptions forkOptions) {
        return forkOptions instanceof DefaultJavaForkOptions
            && hasJvmArgumentProviders((DefaultJavaForkOptions) forkOptions);
    }

    private static boolean hasJvmArgumentProviders(DefaultJavaForkOptions forkOptions) {
        return forkOptions.jvmArgumentProviders != null && !forkOptions.jvmArgumentProviders.isEmpty();
    }

}
