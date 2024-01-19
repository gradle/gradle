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
 * A {@link FlowAction flow action} that can react to {@link ControlFlowProvider control flow events}.
 *
 * @param <T> expected {@link #getFlowTarget() control flow target object type}
 * @param <P> the parameters defined by the configured {@link FlowAction flow action} type.
 * @see FlowScope#register(Class, Action)
 * @see ControlFlowProvider#onEach(ControlFlowRegistration)
 * @since 8.7
 */
@Incubating
public interface ControlFlowAction<T, P extends FlowParameters> extends FlowAction<P> {

    /**
     * Target of the associated {@link ControlFlowProvider control flow event}.
     *
     * @see ControlFlowProvider#onEach(ControlFlowRegistration)
     * @since 8.7
     */
    T getFlowTarget();
}
