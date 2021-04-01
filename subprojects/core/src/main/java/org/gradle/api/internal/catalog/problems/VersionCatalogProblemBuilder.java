/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog.problems;

import org.gradle.problems.StandardSeverity;

import java.util.function.Supplier;

public interface VersionCatalogProblemBuilder {
    ProblemWithId inContext(Supplier<String> context);

    default ProblemWithId inContext(String context) {
        return inContext(() -> context);
    }

    interface ProblemWithId {
        ProblemWithId withSeverity(StandardSeverity severity);

        DescribedProblem withShortDescription(Supplier<String> description);

        default DescribedProblem withShortDescription(String description) {
            return withShortDescription(() -> description);
        }
    }

    interface DescribedProblem {
        DescribedProblem withLongDescription(Supplier<String> description);
        default DescribedProblem withLongDescription(String description) {
            return withLongDescription(() -> description);
        }

        DescribedProblemWithCause happensBecause(Supplier<String> reason);
        default DescribedProblemWithCause happensBecause(String reason) {
            return happensBecause(() -> reason);
        }
    }

    interface DescribedProblemWithCause {
        DescribedProblemWithCause documented();
        DescribedProblemWithCause documentedAt(String page, String section);
        DescribedProblemWithCause addSolution(Supplier<String> solution);
        default DescribedProblemWithCause addSolution(String solution) {
            return addSolution(() -> solution);
        }
    }
}
