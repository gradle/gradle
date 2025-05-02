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

package org.gradle.declarative.dsl.evaluation

import java.io.Serializable


/**
 * Represents the "generation" of a particular operation (either an addition function call or a property assignment operation).
 *
 * The order of generations is important as calls in later generations can override calls in earlier generations, but no the
 * other way around.
 * For instance, a property assignment can override a convention assignment, but a convention assignment cannot override a property assignment.
 */
interface OperationGenerationId : Comparable<OperationGenerationId>, Serializable {
    val ordinal: Int
}
