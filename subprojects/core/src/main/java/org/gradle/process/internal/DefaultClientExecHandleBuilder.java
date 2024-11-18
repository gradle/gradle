/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.api.NonNullApi;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ProcessForkOptions;
import org.gradle.process.internal.streams.EmptyStdInStreamsHandler;
import org.gradle.process.internal.streams.ForwardStdinStreamsHandler;
import org.gradle.process.internal.streams.OutputStreamsForwarder;
import org.gradle.process.internal.streams.SafeStreams;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

@NonNullApi
public class DefaultClientExecHandleBuilder implements ClientExecHandleBuilder, ProcessArgumentsSpec.HasExecutable {

    private static final EmptyStdInStreamsHandler DEFAULT_STDIN = new EmptyStdInStreamsHandler();

    private final BuildCancellationToken buildCancellationToken;
    private final List<ExecHandleListener> listeners;
    private final ProcessStreamsSpec streamsSpec;
    private final ProcessArgumentsSpec argumentsSpec;
    private final PathToFileResolver fileResolver;

    private Map<String, Object> environment;
    private StreamsHandler inputHandler = DEFAULT_STDIN;
    private String displayName;
    private boolean redirectErrorStream;
    private StreamsHandler streamsHandler;
    private int timeoutMillis = Integer.MAX_VALUE;
    protected boolean daemon;
    private final Executor executor;
    private String executable;
    private File workingDir;

    public DefaultClientExecHandleBuilder(PathToFileResolver fileResolver, Executor executor, BuildCancellationToken buildCancellationToken) {
        this.buildCancellationToken = buildCancellationToken;
        this.executor = executor;
        this.listeners = new ArrayList<>();
        this.fileResolver = fileResolver;
        this.argumentsSpec = new ProcessArgumentsSpec(this);
        this.streamsSpec = new ProcessStreamsSpec();
        streamsSpec.setStandardOutput(SafeStreams.systemOut());
        streamsSpec.setErrorOutput(SafeStreams.systemErr());
        streamsSpec.setStandardInput(SafeStreams.emptyInput());
    }

    @Override
    public ClientExecHandleBuilder commandLine(Iterable<?> args) {
        argumentsSpec.commandLine(args);
        return this;
    }

    @Override
    public ClientExecHandleBuilder commandLine(Object... args) {
        argumentsSpec.commandLine(args);
        return this;
    }

    @Override
    public ClientExecHandleBuilder setStandardInput(InputStream inputStream) {
        streamsSpec.setStandardInput(inputStream);
        this.inputHandler = new ForwardStdinStreamsHandler(inputStream);
        return this;
    }

    @Override
    public ClientExecHandleBuilder setStandardOutput(OutputStream outputStream) {
        streamsSpec.setStandardOutput(outputStream);
        return this;
    }

    @Override
    public ClientExecHandleBuilder setErrorOutput(OutputStream outputStream) {
        streamsSpec.setErrorOutput(outputStream);
        return this;
    }

    @Override
    public ClientExecHandleBuilder redirectErrorStream() {
        this.redirectErrorStream = true;
        return this;
    }

