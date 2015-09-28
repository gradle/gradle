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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Defines what information is needed on the provider side regarding the build operation.
 *
 * This is used as an adapter over the {@link org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters} instance provided by the consumer.
 */
public interface ProviderOperationParameters {
    boolean getVerboseLogging();

    LogLevel getBuildLogLevel();

    InputStream getStandardInput();

    File getJavaHome();

    List<String> getJvmArguments();

    /**
     * @since 1.0-milestone-3
     */
    long getStartTime();

    /**
     * @since 1.0-milestone-3
     */
    File getGradleUserHomeDir();

    /**
     * @since 1.0-milestone-3
     */
    File getProjectDir();

    /**
     * @since 1.0-milestone-3
     */
    Boolean isSearchUpwards();

    /**
     * @since 1.0-milestone-3
     */
    Boolean isEmbedded();

    /**
     * @since 2.3-rc-1
     */
    Boolean isColorOutput(Boolean defaultValue);

    /**
     * @since 1.0-milestone-3
     */
    OutputStream getStandardOutput();

    /**
     * @since 1.0-milestone-3
     */
    OutputStream getStandardError();

    /**
     * @since 1.0-milestone-3
     */
    Integer getDaemonMaxIdleTimeValue();

    /**
     * @since 1.0-milestone-3
     */
    TimeUnit getDaemonMaxIdleTimeUnits();

    /**
     * @since 2.2-rc-1
     */
    File getDaemonBaseDir(File defaultDaemonBaseDir);

    /**
     * @since 1.0-milestone-3
     */
    ProgressListenerVersion1 getProgressListener();

    /**
     * @since 2.4-rc-1
     */
    InternalBuildProgressListener getBuildProgressListener(InternalBuildProgressListener defaultListener);

    List<String> getArguments();

    List<String> getTasks();

    /**
     * @since 1.12-rc-1
     */
    List<InternalLaunchable> getLaunchables(List<InternalLaunchable> defaultLaunchables);

    /**
     * @since 2.8-rc-1
     */
    List<File> getInjectedPluginClasspath(List<File> defaultClasspath);
}
