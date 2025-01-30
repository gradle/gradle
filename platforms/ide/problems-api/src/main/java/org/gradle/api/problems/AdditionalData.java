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

package org.gradle.api.problems;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

import java.io.Serializable;

/**
 * Marker interface for additional data that can be attached to a {@link Problem}.
 * <p>
 * This is effectively a sealed interface that is used to restrict the types of additional data that can be attached to a problem.
 *
 * @see ProblemSpec#additionalData(Class, Action)
 * @since 8.13
 */
@Incubating
public interface AdditionalData extends Serializable {

}
