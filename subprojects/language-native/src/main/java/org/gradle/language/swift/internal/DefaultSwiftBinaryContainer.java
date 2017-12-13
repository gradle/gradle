/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift.internal;

import com.google.common.collect.ImmutableSet;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.language.swift.SwiftBinaryContainer;
import org.gradle.language.swift.SwiftBinaryProvider;
import org.gradle.util.ConfigureUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Set;

// TODO - error messages
// TODO - display names for this container and the Provider implementations
public class DefaultSwiftBinaryContainer<T extends SoftwareComponent> implements SwiftBinaryContainer<T> {
    private enum State {
        Collecting, Realizing, Finalized
    }

    private final Class<T> elementType;
    private final ProviderFactory providerFactory;
    private final Set<T> elements = new LinkedHashSet<T>();
    private State state = State.Collecting;
    private ImmutableActionSet<T> knownActions = ImmutableActionSet.empty();
    private ImmutableActionSet<T> configureActions = ImmutableActionSet.empty();
    private ImmutableActionSet<T> finalizeActions = ImmutableActionSet.empty();

    @Inject
    public DefaultSwiftBinaryContainer(Class<T> elementType, ProviderFactory providerFactory) {
        this.elementType = elementType;
        this.providerFactory = providerFactory;
    }

    @Override
    public <S> SwiftBinaryProvider<S> get(final Class<S> type, final Spec<? super S> spec) {
        return new SingleElementProvider<S>(type, spec);
    }

    @Override
    public SwiftBinaryProvider<T> getByName(final String name) {
        return get(new Spec<T>() {
            @Override
            public boolean isSatisfiedBy(T element) {
                return element.getName().equals(name);
            }
        });
    }

    @Override
    public SwiftBinaryProvider<T> get(Spec<? super T> spec) {
        return get(elementType, spec);
    }

    @Override
    public void whenElementKnown(Action<? super T> action) {
        if (state != State.Collecting) {
            throw new IllegalStateException("Cannot add actions to this collection as it has already been realized.");
        }
        knownActions = knownActions.add(action);
        for (T element : elements) {
            action.execute(element);
        }
    }

    @Override
    public void whenElementFinalized(Action<? super T> action) {
        if (state != State.Collecting) {
            throw new IllegalStateException("Cannot add actions to this collection as it has already been realized.");
        }
        finalizeActions = finalizeActions.add(action);
    }

    @Override
    public void configureEach(Action<? super T> action) {
        if (state != State.Collecting) {
            throw new IllegalStateException("Cannot add actions to this collection as it has already been realized.");
        }
        configureActions = configureActions.add(action);
    }

    void add(T element) {
        if (state != State.Collecting) {
            throw new IllegalStateException("Cannot add an element to this collection as it has already been realized.");
        }
        elements.add(element);
        knownActions.execute(element);
    }

    public void realizeNow() {
        if (state != State.Collecting) {
            throw new IllegalStateException("Cannot realize this collection as it has already been realized.");
        }
        state = State.Realizing;
        knownActions = ImmutableActionSet.empty();
        for (T element : elements) {
            configureActions.execute(element);
        }
        configureActions = ImmutableActionSet.empty();
        for (T element : elements) {
            finalizeActions.execute(element);
        }
        finalizeActions = ImmutableActionSet.empty();
        state = State.Finalized;
    }

    @Override
    public Set<T> get() {
        if (state != State.Finalized) {
            throw new IllegalStateException("Cannot query the elements of this container as the elements have not been created yet.");
        }
        return ImmutableSet.copyOf(elements);
    }

    private class SingleElementProvider<S> extends AbstractProvider<S> implements SwiftBinaryProvider<S> {
        private final Class<S> type;
        private final Spec<? super S> spec;

        SingleElementProvider(Class<S> type, Spec<? super S> spec) {
            this.type = type;
            this.spec = spec;
        }

        @Nullable
        @Override
        public Class<S> getType() {
            return type;
        }

        // Should probably decorate instead
        public void configure(Closure<?> closure) {
            configure(ConfigureUtil.<S>configureUsing(closure));
        }

        @Override
        public void configure(final Action<? super S> action) {
            DefaultSwiftBinaryContainer.this.configureEach(new Action<T>() {
                @Override
                public void execute(T t) {
                    // TODO - contract: is the spec applied pre-configure or post-configure? Both and validate the result hasn't changed? Whatever?
                    // TODO - don't recalculate match multiple times
                    if (type.isInstance(t) && spec.isSatisfiedBy(type.cast(t))) {
                        action.execute(type.cast(t));
                    }
                }
            });
        }

        @Nullable
        @Override
        public S getOrNull() {
            if (state != State.Finalized) {
                return null;
            }
            // TODO - don't recalculate value multiple times
            S match = null;
            for (T element : elements) {
                if (type.isInstance(element) && spec.isSatisfiedBy(type.cast(element))) {
                    if (match != null) {
                        throw new IllegalStateException("Found multiple elements");
                    }
                    match = type.cast(element);
                }
            }
            return match;
        }
    }
}
