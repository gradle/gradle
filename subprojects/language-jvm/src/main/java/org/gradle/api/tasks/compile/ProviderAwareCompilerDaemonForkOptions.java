/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.ProviderAwareJvmForkOptions;
import org.gradle.process.internal.ProviderAwareJvmOptions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Fork options for compilation that can accept user-defined {@link CommandLineArgumentProvider} objects.
 *
 * Only take effect if {@code fork} is {@code true}.
 *
 * @since 7.1
 */
@Incubating
public class ProviderAwareCompilerDaemonForkOptions extends BaseForkOptions implements ProviderAwareJvmForkOptions {

    private final ProviderAwareJvmOptions options;

    public ProviderAwareCompilerDaemonForkOptions(ProviderAwareJvmOptions options) {
        super();
        this.options = options;
    }

    @Internal
    @Override
    public Map<String, Object> getSystemProperties() {
        return options.getMutableSystemProperties();
    }

    @Override
    public void setSystemProperties(Map<String, ?> properties) {
        options.setSystemProperties(properties);
    }

    @Override
    public ProviderAwareCompilerDaemonForkOptions systemProperties(Map<String, ?> properties) {
        options.systemProperties(properties);
        return this;
    }

    @Override
    public ProviderAwareCompilerDaemonForkOptions systemProperty(String name, Object value) {
        options.systemProperty(name, value);
        return this;
    }

    @Nullable
    @Override
    public String getDefaultCharacterEncoding() {
        return options.getDefaultCharacterEncoding();
    }

    @Override
    public void setDefaultCharacterEncoding(@Nullable String defaultCharacterEncoding) {
        options.setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    @Nullable
    @Override
    public String getMinHeapSize() {
        return options.getMinHeapSize();
    }

    @Override
    public void setMinHeapSize(@Nullable String heapSize) {
        options.setMinHeapSize(heapSize);
    }

    @Nullable
    @Override
    public String getMaxHeapSize() {
        return options.getMaxHeapSize();
    }

    @Override
    public void setMaxHeapSize(@Nullable String heapSize) {
        options.setMaxHeapSize(heapSize);
    }

    @Override
    public void setJvmArgs(@Nullable Iterable<?> arguments) {
        options.setJvmArgs(arguments);
    }

    @Override
    public ProviderAwareCompilerDaemonForkOptions jvmArgs(Iterable<?> arguments) {
        options.jvmArgs(arguments);
        return this;
    }

    @Override
    public ProviderAwareCompilerDaemonForkOptions jvmArgs(Object... arguments) {
        options.jvmArgs(arguments);
        return this;
    }

    @Override
    public FileCollection getBootstrapClasspath() {
        return options.getBootstrapClasspath();
    }

    @Override
    public void setBootstrapClasspath(FileCollection classpath) {
        options.setBootstrapClasspath(classpath);
    }

    @Override
    public ProviderAwareCompilerDaemonForkOptions bootstrapClasspath(Object... classpath) {
        options.bootstrapClasspath(classpath);
        return this;
    }

    @Override
    public boolean getEnableAssertions() {
        return options.getEnableAssertions();
    }

    @Override
    public void setEnableAssertions(boolean enabled) {
        options.setEnableAssertions(enabled);
    }

    @Override
    public boolean getDebug() {
        return options.getDebug();
    }

    @Override
    public void setDebug(boolean enabled) {
        options.setDebug(enabled);
    }

    @Override
    public JavaDebugOptions getDebugOptions() {
        return options.getDebugOptions();
    }

    @Override
    public void debugOptions(Action<JavaDebugOptions> action) {
        action.execute(options.getDebugOptions());
    }

    @Override
    public List<String> getAllJvmArgs() {
        return options.getAllJvmArgs();
    }

    @Override
    public void setAllJvmArgs(List<String> arguments) {
        options.setAllJvmArgs(arguments);
    }

    @Override
    public void setAllJvmArgs(Iterable<?> arguments) {
        options.setAllJvmArgs(arguments);
    }

    /**
     * Returns any additional JVM argument providers for the compiler process.
     *
     */
    @Optional
    @Nested
    public List<CommandLineArgumentProvider> getJvmArgumentProviders() {
        return options.getJvmArgumentProviders();
    }

    /**
     * Returns the initial heap size for the compiler process.
     * Defaults to {@code null}, in which case the JVM's default will be used.
     */
    @Internal
    @Deprecated
    @Override
    public String getMemoryInitialSize() {
        DeprecationLogger.deprecateMethod(BaseForkOptions.class, "getMemoryInitialSize")
            .replaceWith("getMinHeapSize")
            .willBeRemovedInGradle8()
            .withDslReference(BaseForkOptions.class, "minHeapSize")
            .nagUser();
        return options.getMinHeapSize();
    }

    /**
     * Sets the initial heap size for the compiler process.
     * Defaults to {@code null}, in which case the JVM's default will be used.
     */
    @Deprecated
    @Override
    public void setMemoryInitialSize(String memoryInitialSize) {
        DeprecationLogger.deprecateMethod(BaseForkOptions.class, "setMemoryInitialSize")
            .replaceWith("setMinHeapSize")
            .willBeRemovedInGradle8()
            .withDslReference(BaseForkOptions.class, "minHeapSize")
            .nagUser();
        options.setMinHeapSize(memoryInitialSize);
    }

    /**
     * Returns the maximum heap size for the compiler process.
     * Defaults to {@code null}, in which case the JVM's default will be used.
     */
    @Internal
    @Deprecated
    @Override
    public String getMemoryMaximumSize() {
        DeprecationLogger.deprecateMethod(BaseForkOptions.class, "getMemoryMaximumSize")
            .replaceWith("getMaxHeapSize")
            .willBeRemovedInGradle8()
            .withDslReference(BaseForkOptions.class, "maxHeapSize")
            .nagUser();
        return options.getMaxHeapSize();
    }

    /**
     * Sets the maximum heap size for the compiler process.
     * Defaults to {@code null}, in which case the JVM's default will be used.
     */
    @Deprecated
    @Override
    public void setMemoryMaximumSize(String memoryMaximumSize) {
        DeprecationLogger.deprecateMethod(BaseForkOptions.class, "setMemoryMaximumSize")
            .replaceWith("setMaxHeapSize")
            .willBeRemovedInGradle8()
            .withDslReference(BaseForkOptions.class, "maxHeapSize")
            .nagUser();
        options.setMaxHeapSize(memoryMaximumSize);
    }
}
