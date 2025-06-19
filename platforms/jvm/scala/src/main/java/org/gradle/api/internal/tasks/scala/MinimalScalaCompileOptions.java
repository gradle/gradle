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

package org.gradle.api.internal.tasks.scala;

import com.google.common.collect.ImmutableList;
import org.gradle.language.scala.tasks.KeepAliveMode;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * Immutable, transportable options for configuring Scala compilation,
 * which is serialized over to Scala compiler daemons.
 */
public class MinimalScalaCompileOptions implements Serializable {

    private final boolean failOnError;
    private final boolean deprecation;
    private final boolean unchecked;
    private final String debugLevel;
    private final boolean optimize;
    private final String encoding;
    private final boolean force;
    private final ImmutableList<String> additionalParameters;
    private final boolean listFiles;
    private final String loggingLevel;
    private final ImmutableList<String> loggingPhases;
    private final MinimalScalaCompilerDaemonForkOptions forkOptions;
    private final MinimalIncrementalCompileOptions incrementalOptions;
    private final KeepAliveMode keepAliveMode;

    public MinimalScalaCompileOptions(
        boolean failOnError,
        boolean deprecation,
        boolean unchecked,
        String debugLevel,
        boolean optimize,
        String encoding,
        boolean force,
        ImmutableList<String> additionalParameters,
        boolean listFiles,
        String loggingLevel,
        ImmutableList<String> loggingPhases,
        MinimalScalaCompilerDaemonForkOptions forkOptions,
        MinimalIncrementalCompileOptions incrementalOptions,
        KeepAliveMode keepAliveMode
    ) {
        this.failOnError = failOnError;
        this.deprecation = deprecation;
        this.unchecked = unchecked;
        this.debugLevel = debugLevel;
        this.optimize = optimize;
        this.encoding = encoding;
        this.force = force;
        this.additionalParameters = additionalParameters;
        this.listFiles = listFiles;
        this.loggingLevel = loggingLevel;
        this.loggingPhases = loggingPhases;
        this.forkOptions = forkOptions;
        this.incrementalOptions = incrementalOptions;
        this.keepAliveMode = keepAliveMode;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public boolean isDeprecation() {
        return deprecation;
    }

    public boolean isUnchecked() {
        return unchecked;
    }

    @Nullable
    public String getDebugLevel() {
        return debugLevel;
    }

    public boolean isOptimize() {
        return optimize;
    }

    @Nullable
    public String getEncoding() {
        return encoding;
    }

    public boolean isForce() {
        return force;
    }

    @Nullable
    public ImmutableList<String> getAdditionalParameters() {
        return additionalParameters;
    }

    public boolean isListFiles() {
        return listFiles;
    }

    public String getLoggingLevel() {
        return loggingLevel;
    }

    public ImmutableList<String> getLoggingPhases() {
        return loggingPhases;
    }

    public MinimalScalaCompilerDaemonForkOptions getForkOptions() {
        return forkOptions;
    }

    public MinimalIncrementalCompileOptions getIncrementalOptions() {
        return incrementalOptions;
    }

    public KeepAliveMode getKeepAliveMode() {
        return keepAliveMode;
    }

}
