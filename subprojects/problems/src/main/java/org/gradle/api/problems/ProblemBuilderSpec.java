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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * A function that can be used to specify a {@link Problem} using a {@link ProblemBuilder}.
 * <p>
 * Usage example:
 *
 * <pre>
 * throw getProblemService().throwing(builder -&gt;
 *        builder.label(message)
 *            .undocumented()
 *            .noLocation()
 *            .type("task_selection")
 *            .group(ProblemGroup.GENERIC_ID)
 *            .details("long message")
 *            .severity(Severity.ERROR)
 *            .withException(new TaskSelectionException(message)));
 * </pre>
 *
 * Using this instead of an {@link org.gradle.api.Action} forces the user to specify all required properties of a {@link Problem}.
 *
 * @since 8.4
 */
@Incubating
public interface ProblemBuilderSpec {

    ProblemConfigurator apply(ProblemBuilderDefiningLabel builder);
}
