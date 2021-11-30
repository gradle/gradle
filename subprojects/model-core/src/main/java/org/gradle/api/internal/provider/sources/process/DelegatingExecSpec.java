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

package org.gradle.api.internal.provider.sources.process;

import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecSpec;

import java.util.List;

interface DelegatingExecSpec extends DelegatingBaseExecSpec, ExecSpec {
    @Override
    default void setCommandLine(List<String> args) {
        getDelegate().setCommandLine(args);
    }

    @Override
    default void setCommandLine(Object... args) {
        getDelegate().setCommandLine(args);
    }

    @Override
    default void setCommandLine(Iterable<?> args) {
        getDelegate().setCommandLine(args);
    }

    @Override
    default ExecSpec commandLine(Object... args) {
        getDelegate().commandLine(args);
        return this;
    }

    @Override
    default ExecSpec commandLine(Iterable<?> args) {
        getDelegate().commandLine(args);
        return this;
    }

    @Override
    default ExecSpec args(Object... args) {
        getDelegate().args(args);
        return this;
    }

    @Override
    default ExecSpec args(Iterable<?> args) {
        getDelegate().args(args);
        return this;
    }

    @Override
    default ExecSpec setArgs(List<String> args) {
        getDelegate().setArgs(args);
        return this;
    }

    @Override
    default ExecSpec setArgs(Iterable<?> args) {
        getDelegate().setArgs(args);
        return this;
    }

    @Override
    default List<String> getArgs() {
        return getDelegate().getArgs();
    }

    @Override
    default List<CommandLineArgumentProvider> getArgumentProviders() {
        return getDelegate().getArgumentProviders();
    }

    @Override
    ExecSpec getDelegate();
}
