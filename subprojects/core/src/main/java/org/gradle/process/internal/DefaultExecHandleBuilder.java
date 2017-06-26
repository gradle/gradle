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

import com.google.common.collect.Lists;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.util.GUtil;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DefaultExecHandleBuilder extends AbstractExecHandleBuilder implements ExecHandleBuilder {
    private final List<Object> arguments = new ArrayList<Object>();

    public DefaultExecHandleBuilder() {
        super(new IdentityFileResolver());
    }

    public DefaultExecHandleBuilder(PathToFileResolver fileResolver) {
        super(fileResolver);
    }

    public DefaultExecHandleBuilder executable(Object executable) {
        super.executable(executable);
        return this;
    }

    public DefaultExecHandleBuilder commandLine(Object... arguments) {
        commandLine(Arrays.asList(arguments));
        return this;
    }

    public DefaultExecHandleBuilder commandLine(Iterable<?> args) {
        List<Object> argsList = Lists.newArrayList(args);
        executable(argsList.get(0));
        setArgs(argsList.subList(1, argsList.size()));
        return this;
    }

    public void setCommandLine(List<String> args) {
        commandLine(args);
    }

    public void setCommandLine(Object... args) {
        commandLine(args);
    }

    public void setCommandLine(Iterable<?> args) {
        commandLine(args);
    }

    public DefaultExecHandleBuilder args(Object... args) {
        if (args == null) {
            throw new IllegalArgumentException("args == null!");
        }
        this.arguments.addAll(Arrays.asList(args));
        return this;
    }

    public DefaultExecHandleBuilder args(Iterable<?> args) {
        GUtil.addToCollection(arguments, args);
        return this;
    }

    public DefaultExecHandleBuilder setArgs(List<String> arguments) {
        this.arguments.clear();
        this.arguments.addAll(arguments);
        return this;
    }

    public DefaultExecHandleBuilder setArgs(Iterable<?> arguments) {
        this.arguments.clear();
        GUtil.addToCollection(this.arguments, arguments);
        return this;
    }

    public List<String> getArgs() {
        List<String> args = new ArrayList<String>();
        for (Object argument : arguments) {
            args.add(argument.toString());
        }
        return args;
    }

    @Override
    public List<String> getAllArguments() {
        return getArgs();
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

    public DefaultExecHandleBuilder redirectErrorStream() {
        super.redirectErrorStream();
        return this;
    }

    public DefaultExecHandleBuilder setStandardOutput(OutputStream outputStream) {
        super.setStandardOutput(outputStream);
        return this;
    }

    public DefaultExecHandleBuilder setStandardInput(InputStream inputStream) {
        super.setStandardInput(inputStream);
        return this;
    }

    public DefaultExecHandleBuilder listener(ExecHandleListener listener) {
        super.listener(listener);
        return this;
    }

    public DefaultExecHandleBuilder setTimeout(int timeoutMillis) {
        super.setTimeout(timeoutMillis);
        return this;
    }

    public ExecHandleBuilder setDaemon(boolean daemon) {
        super.daemon = daemon;
        return this;
    }
}
