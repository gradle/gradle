/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.provider.connection;

import org.gradle.api.logging.LogLevel;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.tooling.internal.protocol.InternalLaunchable;
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Defines what information is needed on the provider side regarding the build operation.
 *
 * This is used as an adapter over the {@link org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters} instance provided by the consumer.
 *
 * Adding a new parameter and exposing it to TAPI clients requires declaring a getter here,
 * adding it to {@code ConsumerOperationParameters},
 * and updating the public {@link org.gradle.tooling.LongRunningOperation}.
 *
 * For backwards compatibility, the provider need to provide a default value.
 * Let the getter take a parameter of the same type as its return type.
 * When the getter is called and {@code ConsumerOperationParameters} does not have a corresponding getter (without a parameter), the default value from the parameter is returned immediately.
 */
public interface ProviderOperationParameters {
    boolean getVerboseLogging();

    LogLevel getBuildLogLevel();

    /**
     * @return When null, assume empty stdin (rather than consume from the current process' stdin).
     */
    @Nullable
    InputStream getStandardInput();

    /**
     * @return When null, use the provider's default Java home.
     */
    @Nullable
    File getJavaHome();

    /**
     * Returns the concatenated list of {@link #getBaseJvmArguments()} and {@link #getAdditionalJvmArguments()}. The method is only kept for backwards compatibility (see #31462) to support Gradle versions where {@link #getBaseJvmArguments()} and  {@link #getAdditionalJvmArguments()} is not yet available.
     *
     * @return null if no JVM arguments are provided, otherwise a concatenated list of JVM arguments.
     */
    @Nullable
    List<String> getJvmArguments();

    /**
     * Arguments, which will override the default JVM arguments.
     *
     * @return null if no JVM arguments are provided, otherwise a list of JVM arguments.
     */
    @Nullable
    List<String> getBaseJvmArguments();

    /**
     * Additional arguments, which will be appended to the default/overridden JVM arguments.
     *
     * @return null if no additional JVM arguments are provided, otherwise a list of additional JVM arguments.
     */
    @Nullable
    List<String> getAdditionalJvmArguments();

    /**
     * Returns the environment variables.
     *
     * @return When null, use the provider's default environment variables. When empty, use no environment variables.
     * @since 3.5
     */
    @Nullable
    Map<String, String> getEnvironmentVariables(@Nullable Map<String, String> defaultValue);

    /**
     * Returns the start time.
     *
     * @since 1.0
     */
    long getStartTime();

    /**
     * Returns the gradle user home dir.
     *
     * @return When null, use the provider's default Gradle user home dir.
     * @since 1.0
     */
    @Nullable
    File getGradleUserHomeDir();

    /**
     * Returns the project dir.
     *
     * @since 1.0
     */
    File getProjectDir();


    /**
     * Returns whether embedded is set.
     *
     * @return When null, use the provider's default value for embedded.
     * @since 1.0
     */
    @Nullable
    Boolean isEmbedded();

    /**
     * Returns whether color output is set.
     *
     * @return When null, use the provider's default value for color output.
     * @since 2.3
     */
    Boolean isColorOutput();

    /**
     * Returns the standard output.
     *
     * @return When null, discard the stdout (rather than forward to the current process' stdout)
     * @since 1.0
     */
    @Nullable
    OutputStream getStandardOutput();

    /**
     * Returns the standard error.
     *
     * @return When null, discard the stderr (rather than forward to the current process' stdout)
     * @since 1.0
     */
    @Nullable
    OutputStream getStandardError();

    /**
     * Returns the daemon max idle time value.
     *
     * @return When null, use the provider's default daemon idle timeout
     * @since 1.0
     */
    @Nullable
    Integer getDaemonMaxIdleTimeValue();

    /**
     * Returns the daemon max idle time units.
     *
     * @return Must not return null when {@link #getDaemonMaxIdleTimeValue()} returns a non-null value. Otherwise, unspecified.
     * @since 1.0
     */
    @Nullable
    TimeUnit getDaemonMaxIdleTimeUnits();

    /**
     * Returns the daemon base dir.
     *
     * @return When null, use the provider's default daemon base dir.
     * @since 2.2
     */
    @Nullable
    File getDaemonBaseDir();

    /**
     * Returns the progress listener.
     *
     * @since 1.0
     */
    ProgressListenerVersion1 getProgressListener();

    /**
     * Returns the build progress listener.
     *
     * @return When null, do not forward any build progress events.
     * @since 2.4
     */
    @Nullable
    InternalBuildProgressListener getBuildProgressListener();

    /**
     * @return When null, assume no arguments.
     */
    @Nullable
    List<String> getArguments();

    /**
     * @return When null, no tasks should be run. When empty, use the default tasks
     */
    @Nullable
    List<String> getTasks();

    /**
     * Returns the launchables.
     *
     * @since 1.12
     */
    List<InternalLaunchable> getLaunchables();

    /**
     * Returns the injected plugin classpath.
     *
     * @return When empty, do not inject a plugin classpath.
     * @since 2.8
     */
    List<File> getInjectedPluginClasspath();

    /**
     * Returns the system properties.
     *
     * @return Additional system properties defined by the client to be available in the build.
     * @since 7.6
     */
    Map<String, String> getSystemProperties(Map<String, String> defaultValue);

    /**
     * Handles a value streamed from the build action. Blocks until the value has been handled.
     *
     * <p>This method is called from the provider's message handling loop in the client process, so is required to block until handling is complete so as to preserve
     * the message ordering.</p>
     *
     * @since 8.6
     */
    void onStreamedValue(Object value);
}
