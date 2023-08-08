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

package org.gradle.api.problems.interfaces;

import org.gradle.api.Incubating;

import javax.annotation.Nullable;

/**
 * A builder interface for {@link Problem} instances.
 *
 * This is the last interface in the builder chain.
 *
 * before this can be used the following interfaces must be used in order:
 * <ul>
 *     <li>{@link ProblemBuilderDefiningDocumentation}
 *     <li>{@link ProblemBuilderDefiningLocation}
 *     <li>{@link ProblemBuilderDefiningMessage}
 *     <li>{@link ProblemBuilderDefiningType}
 *     <li>{@link ProblemBuilderDefiningGroup}
 * </ul>
 *
 * An example of how to use the builder:
 * <pre>{@code
 *  <problemService>.createProblemBuilder()
 *          .undocumented()
 *          .noLocation()
 *          .severity(Severity.ERROR)
 *          .message("test problem")
 *          .type(ValidationProblemId.TEST_PROBLEM.name())
 *          .group(ProblemGroup.TYPE_VALIDATION)
 *          .description("this is a test")
 *  }</pre>
 *
 * @since 8.4
 */
@Incubating
public interface ProblemBuilder {

    ProblemBuilder description(String description);

    ProblemBuilder solution(@Nullable String solution);

    ProblemBuilder cause(Throwable cause);

    ProblemBuilder withMetadata(String key, String value);

    ProblemBuilder withException(RuntimeException e);

    ProblemBuilder severity(Severity severity);

    Problem build();

    void report();

    RuntimeException throwIt();
}
