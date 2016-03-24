/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.tooling.internal.consumer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Nullable;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractLongRunningOperation<T extends AbstractLongRunningOperation<T>> implements LongRunningOperation {
    protected final ConnectionParameters connectionParameters;
    protected final ConsumerOperationParameters.Builder operationParamsBuilder;

    protected AbstractLongRunningOperation(ConnectionParameters parameters) {
        connectionParameters = parameters;
        operationParamsBuilder = ConsumerOperationParameters.builder();
        operationParamsBuilder.setCancellationToken(new DefaultCancellationTokenSource().token());
    }

    protected abstract T getThis();

    protected final ConsumerOperationParameters getConsumerOperationParameters() {
        ConnectionParameters connectionParameters = this.connectionParameters;
        return operationParamsBuilder.setParameters(connectionParameters).build();
    }

    protected static @Nullable <T> List<T> rationalizeInput(@Nullable T[] arguments) {
        return arguments != null && arguments.length > 0 ? Arrays.asList(arguments) : null;
    }

    protected static @Nullable <T> List<T> rationalizeInput(@Nullable Iterable<? extends T> arguments) {
        return arguments != null && arguments.iterator().hasNext() ? CollectionUtils.toList(arguments) : null;
    }

    @Override
    public T withArguments(String... arguments) {
        operationParamsBuilder.setArguments(rationalizeInput(arguments));
        return getThis();
    }

    @Override
    public T withArguments(Iterable<String> arguments) {
        operationParamsBuilder.setArguments(rationalizeInput(arguments));
        return getThis();
    }

    @Override
    public T setStandardOutput(OutputStream outputStream) {
        operationParamsBuilder.setStdout(outputStream);
        return getThis();
    }

    @Override
    public T setStandardError(OutputStream outputStream) {
        operationParamsBuilder.setStderr(outputStream);
        return getThis();
    }

    @Override
    public T setStandardInput(InputStream inputStream) {
        operationParamsBuilder.setStdin(inputStream);
        return getThis();
    }

    @Override
    public T setColorOutput(boolean colorOutput) {
        operationParamsBuilder.setColorOutput(colorOutput);
        return getThis();
    }

    @Override
    public T setJavaHome(File javaHome) {
        operationParamsBuilder.setJavaHome(javaHome);
        return getThis();
    }

    @Override
    public T setJvmArguments(String... jvmArguments) {
        operationParamsBuilder.setJvmArguments(rationalizeInput(jvmArguments));
        return getThis();
    }

    @Override
    public T setJvmArguments(Iterable<String> jvmArguments) {
        operationParamsBuilder.setJvmArguments(rationalizeInput(jvmArguments));
        return getThis();
    }

    @Override
    public T addProgressListener(ProgressListener listener) {
        operationParamsBuilder.addProgressListener(listener);
        return getThis();
    }

    @Override
    public T addProgressListener(org.gradle.tooling.events.ProgressListener listener) {
        return addProgressListener(listener, EnumSet.allOf(OperationType.class));
    }

    @Override
    public T addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes) {
        return addProgressListener(listener, ImmutableSet.copyOf(operationTypes));
    }

    @Override
    public T addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> eventTypes) {
        if (eventTypes.contains(OperationType.TEST)) {
            operationParamsBuilder.addTestProgressListener(listener);
        }
        if (eventTypes.contains(OperationType.TASK)) {
            operationParamsBuilder.addTaskProgressListener(listener);
        }
        if (eventTypes.contains(OperationType.GENERIC)) {
            operationParamsBuilder.addBuildOperationProgressListeners(listener);
        }
        return getThis();
    }

    @Override
    public T withCancellationToken(CancellationToken cancellationToken) {
        operationParamsBuilder.setCancellationToken(Preconditions.checkNotNull(cancellationToken));
        return getThis();
    }

    /**
     * Specifies classpath URIs used for loading user-defined classes. This list is in addition to the default classpath.
     *
     * @param classpath Classpath URIs
     * @return this
     * @since 2.8
     */
    public T withInjectedClassPath(ClassPath classpath) {
        operationParamsBuilder.setInjectedPluginClasspath(classpath);
        return getThis();
    }

    public void copyFrom(ConsumerOperationParameters operationParameters) {
        operationParamsBuilder.copyFrom(operationParameters);
    }
}
