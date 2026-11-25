/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of {@link AbstractExecTask}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface AbstractExecTaskInputs {

    /**
     * The executable to run.
     *
     * @see AbstractExecTask#getExecutable()
     * @since 10.0.0
     */
    Property<String> getExecutable();

    /**
     * The arguments of the command, without the arguments of the argument providers.
     *
     * @see AbstractExecTask#getArgs()
     * @since 10.0.0
     */
    ListProperty<String> getArgs();

    /**
     * The environment variables of the process. The convention is the environment that the process inherits from Gradle.
     *
     * @see AbstractExecTask#getEnvironment()
     * @since 10.0.0
     */
    MapProperty<String, Object> getEnvironment();

    /**
     * Whether a non-zero exit value of the process is ignored.
     *
     * @see AbstractExecTask#isIgnoreExitValue()
     * @since 10.0.0
     */
    Property<Boolean> getIgnoreExitValue();
}
