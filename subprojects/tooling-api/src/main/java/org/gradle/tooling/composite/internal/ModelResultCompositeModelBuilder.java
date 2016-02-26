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

import org.gradle.api.Incubating;
import org.gradle.api.Transformer;
import org.gradle.tooling.*;
import org.gradle.tooling.composite.ModelResult;
import org.gradle.tooling.composite.ProjectIdentity;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

// TODO: Delete this class once the regular ModelBuilder is producing Iterable<ModelResult<T>>
public class ModelResultCompositeModelBuilder<T> implements ModelBuilder<Iterable<ModelResult<T>>> {
    private final ModelBuilder<Set<T>> delegate;

    ModelResultCompositeModelBuilder(ModelBuilder<Set<T>> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Iterable<ModelResult<T>> get() throws GradleConnectionException, IllegalStateException {
        Set<T> results = delegate.get();
        return transform(results);
    }

    public void get(final ResultHandler<? super Iterable<ModelResult<T>>> handler) throws IllegalStateException {
        delegate.get(new ResultHandler<Set<T>>() {
            @Override
            public void onComplete(Set<T> result) {
                handler.onComplete(transform(result));
            }

            @Override
            public void onFailure(GradleConnectionException failure) {
                handler.onFailure(failure);
            }
        });
    }

    private Set<ModelResult<T>> transform(Set<T> results) {
        return CollectionUtils.collect(results, new Transformer<ModelResult<T>, T>() {
            @Override
            public ModelResult<T> transform(T t) {
                return new DefaultModelResult<T>(t, extractProjectIdentityHack(t));
            }
        });
    }

    private ProjectIdentity extractProjectIdentityHack(T result) {
        if (result instanceof EclipseProject) {
            EclipseProject eclipseProject = (EclipseProject)result;
            EclipseProject rootProject = eclipseProject;
            while (rootProject.getParent()!=null) {
                rootProject = rootProject.getParent();
            }
            File rootDir = rootProject.getGradleProject().getProjectDirectory();
            String projectPath = eclipseProject.getGradleProject().getPath();
            return new DefaultProjectIdentity(new DefaultBuildIdentity(rootDir), rootDir, projectPath);
        }
        return null;
    }

    @Override
    @Incubating
    public ModelResultCompositeModelBuilder<T> forTasks(String... tasks) {
        delegate.forTasks(tasks);
        return this;
    }

    @Override
    @Incubating
    public ModelResultCompositeModelBuilder<T> forTasks(Iterable<String> tasks) {
        delegate.forTasks(tasks);
        return this;
    }

    @Override
    public ModelResultCompositeModelBuilder<T> withArguments(String... arguments) {
        delegate.withArguments(arguments);
        return this;
    }

    @Override
    public ModelResultCompositeModelBuilder<T> withArguments(Iterable<String> arguments) {
        delegate.withArguments(arguments);
        return this;
    }

    @Override
    public ModelResultCompositeModelBuilder<T> setStandardOutput(OutputStream outputStream) {
        delegate.setStandardOutput(outputStream);
        return this;
    }

    @Override
    public ModelResultCompositeModelBuilder<T> setStandardError(OutputStream outputStream) {
        delegate.setStandardError(outputStream);
        return this;
    }

    @Incubating
    @Override
    public ModelResultCompositeModelBuilder<T> setColorOutput(boolean colorOutput) {
        delegate.setColorOutput(colorOutput);
        return this;
    }

    @Override
    public ModelResultCompositeModelBuilder<T> setStandardInput(InputStream inputStream) {
        delegate.setStandardInput(inputStream);
        return this;
    }

    @Override
    public ModelResultCompositeModelBuilder<T> setJavaHome(File javaHome) {
        delegate.setJavaHome(javaHome);
        return this;
    }

    @Override
    public ModelResultCompositeModelBuilder<T> setJvmArguments(String... jvmArguments) {
        delegate.setJvmArguments(jvmArguments);
        return this;
    }

    @Override
    public ModelResultCompositeModelBuilder<T> setJvmArguments(Iterable<String> jvmArguments) {
        delegate.setJvmArguments(jvmArguments);
        return this;
    }

    @Override
    public ModelResultCompositeModelBuilder<T> addProgressListener(ProgressListener listener) {
        delegate.addProgressListener(listener);
        return this;
    }

    @Incubating
    @Override
    public ModelResultCompositeModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener) {
        delegate.addProgressListener(listener);
        return this;
    }

    @Incubating
    @Override
    public ModelResultCompositeModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> eventTypes) {
        delegate.addProgressListener(listener, eventTypes);
        return this;
    }

    @Incubating
    @Override
    public ModelResultCompositeModelBuilder<T> addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes) {
        delegate.addProgressListener(listener, operationTypes);
        return this;
    }

    @Incubating
    @Override
    public ModelResultCompositeModelBuilder<T> withCancellationToken(CancellationToken cancellationToken) {
        delegate.withCancellationToken(cancellationToken);
        return this;
    }
}
