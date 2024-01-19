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

package org.gradle.kotlin.dsl.flow

import org.gradle.api.Incubating
import org.gradle.api.flow.ControlFlowAction
import org.gradle.api.flow.FlowParameters


/**
 * Syntactic sugar for [control flow actions][ControlFlowAction].
 *
 * Makes the [flow target][getFlowTarget] the receiver of the [execute] operation.
 *
 * @see ControlFlowAction
 * @since 8.7
 */
@Incubating
abstract class KotlinControlFlowAction<T, P : FlowParameters> : ControlFlowAction<T, P> {

    override fun execute(parameters: P) =
        flowTarget.execute(parameters)

    abstract fun T.execute(parameters: P)
}
