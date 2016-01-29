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
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.AbstractLongRunningOperation;
import org.gradle.tooling.internal.consumer.ConnectionParameters;

import java.util.Set;

public class CompositeModelBuilder<T> extends AbstractLongRunningOperation<CompositeModelBuilder<T>> implements ModelBuilder<Set<T>> {

    private final Set<ModelBuilder<T>> participantModelBuilders;

    protected CompositeModelBuilder(ConnectionParameters parameters, Set<ModelBuilder<T>> participantModelBuilders) {
        super(parameters);
        this.participantModelBuilders = participantModelBuilders;
    }

    // TODO: Make all configuration methods configure underlying model builders

    @Override
    protected CompositeModelBuilder<T> getThis() {
        return null;
    }

    @Override
    public ModelBuilder<Set<T>> forTasks(String... tasks) {
        return null;
    }

    @Override
    public ModelBuilder<Set<T>> forTasks(Iterable<String> tasks) {
        return null;
    }

    @Override
    public Set<T> get() throws GradleConnectionException, IllegalStateException {
        return null;
    }

    @Override
    public void get(ResultHandler<? super Set<T>> handler) throws IllegalStateException {

    }
}
