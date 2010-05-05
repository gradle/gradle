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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.process.ExecSpec;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class ExecHandleBuilder extends AbstractExecHandleBuilder implements ExecSpec {
    private final List<String> arguments = new ArrayList<String>();

    public ExecHandleBuilder() {
        super(new IdentityFileResolver());
    }

    public ExecHandleBuilder(FileResolver fileResolver) {
        super(fileResolver);
    }

    public ExecHandleBuilder commandLine(String... arguments) {
        executable(arguments[0]);
        setArgs(Arrays.asList(arguments).subList(1, arguments.length));
        return this;
    }

    public ExecHandleBuilder args(String... args) {
        if (args == null) {
            throw new IllegalArgumentException("args == null!");
        }
        this.arguments.addAll(Arrays.asList(args));
        return this;
    }

    public ExecSpec args(Iterable<String> args) {
        GUtil.addToCollection(arguments, args);
        return this;
    }

    public ExecHandleBuilder setArgs(List<String> arguments) {
        this.arguments.clear();
        this.arguments.addAll(arguments);
        return this;
    }

    public List<String> getArgs() {
        return arguments;
    }

    @Override
    public List<String> getAllArguments() {
        return getArgs();
    }

    @Override
    public ExecHandleBuilder setIgnoreExitValue(boolean ignoreExitValue) {
        super.setIgnoreExitValue(ignoreExitValue);
        return this;
    }
}
