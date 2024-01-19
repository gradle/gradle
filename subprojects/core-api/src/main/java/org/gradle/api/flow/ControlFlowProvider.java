/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.flow;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Triggers an associated {@link ControlFlowAction} at a specific point in the lifecycle of
 * objects of type {@link T}.
 *
 * @param <T> type of the object made available as the {@link ControlFlowAction#getFlowTarget() control flow action target}
 * @since 8.7
 */
@Incubating
public interface ControlFlowProvider<T> {

    /**
     * Connects a {@link ControlFlowAction control flow action} to this {@link ControlFlowProvider control flow}.
     *
     * @param flowRegistration the {@link ControlFlowAction control flow action} {@link FlowScope#register(Class, Action) registration}.
     * @see FlowScope#register(Class, Action)
     * @since 8.7
     */
    void onEach(ControlFlowRegistration<? extends ControlFlowAction<? super T, ?>> flowRegistration);
}
