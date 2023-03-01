/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configuration.internal;

import java.util.function.Consumer;

/**
 * <p>
 * A context tracking tool for calls to dynamic methods.
 * Notifies the {@code onEnter} and {@code onLeave} listeners upon entering and leaving calls,
 * which is triggered by {@code enterDynamicCall} and {@code leaveDynamicCall}.
 * <p>
 * An entry point is a boundary between non-dynamic code and a dynamic lookup. For example,
 * one such boundary is {@code Project.hasProperty}. The object used for entry point is checked
 * for equality with the argument passed to {@code leaveDynamicCall}.
 * <p>
 * The implementations should be thread-safe and should support tracking the context in multiple threads.
 */
// TODO: consider transforming this to a more generic tool for tracking the events
//       of entering and leaving some generic context (marked with a key?)
public interface DynamicCallContextTracker {
    /**
     * Notifies the tracker that the control flow entered the context of a new dynamic call.
     * The tracker itself notifies all the registered listeners.
     * @param entryPoint the key to identify the dynamic call
     */
    void enterDynamicCall(Object entryPoint);

    /**
     * Notifies the tracker that the dynamic call ended.
     * The tracker itself notifies all the registered listeners.
     * @param entryPoint the key identifying the dynamic call, should match the key passed to {@code enterDynamicCall}.
     * @throws IllegalStateException if the {@code entryPoint} does not match the one stored in the call entry.
     */
    void leaveDynamicCall(Object entryPoint);

    /**
     * Registers a listener for {@code enterDynamicCall} events.
     */
    void onEnter(Consumer<Object> listener);

    /**
     * Registers a listener for {@code leaveDynamicCall} events.
     */
    void onLeave(Consumer<Object> listener);
}
