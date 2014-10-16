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
import org.gradle.tooling.internal.protocol.InternalLaunchable;
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Defines what information is needed on the provider side regarding the build operation.
 */
public interface ProviderOperationParameters {
    boolean getVerboseLogging(boolean defaultValue);

    LogLevel getBuildLogLevel();

    InputStream getStandardInput(InputStream defaultInput);

    File getJavaHome(File defaultJavaHome);

    List<String> getJvmArguments(List<String> defaultJvmArgs);

    long getStartTime();

    File getGradleUserHomeDir();

    File getProjectDir();

    Boolean isSearchUpwards();

    Boolean isEmbedded();

    OutputStream getStandardOutput();

    OutputStream getStandardError();

    Integer getDaemonMaxIdleTimeValue();

    TimeUnit getDaemonMaxIdleTimeUnits();

    File getDaemonBaseDir(File defaultDaemonBaseDir);

    ProgressListenerVersion1 getProgressListener();

    List<String> getArguments(List<String> defaultArguments);

    List<String> getTasks();

    List<InternalLaunchable> getLaunchables(List<InternalLaunchable> defaultLaunchables);
}
