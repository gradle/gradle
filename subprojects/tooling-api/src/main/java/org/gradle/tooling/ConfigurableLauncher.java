/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.OperationType;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

/**
 * A {@code ConfigurableLauncher} allows you to configure a long running operation.
 *
 * @param <T> the ConfigurableLauncher implementation to return as part of the fluent API.
 * @since 2.6
 * */
public interface ConfigurableLauncher<T extends ConfigurableLauncher> extends LongRunningOperation {
    /**
     * {@inheritDoc}
     * @since 1.0
     */
    @Override
    T withArguments(String ... arguments);

    /**
     * {@inheritDoc}
     * @since 2.6
     */
    @Override
    T withArguments(Iterable<String> arguments);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-3
     */
    @Override
    T setStandardOutput(OutputStream outputStream);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-3
     */
    @Override
    T setStandardError(OutputStream outputStream);

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    @Incubating
    @Override
    T setColorOutput(boolean colorOutput);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-7
     */
    @Override
    T setStandardInput(InputStream inputStream);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-8
     */
    @Override
    T setJavaHome(File javaHome);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-9
     */
    @Override
    T setJvmArguments(String... jvmArguments);

    /**
     * {@inheritDoc}
     * @since 2.6
     */
    @Override
    T setJvmArguments(Iterable<String> jvmArguments);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-3
     */
    @Override
    T addProgressListener(ProgressListener listener);

    /**
     * {@inheritDoc}
     * @since 2.5
     */
    @Incubating
    @Override
    T addProgressListener(org.gradle.tooling.events.ProgressListener listener);

    /**
     * {@inheritDoc}
     * @since 2.5
     */
    @Incubating
    @Override
    T addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> eventTypes);

    /**
     * {@inheritDoc}
     * @since 2.6
     */
    @Incubating
    @Override
    T addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes);

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    @Incubating
    @Override
    T withCancellationToken(CancellationToken cancellationToken);
}
