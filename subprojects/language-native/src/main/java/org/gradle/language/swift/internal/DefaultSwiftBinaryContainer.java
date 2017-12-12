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
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.internal.MutableActionSet;
import org.gradle.language.swift.SwiftBinary;
import org.gradle.language.swift.SwiftBinaryContainer;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public class DefaultSwiftBinaryContainer implements SwiftBinaryContainer {
    private final Set<SwiftBinary> elements = new LinkedHashSet<SwiftBinary>();
    private final MutableActionSet<SwiftBinary> actions = new MutableActionSet<SwiftBinary>();

    @Override
    public <T extends SwiftBinary> Provider<T> get(final Class<T> type, final Spec<? super T> spec) {
        return new DefaultProvider<T>(new Callable<T>() {
            @Override
            public T call() throws Exception {
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
    public void whenElementFinalized(Action<? super SwiftBinary> action) {
        actions.add(action);
        for (SwiftBinary element : elements) {
            action.execute(element);
        }
    }

    void add(SwiftBinary element) {
        elements.add(element);
        actions.execute(element);
    }

    @Override
    public Set<SwiftBinary> get() {
        return ImmutableSet.copyOf(elements);
    }
}
