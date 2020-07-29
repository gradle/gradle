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
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class ProcessArgumentsSpec {

    interface HasExecutable {

        String getExecutable();

        void setExecutable(Object executable);
    }

    private final HasExecutable hasExecutable;
    private final List<Object> arguments = new ArrayList<>();
    private final List<CommandLineArgumentProvider> argumentProviders = new ArrayList<>();

    public ProcessArgumentsSpec(HasExecutable hasExecutable) {
        this.hasExecutable = hasExecutable;
    }

    public List<String> getCommandLine() {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(hasExecutable.getExecutable());
        commandLine.addAll(getAllArguments());
        return commandLine;
    }

    public ProcessArgumentsSpec commandLine(Object... arguments) {
        commandLine(Arrays.asList(arguments));
        return this;
    }

    public ProcessArgumentsSpec commandLine(Iterable<?> args) {
        List<Object> argsList = Lists.newArrayList(args);
        hasExecutable.setExecutable(argsList.get(0));
        setArgs(argsList.subList(1, argsList.size()));
        return this;
    }

    public List<String> getAllArguments() {
        List<String> allArgs;
        List<String> args = getArgs();
        if (args == null) {
            allArgs = new ArrayList<>();
        } else {
            allArgs = new ArrayList<>(args);
        }
        for (CommandLineArgumentProvider argumentProvider : argumentProviders) {
            Iterables.addAll(allArgs, argumentProvider.asArguments());
        }
        return allArgs;
    }

    public ProcessArgumentsSpec args(Object... args) {
        if (args == null) {
            throw new IllegalArgumentException("args == null!");
        }
        args(Arrays.asList(args));
        return this;
    }

    public ProcessArgumentsSpec args(Iterable<?> args) {
        GUtil.addToCollection(arguments, true, args);
        return this;
    }

    public ProcessArgumentsSpec setArgs(List<String> arguments) {
        this.arguments.clear();
        args(arguments);
        return this;
    }

    public ProcessArgumentsSpec setArgs(Iterable<?> arguments) {
        this.arguments.clear();
        args(arguments);
        return this;
    }

    public List<String> getArgs() {
        List<String> args = new ArrayList<>();
        for (Object argument : arguments) {
            args.add(argument.toString());
        }
        return args;
    }

    public List<CommandLineArgumentProvider> getArgumentProviders() {
        return argumentProviders;
    }
}