    @Override
    public ClientExecHandleBuilder setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
        return this;
    }

    @Override
    public ClientExecHandleBuilder setDaemon(boolean daemon) {
        this.daemon = daemon;
        return this;
    }

    @Override
    public ClientExecHandleBuilder streamsHandler(StreamsHandler streamsHandler) {
        this.streamsHandler = streamsHandler;
        return this;
    }

    @Override
    public ClientExecHandleBuilder setTimeout(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
        return this;
    }

    @Override
    public ClientExecHandleBuilder args(Object... args) {
        argumentsSpec.args(args);
        return this;
    }

    @Override
    public ClientExecHandleBuilder args(Iterable<?> args) {
        argumentsSpec.args(args);
        return this;
    }

    @Override
    public List<String> getArgs() {
        return argumentsSpec.getArgs();
    }

    @Override
    public ClientExecHandleBuilder setArgs(Iterable<?> args) {
        argumentsSpec.setArgs(args);
        return this;
    }

    @Override
    public String getExecutable() {
        return executable;
    }

    @Override
    public void setExecutable(Object executable) {
        setExecutable(Objects.toString(executable));
    }

    @Override
    public ClientExecHandleBuilder setExecutable(String executable) {
        this.executable = executable;
        return this;
    }

    @Override
    public ClientExecHandleBuilder listener(ExecHandleListener listener) {
        listeners.add(listener);
        return this;
    }

    @Override
    public OutputStream getErrorOutput() {
        return streamsSpec.getErrorOutput();
    }

    @Override
    public List<String> getCommandLine() {
        return argumentsSpec.getCommandLine();
    }

    @Override
    public OutputStream getStandardOutput() {
        return streamsSpec.getStandardOutput();
    }

    @Override
    public List<String> getAllArguments() {
        return argumentsSpec.getAllArguments();
    }

    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return argumentsSpec.getArgumentProviders();
    }

    @Override
    public Map<String, Object> getEnvironment() {
        if (environment == null) {
            setEnvironment(System.getenv());
        }
        return environment;
    }

    @Override
    public ClientExecHandleBuilder environment(String key, Object value) {
        getEnvironment().put(key, value);
        return this;
    }

    @Override
    public void setEnvironment(Map<String, ?> environmentVariables) {
        environment = Maps.newHashMap(environmentVariables);
    }

    @Override
    public void environment(Map<String, ?> environmentVariables) {
        getEnvironment().putAll(environmentVariables);
    }

    @Override
    public InputStream getStandardInput() {
        return streamsSpec.getStandardInput();
    }

    @Nullable
    @Override
    public File getWorkingDir() {
        if (workingDir == null) {
            workingDir = fileResolver.resolve(".");
        }
        return workingDir;
    }

    @Override
    public ClientExecHandleBuilder setWorkingDir(@Nullable File dir) {
        this.workingDir = dir == null ? null:  fileResolver.resolve(dir);
        return this;
    }

    @Override
    public ClientExecHandleBuilder setWorkingDir(@Nullable Object dir) {
        this.workingDir = dir == null ? null : fileResolver.resolve(dir);
        return this;
    }

    @Override
    public void copyTo(ProcessForkOptions options) {
        options.setExecutable(executable);
        options.setWorkingDir(getWorkingDir());
        options.setEnvironment(getEnvironment());
    }

    private static Map<String, String> getEffectiveEnvironment(Map<String, Object> environment) {
        Map<String, String> effectiveEnvironment = Maps.newLinkedHashMapWithExpectedSize(environment.size());
        for (Map.Entry<String, Object> entry : environment.entrySet()) {
            effectiveEnvironment.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return effectiveEnvironment;
    }

    private static StreamsHandler getEffectiveStreamsHandler(@Nullable StreamsHandler streamsHandler, ProcessStreamsSpec streamsSpec, boolean redirectErrorStream) {
        if (streamsHandler != null) {
            return streamsHandler;
        }
        boolean shouldReadErrorStream = !redirectErrorStream;
        return new OutputStreamsForwarder(
            streamsSpec.getStandardOutput(),
            streamsSpec.getErrorOutput(),
            shouldReadErrorStream
        );
    }

    @Override
    public ExecHandle build() {
        return buildWithEffectiveArguments(argumentsSpec.getAllArguments());
    }

    @Override
    public ExecHandle buildWithEffectiveArguments(List<String> effectiveArguments) {
        String displayName = this.displayName == null ? String.format("command '%s'", executable) : this.displayName;
        Map<String, String> effectiveEnvironment = getEffectiveEnvironment(getEnvironment());
        StreamsHandler effectiveOutputHandler = getEffectiveStreamsHandler(streamsHandler, streamsSpec, redirectErrorStream);
        return new DefaultExecHandle(
            displayName,
            getWorkingDir(),
            executable,
            effectiveArguments,
            effectiveEnvironment,
            effectiveOutputHandler,
            inputHandler,
            listeners,
            redirectErrorStream,
            timeoutMillis,
            daemon,
            executor,
            buildCancellationToken
        );
    }
}
