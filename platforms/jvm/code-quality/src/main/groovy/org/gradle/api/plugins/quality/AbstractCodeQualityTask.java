/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.plugins.quality;

import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec;
import org.gradle.process.JavaForkOptions;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;

/**
 * Base class for code quality tasks.
 *
 * @since 8.4
 */
@Incubating
@DisableCachingByDefault(because = "Super-class, not to be instantiated directly")
abstract public class AbstractCodeQualityTask extends SourceTask implements VerificationTask {
    private static final String OPEN_MODULES_ARG = "java.prefs/java.util.prefs=ALL-UNNAMED";
    private final Property<JavaLauncher> javaLauncher;
    private final Property<String> minHeapSize;
    private final Property<String> maxHeapSize;
    // TODO - Convert VerificationTask to use property-based API and deprecate boolean methods
    private final Property<Boolean> ignoreFailures;
    private final MapProperty<String, String> systemProperties;
    private final MapProperty<String, String> environment;

    @Inject
    public AbstractCodeQualityTask() {
        this.javaLauncher = configureFromCurrentJvmLauncher(getToolchainService(), getObjectFactory());
        this.minHeapSize = getObjectFactory().property(String.class);
        this.maxHeapSize = getObjectFactory().property(String.class);
        this.ignoreFailures = getObjectFactory().property(Boolean.class).convention(false);
        this.systemProperties = getObjectFactory().mapProperty(String.class, String.class);
        this.environment = getObjectFactory().mapProperty(String.class, String.class);
    }

    /**
     * JavaLauncher for toolchain support
     */
    @Nested
    public Property<JavaLauncher> getJavaLauncher() {
        return javaLauncher;
    }

    /**
     * The minimum heap size for the worker process, if any.
     * Supports the units megabytes (e.g. "512m") and gigabytes (e.g. "1g").
     *
     * @return The minimum heap size. Value should be null if the default minimum heap size should be used.
     */
    @Optional
    @Input
    public Property<String> getMinHeapSize() {
        return minHeapSize;
    }

    /**
     * The maximum heap size for the worker process, if any.
     * Supports the units megabytes (e.g. "512m") and gigabytes (e.g. "1g").
     *
     * @return The minimum heap size. Value should be null if the default maximum heap size should be used.
     */
    @Optional
    @Input
    public Property<String> getMaxHeapSize() {
        return maxHeapSize;
    }

    /**
     * The system properties to pass to the worker process, if any.
     *
     * @return The system properties.
     */
    @Optional
    @Input
    public MapProperty<String, String> getSystemProperties() {
        return systemProperties;
    }

    /**
     * The environment variables to pass to the worker process, if any.
     *
     * @return The environment variables.
     */
    @Optional
    @Input
    public MapProperty<String, String> getEnvironment() {
        return environment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getIgnoreFailures() {
        return ignoreFailures.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures.set(ignoreFailures);
    }

    @Inject
    abstract protected ObjectFactory getObjectFactory();

    @Inject
    abstract protected JavaToolchainService getToolchainService();

    @Inject
    abstract protected WorkerExecutor getWorkerExecutor();

    protected void configureForkOptions(JavaForkOptions forkOptions) {
        forkOptions.setMinHeapSize(getMinHeapSize().getOrNull());
        forkOptions.setMaxHeapSize(getMaxHeapSize().getOrNull());
        forkOptions.getSystemProperties().putAll(getSystemProperties().get());
        forkOptions.getEnvironment().putAll(getEnvironment().get());
        forkOptions.setExecutable(getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath());
        maybeAddOpensJvmArgs(getJavaLauncher().get(), forkOptions);
    }

    private static Property<JavaLauncher> configureFromCurrentJvmLauncher(JavaToolchainService toolchainService, ObjectFactory objectFactory) {
        Provider<JavaLauncher> currentJvmLauncherProvider = toolchainService.launcherFor(new CurrentJvmToolchainSpec(objectFactory));
        return objectFactory.property(JavaLauncher.class).convention(currentJvmLauncherProvider);
    }

    private static void maybeAddOpensJvmArgs(JavaLauncher javaLauncher, JavaForkOptions forkOptions) {
        if (JavaVersion.toVersion(javaLauncher.getMetadata().getJavaRuntimeVersion()).isJava9Compatible()) {
            forkOptions.jvmArgs("--add-opens", OPEN_MODULES_ARG);
        }
    }
}
