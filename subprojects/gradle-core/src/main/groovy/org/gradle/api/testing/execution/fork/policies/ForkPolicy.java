package org.gradle.api.testing.execution.fork.policies;

import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.execution.fork.ForkControl;

/**
 * @author Tom Eyckmans
 */
public interface ForkPolicy {
    ForkPolicyName getName();

    ForkPolicyConfig getForkPolicyConfigInstance();

    ForkPolicyInstance getForkPolicyInstance(Pipeline pipeline, ForkControl forkControl);
}
