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

import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;

/**
 * Allows configuring the parameters for a {@link FlowAction action} connected
 * to a {@link ControlFlowProvider}.
 *
 * @param <T> type of the target made available by the connected {@link ControlFlowProvider}
 * @param <P> the parameters defined by the configured {@link FlowAction dataflow action} type.
 * @since 8.7
 */
@Incubating
public interface ControlFlowActionSpec<T, P extends FlowParameters> extends FlowActionSpec<P> {

    /**
     * {@link ControlFlowProvider} target. <b>Must</b> be connected to a {@link FlowParameters flow parameter}.
     *
     * @since 8.7
     */
    Provider<T> getFlowTarget();
}
