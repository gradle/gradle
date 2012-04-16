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

package org.gradle.tooling.internal.provider.input;

import org.gradle.api.logging.LogLevel;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.logging.LoggingConfiguration;
import org.gradle.logging.internal.LoggingCommandLineConverter;
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1;
import org.gradle.tooling.internal.protocol.BuildParametersVersion1;
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1;
import org.gradle.tooling.internal.reflect.CompatibleIntrospector;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * by Szczepan Faber, created at: 12/20/11
 */
public class AdaptedOperationParameters implements ProviderOperationParameters {

    private final BuildOperationParametersVersion1 delegate;
    private final List<String> tasks;
    
    CompatibleIntrospector introspector;

    public AdaptedOperationParameters(BuildOperationParametersVersion1 operationParameters) {
        this(operationParameters, Arrays.<String>asList());
    }

    public AdaptedOperationParameters(BuildOperationParametersVersion1 operationParameters, BuildParametersVersion1 buildParameters) {
        this(operationParameters, buildParameters.getTasks());
    }

    private AdaptedOperationParameters(BuildOperationParametersVersion1 delegate, List<String> tasks) {
        this.delegate = delegate;
        this.introspector = new CompatibleIntrospector(delegate);
        this.tasks = new LinkedList<String>(tasks);
    }

    public InputStream getStandardInput() {
        //Tooling api means embedded use. We don't want to consume standard input if we don't own the process.
        //Hence we use a dummy input stream by default
        ByteArrayInputStream safeDummy = new ByteArrayInputStream(new byte[0]);
        return maybeGet(safeDummy, "getStandardInput");
    }

    public LogLevel getBuildLogLevel() {
        LoggingCommandLineConverter converter = new LoggingCommandLineConverter();
        CommandLineParser parser = new CommandLineParser().allowUnknownOptions();
        converter.configure(parser);
        ParsedCommandLine parsedCommandLine = parser.parse(getArguments());
        //configure verbosely only if arguments do not specify any log level.
        if (getVerboseLogging() && !parsedCommandLine.hasAnyOption(converter.getLogLevelOptions())) {
            return LogLevel.DEBUG;
        }

        LoggingConfiguration loggingConfiguration = converter.convert(parsedCommandLine);
        return loggingConfiguration.getLogLevel();
    }

    public boolean getVerboseLogging() {
        return introspector.getSafely(false, "getVerboseLogging");
    }

    public File getJavaHome(File defaultJavaHome) {
        return maybeGet(defaultJavaHome, "getJavaHome");
    }

    public List<String> getJvmArguments(List<String> defaultJvmArgs) {
        return maybeGet(defaultJvmArgs, "getJvmArguments");
    }

    private <T> T maybeGet(T defaultValue, String methodName) {
        T out = introspector.getSafely(defaultValue, methodName);
        if (out == null) {
            return defaultValue;
        }
        return out;
    }

    public File getProjectDir() {
        return delegate.getProjectDir();
    }

    public Boolean isSearchUpwards() {
        return delegate.isSearchUpwards();
    }

    public File getGradleUserHomeDir() {
        return delegate.getGradleUserHomeDir();
    }

    public Boolean isEmbedded() {
        return delegate.isEmbedded();
    }

    public Integer getDaemonMaxIdleTimeValue() {
        return delegate.getDaemonMaxIdleTimeValue();
    }

    public TimeUnit getDaemonMaxIdleTimeUnits() {
        return delegate.getDaemonMaxIdleTimeUnits();
    }

    public long getStartTime() {
        return delegate.getStartTime();
    }

    public OutputStream getStandardOutput() {
        return delegate.getStandardOutput();
    }

    public OutputStream getStandardError() {
        return delegate.getStandardError();
    }

    public ProgressListenerVersion1 getProgressListener() {
        return delegate.getProgressListener();
    }

    public List<String> getArguments() {
        return maybeGet(Arrays.<String>asList(), "getArguments");
    }
    
    public List<String> getTasks() {
        return tasks;
    }
}
