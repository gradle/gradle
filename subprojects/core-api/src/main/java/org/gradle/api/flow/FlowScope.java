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

package org.gradle.api.flow;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Augments the cached work graph with {@link FlowAction dataflow actions}, anonymous, parameterized and
 * isolated pieces of work that are triggered solely based on the availability of their input parameters.
 *
 * @since 8.1
 */
@Incubating
@ServiceScope(Scopes.Build.class)
public interface FlowScope {

    /**
     * Registers a {@link FlowAction dataflow action} that's always part of the dataflow graph.
     *
     * @param action the {@link FlowAction dataflow action} type.
     * @param configure configuration for the given {@link FlowAction dataflow action} parameters.
     * @param <P> the parameters defined by the given {@link FlowAction dataflow action} type.
     * @return a {@link Registration} object representing the registered action.
     */
    <P extends FlowParameters> Registration<P> always(
        Class<? extends FlowAction<P>> action,
        Action<? super FlowActionSpec<P>> configure
    );

    /**
     * Represents a registered {@link FlowAction dataflow action}.
     *
     * @param <P> the parameters defined by the given {@link FlowAction dataflow action}.
     * @since 8.1
     */
    @Incubating
    interface Registration<P extends FlowParameters> {
    }
}
