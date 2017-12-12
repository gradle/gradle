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
import org.gradle.api.Action;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftBinaryContainer;

import javax.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

// TODO - error messages
// TODO - display names for this container and the Provider implementations
public class DefaultSwiftBinaryContainer implements SwiftBinaryContainer {
    private enum State {
        Collecting, Realizing, Finalized
    }

    private State state = State.Collecting;
    private final ProviderFactory providerFactory;
    private final Set<SwiftBinary> elements = new LinkedHashSet<SwiftBinary>();
    private ImmutableActionSet<SwiftBinary> knownActions = ImmutableActionSet.empty();
    private ImmutableActionSet<SwiftBinary> configureActions = ImmutableActionSet.empty();
    private ImmutableActionSet<SwiftBinary> finalizeActions = ImmutableActionSet.empty();

    @Inject
    public DefaultSwiftBinaryContainer(ProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    @Override
    public <T extends SwiftBinary> Provider<T> get(final Class<T> type, final Spec<? super T> spec) {
        return providerFactory.provider(new Callable<T>() {
            @Override
            public T call() {
                if (state != State.Finalized) {
                    return null;
                }
                // TODO - don't recalculate value multiple times
                T match = null;
                for (SwiftBinary element : elements) {
                    if (type.isInstance(element) && spec.isSatisfiedBy(type.cast(element))) {
                        if (match != null) {
                            throw new IllegalStateException("Found multiple elements");
                        }
                        match = type.cast(element);
                    }
                }
                return match;
            }
        });
    }

    @Override
    public Provider<SwiftBinary> getByName(final String name) {
        return get(new Spec<SwiftBinary>() {
            @Override
            public boolean isSatisfiedBy(SwiftBinary element) {
                return element.getName().equals(name);
            }
        });
    }

    @Override
    public Provider<SwiftBinary> get(Spec<? super SwiftBinary> spec) {
        return get(SwiftBinary.class, spec);
    }

    @Override
    public void whenElementKnown(Action<? super SwiftBinary> action) {
        if (state != State.Collecting) {
            throw new IllegalStateException("Cannot add actions to this collection as it has already been realized.");
        }
        knownActions = knownActions.add(action);
        for (SwiftBinary element : elements) {
            action.execute(element);
        }
    }

    @Override
    public void whenElementFinalized(Action<? super SwiftBinary> action) {
        if (state != State.Collecting) {
            throw new IllegalStateException("Cannot add actions to this collection as it has already been realized.");
        }
        finalizeActions = finalizeActions.add(action);
    }

    @Override
    public void configureEach(Action<? super SwiftBinary> action) {
        if (state != State.Collecting) {
            throw new IllegalStateException("Cannot add actions to this collection as it has already been realized.");
        }
        configureActions = configureActions.add(action);
    }

    void add(SwiftBinary element) {
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
        for (SwiftBinary element : elements) {
            configureActions.execute(element);
        }
        configureActions = ImmutableActionSet.empty();
        for (SwiftBinary element : elements) {
            finalizeActions.execute(element);
        }
        finalizeActions = ImmutableActionSet.empty();
        state = State.Finalized;
    }

    @Override
    public Set<SwiftBinary> get() {
        if (state != State.Finalized) {
            throw new IllegalStateException("Cannot query the elements of this container as the elements have not been created yet.");
        }
        return ImmutableSet.copyOf(elements);
    }
}
