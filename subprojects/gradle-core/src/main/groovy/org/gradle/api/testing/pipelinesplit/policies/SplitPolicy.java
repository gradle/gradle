package org.gradle.api.testing.pipelinesplit.policies;

import org.gradle.api.testing.execution.PipelineConfig;

/**
 * @author Tom Eyckmans
 */
public interface SplitPolicy {
    SplitPolicyConfig getSplitPolicyConfigInstance();

    SplitPolicyName getName();

    SplitPolicyInstance getSplitPolicyInstance(PipelineConfig config);
}
