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

package org.gradle.language;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;

import java.util.Set;

/**
 * A collection of binaries that are created and configured as they are required.
 *
 * <p>Each element in this collection passes through several states. The element is created and becomes 'known'. The element is passed to any actions registered using {@link #whenElementKnown(Action)}. The element is then configured using any actions registered using {@link #configureEach(Action)} and becomes 'finalized'. The element is passed to any actions registered using {@link #whenElementFinalized(Action)}. Elements are created and configured only when required.
 *
 * @param <T> type of the elements in this container.
 * @since 4.5
 */
@Incubating
public interface BinaryCollection<T extends SoftwareComponent> {
    /**
     * Returns a {@link BinaryProvider} that contains the single binary matching the specified type and specification. The binary will be in the finalized state. The provider can be used to apply configuration to the element before it is finalized.
     *
     * <p>Querying the return value will fail when there is not exactly one matching binary.
     *
     * @param type type to match
     * @param spec specification to satisfy. The spec is applied to each binary <em>prior</em> to configuration.
     * @param <S> type of the binary to return
     * @return a binary from the collection in a finalized state
     */
    <S> BinaryProvider<S> get(Class<S> type, Spec<? super S> spec);

    /**
     * Returns a {@link BinaryProvider} that contains the single binary with the given name. The binary will be in the finalized state. The provider can be used to apply configuration to the element before it is finalized.
     *
     * <p>Querying the return value will fail when there is not exactly one matching binary.
     *
     * @param name The name of the binary
     * @return a binary from the collection in a finalized state
     */
    BinaryProvider<T> getByName(String name);

    /**
     * Returns a {@link Provider} that contains the single binary matching the given specification. The binary will be in the finalized state. The provider can be used to apply configuration to the element before it is finalized.
     *
     * <p>Querying the return value will fail when there is not exactly one matching binary.
     *
     * @param spec specification to satisfy. The spec is applied to each binary prior to configuration.
     * @return a binary from the collection in a finalized state
     */
    BinaryProvider<T> get(Spec<? super T> spec);

    /**
     * Registers an action to execute when an element becomes known. The action is only executed for those elements that are required. Fails if any element has already been finalized.
     *
     * @param action The action to execute for each element becomes known.
     */
    void whenElementKnown(Action<? super T> action);

    /**
     * Registers an action to execute when an element of the given type becomes known. The action is only executed for those elements that are required. Fails if any matching element has already been finalized.
     *
     * @param type The type of element to select.
     * @param action The action to execute for each element becomes known.
     */
    <S> void whenElementKnown(Class<S> type, Action<? super S> action);

    /**
     * Registers an action to execute when an element is finalized. The action is only executed for those elements that are required. Fails if any element has already been finalized.
     *
     * @param action The action to execute for each element when finalized.
     */
    void whenElementFinalized(Action<? super T> action);

    /**
     * Registers an action to execute when an element of the given type is finalized. The action is only executed for those elements that are required. Fails if any matching element has already been finalized.
     *
     * @param type The type of element to select.
     * @param action The action to execute for each element when finalized.
     */
    <S> void whenElementFinalized(Class<S> type, Action<? super S> action);

    /**
     * Registers an action to execute to configure each element in the collection. The action is only executed for those elements that are required. Fails if any element has already been finalized.
     *
     * @param action The action to execute on each element for configuration.
     */
    void configureEach(Action<? super T> action);

    /**
     * Registers an action to execute to configure each element of the given type in the collection. The action is only executed for those elements that are required. Fails if any matching element has already been finalized.
     *
     * @param type The type of element to select.
     * @param action The action to execute on each element for configuration.
     */
    <S> void configureEach(Class<S> type, Action<? super S> action);

    /**
     * Returns the set of binaries from this collection. Elements are in a finalized state.
     */
    Set<T> get();
}
