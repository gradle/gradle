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

import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.composite.CompositeModelBuilder;
import org.gradle.tooling.composite.GradleConnection;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.util.Set;

public class ValidatingGradleConnection implements GradleConnection {
    private final GradleConnection delegate;
    private final CompositeValidator validator;

    public ValidatingGradleConnection(GradleConnection delegate, CompositeValidator validator) {
        this.delegate = delegate;
        this.validator = validator;
    }

    @Override
    public <T> Set<T> getModels(Class<T> modelType) throws GradleConnectionException, IllegalStateException {
        checkSupportedModelType(modelType);
        validate();
        return delegate.getModels(modelType);
    }

    @Override
    public <T> void getModels(Class<T> modelType, ResultHandler<? super Set<T>> handler) throws IllegalStateException {
        checkSupportedModelType(modelType);
        validate();
        delegate.getModels(modelType, handler);
    }

    @Override
    public <T> CompositeModelBuilder<T> models(Class<T> modelType) {
        checkSupportedModelType(modelType);
        validate();
        return delegate.models(modelType);
    }

    private <T> void checkSupportedModelType(Class<T> modelType) {
        if (!modelType.isInterface()) {
            throw new IllegalArgumentException(String.format("Cannot fetch a model of type '%s' as this type is not an interface.", modelType.getName()));
        }

        // TODO: Support other model types once this is opened up
        if (!modelType.equals(EclipseProject.class)) {
            throw new UnsupportedOperationException(String.format("The only supported model for a Gradle composite is %s.class.", EclipseProject.class.getSimpleName()));
        }
    }

    private void validate() {
        // TODO: skip validation for now
//        if (!validator.isSatisfiedBy(delegate)) {
//            throw new IllegalArgumentException(validator.whyUnsatisfied());
//        }
    }

    @Override
    public void close() {
        delegate.close();
    }
}
