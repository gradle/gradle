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

import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.composite.CompositeModelBuilder;
import org.gradle.tooling.internal.consumer.CompositeConnectionParameters;
import org.gradle.tooling.internal.consumer.DefaultModelBuilder;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.protocol.eclipse.SetOfEclipseProjects;

import java.util.Set;

public class CoordinatorCompositeModelBuilder<T> implements CompositeModelBuilder<T> {
    private final ModelBuilder<SetOfEclipseProjects> delegate;

    protected CoordinatorCompositeModelBuilder(Class<T> modelType, AsyncConsumerActionExecutor asyncConnection, CompositeConnectionParameters parameters) {
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
    public CompositeModelBuilder<T> withCancellationToken(CancellationToken cancellationToken) {
        delegate.withCancellationToken(cancellationToken);
        return this;
    }
}
