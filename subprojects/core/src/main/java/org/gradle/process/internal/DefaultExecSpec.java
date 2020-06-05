/*
 * Copyright 2020 the original author or authors.
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
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.process.BaseExecSpec;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecSpec;
import org.gradle.util.GUtil;

import javax.inject.Inject;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class DefaultExecSpec extends DefaultProcessForkOptions implements ExecSpec {

    private boolean ignoreExitValue;
    private InputStream standardInput;
    private OutputStream standardOutput;
    private OutputStream errorOutput;

    private final List<Object> arguments = new ArrayList<>();
    private final List<CommandLineArgumentProvider> argumentProviders = new ArrayList<>();

    @Inject
    public DefaultExecSpec(PathToFileResolver resolver) {
        super(resolver);
    }

    public void copyTo(ExecSpec targetSpec) {
        // Fork options
        super.copyTo(targetSpec);
        // BaseExecSpec
        copyBaseExecSpecTo(this, targetSpec);
        // ExecSpec
        targetSpec.setArgs(getArgs());
        targetSpec.getArgumentProviders().addAll(getArgumentProviders());
    }

    static void copyBaseExecSpecTo(BaseExecSpec source, BaseExecSpec target) {
        target.setIgnoreExitValue(source.isIgnoreExitValue());
        if (source.getStandardInput() != null) {
            target.setStandardInput(source.getStandardInput());
        }
        if (source.getStandardOutput() != null) {
            target.setStandardOutput(source.getStandardOutput());
        }
        if (source.getErrorOutput() != null) {
            target.setErrorOutput(source.getErrorOutput());
        }
    }

    @Override
    public List<String> getCommandLine() {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(getExecutable());
        commandLine.addAll(getAllArguments());
        return commandLine;
    }

    private List<String> getAllArguments() {
        List<String> args = new ArrayList<>(getArgs());
        for (CommandLineArgumentProvider argumentProvider : argumentProviders) {
            Iterables.addAll(args, argumentProvider.asArguments());
        }
        return args;
    }

    @Override
    public ExecSpec commandLine(Object... arguments) {
        commandLine(Arrays.asList(arguments));
        return this;
    }

    @Override
    public ExecSpec commandLine(Iterable<?> args) {
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
    public ExecSpec args(Object... args) {
        if (args == null) {
            throw new IllegalArgumentException("args == null!");
        }
        this.arguments.addAll(Arrays.asList(args));
        return this;
    }

    @Override
    public ExecSpec args(Iterable<?> args) {
        GUtil.addToCollection(arguments, args);
        return this;
    }

    @Override
    public ExecSpec setArgs(List<String> arguments) {
        this.arguments.clear();
        this.arguments.addAll(arguments);
        return this;
    }

    @Override
    public ExecSpec setArgs(Iterable<?> arguments) {
        this.arguments.clear();
        GUtil.addToCollection(this.arguments, arguments);
        return this;
    }

    @Override
    public List<String> getArgs() {
        List<String> args = new ArrayList<>();
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
    public ExecSpec setIgnoreExitValue(boolean ignoreExitValue) {
        this.ignoreExitValue = ignoreExitValue;
        return this;
    }

    @Override
    public boolean isIgnoreExitValue() {
        return ignoreExitValue;
    }

    @Override
    public BaseExecSpec setStandardInput(InputStream inputStream) {
        standardInput = inputStream;
        return this;
    }

    @Override
    public InputStream getStandardInput() {
        return standardInput;
    }

    @Override
    public BaseExecSpec setStandardOutput(OutputStream outputStream) {
        standardOutput = outputStream;
        return this;
    }

    @Override
    public OutputStream getStandardOutput() {
        return standardOutput;
    }

    @Override
    public BaseExecSpec setErrorOutput(OutputStream outputStream) {
        errorOutput = outputStream;
        return this;
    }

    @Override
    public OutputStream getErrorOutput() {
        return errorOutput;
    }
}
