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

import org.gradle.api.Incubating;

/**
 * Marker interface for {@link FlowAction dataflow action} parameters.
 *
 * <p>
 * Parameter types should be interfaces, only declaring getters for {@link org.gradle.api.provider.Property}-like objects.
 * Example:
 * </p>
 * <pre>
 * public interface MyFlowParameters extends FlowParameters {
 *    Property&lt;String&gt; getString();
 *   {@literal @}ServiceReference Property&lt;MyBuildService&gt; getBuildService();
 * }
 * </pre>
 *
 * @see FlowAction
 * @since 8.1
 */
@Incubating
public interface FlowParameters {
    /**
     * Used for {@link FlowAction dataflow actions} without parameters.
     *
     * <p>When {@link None} is used as parameters, calling {@link FlowActionSpec#getParameters()} throws an exception.</p>
     *
     * @since 8.1
     */
    @Incubating
    final class None implements FlowParameters {
        public final static None INSTANCE = new None();

        private None() {}
    }
}
