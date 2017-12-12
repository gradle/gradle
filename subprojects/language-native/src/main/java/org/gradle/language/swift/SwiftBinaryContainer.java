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

package org.gradle.language.swift;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

import java.util.Set;

/**
 * A collection of binaries of a component.
 *
 * @since 4.5
 */
@Incubating
public interface SwiftBinaryContainer {
    /**
     * Returns a single binary matching the specified type and specification.
     *
     * @param type subtype to match
     * @param spec specification to satisfy
     * @param <T> type of the binary to return
     * @return a binary from the collection in a unspecified state
     */
    <T extends SwiftBinary> Provider<T> get(Class<T> type, Spec<? super T> spec);

    /**
     * Register an action to execute when an element is finalized.
     *
     * @param action The action to execute for each element when finalized.
     */
    void whenElementFinalized(Action<? super SwiftBinary> action);

    /**
     * Returns the set of binaries from the collection in a unspecified state.
     */
    Set<SwiftBinary> get();
}
