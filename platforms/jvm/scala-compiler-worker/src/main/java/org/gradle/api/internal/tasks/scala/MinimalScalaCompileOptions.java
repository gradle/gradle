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

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

public class MinimalScalaCompileOptions implements Serializable {
    private boolean failOnError = true;
    private boolean deprecation = true;
    private boolean unchecked = true;
    private String debugLevel;
    private boolean optimize;
    private String encoding;
    private boolean force;
    private List<String> additionalParameters;
    private boolean listFiles;
    private String loggingLevel;
    private List<String> loggingPhases;
    private MinimalScalaCompilerDaemonForkOptions forkOptions;
    private MinimalIncrementalCompileOptions incrementalOptions;
    private String keepAliveMode;

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public boolean isDeprecation() {
        return deprecation;
    }

    public void setDeprecation(boolean deprecation) {
        this.deprecation = deprecation;
    }

    public boolean isUnchecked() {
        return unchecked;
    }

    public void setUnchecked(boolean unchecked) {
        this.unchecked = unchecked;
    }

    @Nullable
    public String getDebugLevel() {
        return debugLevel;
    }

    public void setDebugLevel(@Nullable String debugLevel) {
        this.debugLevel = debugLevel;
    }

    public boolean isOptimize() {
        return optimize;
    }

    public void setOptimize(boolean optimize) {
        this.optimize = optimize;
    }

    @Nullable
    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(@Nullable String encoding) {
        this.encoding = encoding;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    @Nullable
    public List<String> getAdditionalParameters() {
        return additionalParameters;
    }

    public void setAdditionalParameters(@Nullable List<String> additionalParameters) {
        this.additionalParameters = additionalParameters;
    }

    public boolean isListFiles() {
        return listFiles;
    }

    public void setListFiles(boolean listFiles) {
        this.listFiles = listFiles;
    }

    public String getLoggingLevel() {
        return loggingLevel;
    }

    public void setLoggingLevel(String loggingLevel) {
        this.loggingLevel = loggingLevel;
    }

    public List<String> getLoggingPhases() {
        return loggingPhases;
    }

    public void setLoggingPhases(List<String> loggingPhases) {
        this.loggingPhases = loggingPhases;
    }

    public MinimalScalaCompilerDaemonForkOptions getForkOptions() {
        return forkOptions;
    }

    public void setForkOptions(MinimalScalaCompilerDaemonForkOptions forkOptions) {
        this.forkOptions = forkOptions;
    }

    public MinimalIncrementalCompileOptions getIncrementalOptions() {
        return incrementalOptions;
    }

    public void setIncrementalOptions(MinimalIncrementalCompileOptions incrementalOptions) {
        this.incrementalOptions = incrementalOptions;
    }

    /**
     * Must be equal to the name of an element of {@link org.gradle.workers.internal.KeepAliveMode}.
     */
    public void setKeepAliveMode(String keepAliveMode) {
        this.keepAliveMode = keepAliveMode;
    }

    public String getKeepAliveMode() {
        return keepAliveMode;
    }
}
