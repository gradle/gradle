/*
 * Copyright 2010 the original author or authors.
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.util.GUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Use {@link ExecHandleFactory} instead.
 */
public class DefaultExecHandleBuilder extends AbstractExecHandleBuilder implements ExecHandleBuilder {
    private final List<Object> arguments = new ArrayList<Object>();
    private final List<CommandLineArgumentProvider> argumentProviders = new ArrayList<CommandLineArgumentProvider>();

    public DefaultExecHandleBuilder(PathToFileResolver fileResolver, Executor executor) {
        this(fileResolver, executor, new DefaultBuildCancellationToken());
    }

    public DefaultExecHandleBuilder(PathToFileResolver fileResolver, Executor executor, BuildCancellationToken buildCancellationToken) {
        super(fileResolver, executor, buildCancellationToken);
    }

    @Override
    public DefaultExecHandleBuilder executable(Object executable) {
        super.executable(executable);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder commandLine(Object... arguments) {
        commandLine(Arrays.asList(arguments));
        return this;
    }

    @Override
    public DefaultExecHandleBuilder commandLine(Iterable<?> args) {
        List<Object> argsList = Lists.newArrayList(args);
        executable(argsList.get(0));
        setArgs(argsList.subList(1, argsList.size()));
        return this;
    }

    @Override
    public void setCommandLine(List<String> args) {
        commandLine(args);
    }

    @Override
    public void setCommandLine(Object... args) {
        commandLine(args);
    }

    @Override
    public void setCommandLine(Iterable<?> args) {
        commandLine(args);
    }

    @Override
    public DefaultExecHandleBuilder args(Object... args) {
        if (args == null) {
            throw new IllegalArgumentException("args == null!");
        }
        this.arguments.addAll(Arrays.asList(args));
        return this;
    }

    @Override
    public DefaultExecHandleBuilder args(Iterable<?> args) {
        GUtil.addToCollection(arguments, args);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setArgs(List<String> arguments) {
        this.arguments.clear();
        this.arguments.addAll(arguments);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setArgs(Iterable<?> arguments) {
        this.arguments.clear();
        GUtil.addToCollection(this.arguments, arguments);
        return this;
    }

    @Override
    public List<String> getArgs() {
        List<String> args = new ArrayList<String>();
        for (Object argument : arguments) {
            args.add(argument.toString());
        }
        return args;
    }

    @Override
    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return argumentProviders;
    }

    @Override
    public List<String> getAllArguments() {
        List<String> args = new ArrayList<String>(getArgs());
        for (CommandLineArgumentProvider argumentProvider : argumentProviders) {
            Iterables.addAll(args, argumentProvider.asArguments());
        }
        return args;
    }

    @Override
    public DefaultExecHandleBuilder setIgnoreExitValue(boolean ignoreExitValue) {
        super.setIgnoreExitValue(ignoreExitValue);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder workingDir(Object dir) {
        super.workingDir(dir);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setDisplayName(String displayName) {
        super.setDisplayName(displayName);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder redirectErrorStream() {
        super.redirectErrorStream();
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setStandardOutput(OutputStream outputStream) {
        super.setStandardOutput(outputStream);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setStandardInput(InputStream inputStream) {
        super.setStandardInput(inputStream);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder streamsHandler(StreamsHandler streamsHandler) {
        super.streamsHandler(streamsHandler);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder listener(ExecHandleListener listener) {
        super.listener(listener);
        return this;
    }

    @Override
    public DefaultExecHandleBuilder setTimeout(int timeoutMillis) {
        super.setTimeout(timeoutMillis);
        return this;
    }

    @Override
    public ExecHandleBuilder setDaemon(boolean daemon) {
        super.daemon = daemon;
        return this;
    }
}
