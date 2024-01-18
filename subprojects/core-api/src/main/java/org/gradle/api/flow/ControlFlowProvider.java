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

/**
 * Triggers an associated {@link FlowAction} at a specific point in the lifecycle of
 * potentially stateful objects of type {@link T}.
 *
 * @param <T> type of the potentially stateful object made available at a specific point in time
 * @since 8.7
 */
@Incubating
public interface ControlFlowProvider<T> {
}
