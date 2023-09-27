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

    /**
     * Transforms the given problem.
     * The returned problem will be reported instead of the original problem.
     *
     * <p>
     * Transformations do not need to create a new problem, they can also modify the given problem.
     *
     * @param problem the problem to transform
     * @return the transformed problem
     */
    Problem transform(Problem problem);

}
