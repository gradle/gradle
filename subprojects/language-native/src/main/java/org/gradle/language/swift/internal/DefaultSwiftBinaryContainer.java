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
import org.gradle.internal.MutableActionSet;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftBinaryContainer;

import javax.inject.Inject;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public class DefaultSwiftBinaryContainer implements SwiftBinaryContainer {
    private final ProviderFactory providerFactory;
    private final Set<SwiftBinary> elements = new LinkedHashSet<SwiftBinary>();
    private final MutableActionSet<SwiftBinary> finalizeActions = new MutableActionSet<SwiftBinary>();
    private final MutableActionSet<SwiftBinary> configureActions = new MutableActionSet<SwiftBinary>();

    @Inject
    public DefaultSwiftBinaryContainer(ProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    @Override
    public <T extends SwiftBinary> Provider<T> get(final Class<T> type, final Spec<? super T> spec) {
        return providerFactory.provider(new Callable<T>() {
            @Override
            public T call() {
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
    public void whenElementFinalized(Action<? super SwiftBinary> action) {
        finalizeActions.add(action);
        for (SwiftBinary element : elements) {
            action.execute(element);
        }
    }

    @Override
    public void configureEach(Action<? super SwiftBinary> action) {
        configureActions.add(action);
        for (SwiftBinary element : elements) {
            action.execute(element);
        }
    }

    void add(SwiftBinary element) {
        elements.add(element);
        configureActions.execute(element);
        finalizeActions.execute(element);
    }

    @Override
    public Set<SwiftBinary> get() {
        return ImmutableSet.copyOf(elements);
    }
}
