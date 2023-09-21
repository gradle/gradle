package org.gradle.api.problems;

import org.gradle.api.NonNullApi;

/**
 * Describes a transformation that can be applied to a problem.
 *
 * These transformers could be added to the {@link Problems} service to transform problems before they are reported.
 */
@FunctionalInterface
@NonNullApi
public interface ProblemTransformer {
    Problem transform(Problem problem);
}
