/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.composite.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.tooling.*;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.consumer.CompositeConnectionParameters;
import org.gradle.tooling.internal.consumer.DefaultModelBuilder;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.protocol.eclipse.SetOfEclipseProjects;
import org.gradle.tooling.model.UnsupportedMethodException;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

public class DefaultCompositeModelBuilder<T> implements ModelBuilder<Set<T>> {
    private final ModelBuilder<SetOfEclipseProjects> delegate;

    protected DefaultCompositeModelBuilder(Class<T> modelType, AsyncConsumerActionExecutor asyncConnection, CompositeConnectionParameters parameters) {
        delegate = new DefaultModelBuilder<SetOfEclipseProjects>(SetOfEclipseProjects.class, asyncConnection, parameters);
        //delegate.setJvmArguments("-Xmx1G", "-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005");
    }

    @Override
    public Set<T> get() throws GradleConnectionException, IllegalStateException {
        return (Set<T>) delegate.get().getResult();
    }

    @Override
    public void get(final ResultHandler<? super Set<T>> handler) throws IllegalStateException {
        delegate.get(new ResultHandler<SetOfEclipseProjects>() {
                         @Override
                         public void onComplete(SetOfEclipseProjects result) {
                             handler.onComplete((Set<T>) result.getResult());
                         }

                         @Override
                         public void onFailure(GradleConnectionException failure) {
                             handler.onFailure(failure);
                         }
                     }
        );
    }

    @Override
    public ModelBuilder<Set<T>> withCancellationToken(CancellationToken cancellationToken) {
        delegate.withCancellationToken(cancellationToken);
        return this;
    }

    // TODO: Make all configuration methods configure underlying model builders

    private ModelBuilder<Set<T>> unsupportedMethod() {
        throw new UnsupportedMethodException("Not supported for composite connections.");
    }

    @Override
    public ModelBuilder<Set<T>> forTasks(String... tasks) {
        return forTasks(Lists.newArrayList(tasks));
    }

    @Override
    public ModelBuilder<Set<T>> forTasks(Iterable<String> tasks) {
        return unsupportedMethod();
    }

    @Override
    public ModelBuilder<Set<T>> withArguments(String... arguments) {
        return withArguments(Lists.newArrayList(arguments));
    }

    @Override
    public ModelBuilder<Set<T>> withArguments(Iterable<String> arguments) {
        return unsupportedMethod();
    }

    @Override
    public ModelBuilder<Set<T>> setStandardOutput(OutputStream outputStream) {
        return unsupportedMethod();
    }

    @Override
    public ModelBuilder<Set<T>> setStandardError(OutputStream outputStream) {
        return unsupportedMethod();
    }

    @Override
    public ModelBuilder<Set<T>> setColorOutput(boolean colorOutput) {
        return unsupportedMethod();
    }

    @Override
    public ModelBuilder<Set<T>> setStandardInput(InputStream inputStream) {
        return unsupportedMethod();
    }

    @Override
    public ModelBuilder<Set<T>> setJavaHome(File javaHome) {
        return unsupportedMethod();
    }

    @Override
    public ModelBuilder<Set<T>> setJvmArguments(String... jvmArguments) {
        return unsupportedMethod();
    }

    @Override
    public ModelBuilder<Set<T>> setJvmArguments(Iterable<String> jvmArguments) {
        return unsupportedMethod();
    }

    @Override
    public ModelBuilder<Set<T>> addProgressListener(ProgressListener listener) {
        delegate.addProgressListener(listener);
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> addProgressListener(org.gradle.tooling.events.ProgressListener listener) {
        return unsupportedMethod();
    }

    @Override
    public ModelBuilder<Set<T>> addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes) {
        return addProgressListener(listener, Sets.newHashSet(operationTypes));
    }

    @Override
    public ModelBuilder<Set<T>> addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> eventTypes) {
        return unsupportedMethod();
    }
}
