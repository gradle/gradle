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
 * @since 8.4
 */
@Incubating
public interface ProblemBuilder {
    ProblemBuilder group(ProblemGroup group);
    ProblemBuilder message(String message);

    ProblemBuilder severity(Severity severity);

    ProblemBuilder location(String path, Integer line);

    ProblemBuilder noLocation();

    ProblemBuilder description(String description);

    ProblemBuilder documentedAt(DocLink doc);

    ProblemBuilder undocumented();

    ProblemBuilder type(String problemType);

    ProblemBuilder solution(@Nullable String solution);

    ProblemBuilder cause(Throwable cause);

    ProblemBuilder withMetadata(String key, String value);

    Problem build();

    void report();

    RuntimeException throwIt();
}
