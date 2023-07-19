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
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
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

    @Inject
    public AbstractCodeQualityTask() {
        getIgnoreFailuresProperty().convention(false);
        getJavaLauncher().convention(getToolchainService().launcherFor(new CurrentJvmToolchainSpec(getObjectFactory())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getIgnoreFailures() {
        return getIgnoreFailuresProperty().get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.getIgnoreFailuresProperty().set(ignoreFailures);
    }

    @Internal
    abstract protected Property<Boolean> getIgnoreFailuresProperty();

    @Inject
    abstract protected ObjectFactory getObjectFactory();

    @Inject
    abstract protected JavaToolchainService getToolchainService();

    @Inject
    abstract protected WorkerExecutor getWorkerExecutor();

    protected void configureForkOptions(JavaForkOptions forkOptions) {
        forkOptions.setMinHeapSize(getMinHeapSize().getOrNull());
        forkOptions.setMaxHeapSize(getMaxHeapSize().getOrNull());
        forkOptions.setExecutable(getJavaLauncher().get().getExecutablePath().getAsFile().getAbsolutePath());
        maybeAddOpensJvmArgs(getJavaLauncher().get(), forkOptions);
    }

    private static void maybeAddOpensJvmArgs(JavaLauncher javaLauncher, JavaForkOptions forkOptions) {
        if (JavaVersion.toVersion(javaLauncher.getMetadata().getJavaRuntimeVersion()).isJava9Compatible()) {
            forkOptions.jvmArgs("--add-opens", OPEN_MODULES_ARG);
        }
    }

    /**
     * Java launcher used to start the worker process
     */
    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    /**
     * The minimum heap size for the worker process.  When unspecified, no minimum heap size is set.
     *
     * Supports units like the command-line option {@code -Xms} such as {@code "1g"}.
     *
     * @return The minimum heap size.
     */
    @Optional
    @Input
    public abstract Property<String> getMinHeapSize();

    /**
     * The maximum heap size for the worker process.  If unspecified, a maximum heap size will be provided by Gradle.
     *
     * Supports units like the command-line option {@code -Xmx} such as {@code "1g"}.
     *
     * @return The maximum heap size.
     */
    @Optional
    @Input
    public abstract Property<String> getMaxHeapSize();
}
