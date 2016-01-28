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

import com.google.common.collect.Sets;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.internal.consumer.AbstractLongRunningOperation;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.internal.Exceptions;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

public class CompositeModelBuilder<T> extends AbstractLongRunningOperation<CompositeModelBuilder<T>> implements ModelBuilder<Set<T>> {

    private final Class<T> modelType;
    private final Set<GradleParticipantBuild> participants;

    protected CompositeModelBuilder(Class<T> modelType, Set<GradleParticipantBuild> participants, ConnectionParameters parameters) {
        super(parameters);
        this.modelType = modelType;
        this.participants = participants;
    }

    // TODO: Make all configuration methods configure underlying model builders

    @Override
    protected CompositeModelBuilder<T> getThis() {
        return this;
    }

    @Override
    public ModelBuilder<Set<T>> forTasks(String... tasks) {
        throw new UnsupportedOperationException("Not implemented");
        // TODO: return getThis();
    }

    @Override
    public ModelBuilder<Set<T>> forTasks(Iterable<String> tasks) {
        throw new UnsupportedOperationException("Not implemented");
        // TODO: return getThis();
    }

    @Override
    public Set<T> get() throws GradleConnectionException, IllegalStateException {
        ResultHandler<Set<T>> handler = null;
        get(handler);
        return null; // handler.getResult();
    }

    @Override
    public void get(ResultHandler<? super Set<T>> handler) throws IllegalStateException {
        // TODO: Doesn't work yet
        final Set<T> results = Sets.newLinkedHashSet();
        final AtomicReference<Throwable> firstFailure = new AtomicReference<Throwable>();
        final ResultHandlerAdapter<T> adaptedHandler = new ResultHandlerAdapter(handler);

        for (GradleParticipantBuild participant : participants) {
            participant.getConnection().getModel(modelType, new ResultHandler<T>() {
                @Override
                public void onComplete(T result) {
                    results.add(result);
                }

                @Override
                public void onFailure(GradleConnectionException failure) {
                    firstFailure.compareAndSet(null, failure);
                }
            });
        }

        new CyclicBarrier(participants.size(), new Runnable() {
            @Override
            public void run() {
                if (firstFailure.get()==null) {
                    adaptedHandler.onComplete(results);
                } else {
                    adaptedHandler.onFailure(firstFailure.get());
                }
            }
        });
    }

    private class HierarchialResultAdapter<T> implements ResultHandler<Set<T>> {
        private final ResultHandler<Set<T>> delegate;

        private HierarchialResultAdapter(ResultHandler<Set<T>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onComplete(Set<T> results) {
            Collection<? extends HierarchicalElement> hierarchicalSet = CollectionUtils.checkedCast(HierarchicalElement.class, results);
            Set<T> fullSet = Sets.newLinkedHashSet();
            for (HierarchicalElement element : hierarchicalSet) {
                accumulate(element, fullSet);
            }
            delegate.onComplete(fullSet);
        }

        private void accumulate(HierarchicalElement element, Set acc) {
            acc.add(element);
            for (HierarchicalElement child : element.getChildren().getAll()) {
                accumulate(child, acc);
            }
        }

        @Override
        public void onFailure(GradleConnectionException failure) {
            delegate.onFailure(failure);
        }
    }
    private class ResultHandlerAdapter<T> extends org.gradle.tooling.internal.consumer.ResultHandlerAdapter<Set<T>> {
        public ResultHandlerAdapter(ResultHandler<Set<T>> handler) {
            super(handler);
        }

        @Override
        protected String connectionFailureMessage(Throwable failure) {
            // TODO: Supply some composite connection info
            String connectionDisplayName = "composite connection";
            String message = String.format("Could not fetch model of type '%s' using %s.", modelType.getSimpleName(), connectionDisplayName);
            if (!(failure instanceof UnsupportedMethodException) && failure instanceof UnsupportedOperationException) {
                message += "\n" + Exceptions.INCOMPATIBLE_VERSION_HINT;
            }
            return message;
        }
    }
}
