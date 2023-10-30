/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.events.problems;

import org.gradle.api.Incubating;
import org.gradle.tooling.events.OperationDescriptor;

/**
 * Describes a problem operation.
 * <p>
 * The Problems API in an incubating stage and the associated classes are likely to change in a non-compatible way.
 * Consequently, the Tooling API only exposes problem instances with dynamic data in json format without any guarantees.
 *
 * @since 8.4
 */
@Incubating
public interface ProblemDescriptor extends OperationDescriptor {

    /**
     * The problem properties in JSON format.
     *
     * @return the problem properties.
     */
    String getJson();
}
