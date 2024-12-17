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
package org.gradle.process;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import java.util.List;

/**
 * Specified the options for executing some command.
 */
public interface ExecSpec extends BaseExecSpec {

    /**
     * {@inheritDoc}
     */
    @Override
    @ReplacesEagerProperty(adapter = ExecSpecAdapters.CommandLineAdapter.class)
    Provider<List<String>> getCommandLine();

    /**
     * Sets the full command line, including the executable to be executed plus its arguments.
     *
     * @param args the command plus the args to be executed
     * @return this
     */
    ExecSpec commandLine(Object... args);

    /**
     * Sets the full command line, including the executable to be executed plus its arguments.
     *
     * @param args the command plus the args to be executed
     * @return this
     */
    ExecSpec commandLine(Iterable<?> args);

    /**
     * Adds arguments for the command to be executed.
     *
     * @param args args for the command
     * @return this
     */
    ExecSpec args(Object... args);

    /**
     * Adds arguments for the command to be executed.
     *
     * @param args args for the command
     * @return this
     */
    ExecSpec args(Iterable<?> args);

    /**
     * Returns the arguments for the command to be executed. Defaults to an empty list.
     */
    @ReplacesEagerProperty(adapter = ExecSpecAdapters.ArgsAdapter.class)
    ListProperty<String> getArgs();

    /**
     * Argument providers for the application.
     *
     * @since 4.6
     */
    @ReplacesEagerProperty(replacedAccessors = {
        @ReplacedAccessor(value = AccessorType.GETTER, name = "getArgumentProviders")
    })
    ListProperty<CommandLineArgumentProvider> getArgumentProviders();
}
