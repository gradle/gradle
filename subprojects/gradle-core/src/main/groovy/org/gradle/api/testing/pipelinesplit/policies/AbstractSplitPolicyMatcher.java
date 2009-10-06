package org.gradle.api.testing.pipelinesplit.policies;

import org.gradle.api.testing.execution.PipelineConfig;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractSplitPolicyMatcher implements SplitPolicyMatcher {
    protected final PipelineConfig config;

    protected AbstractSplitPolicyMatcher(PipelineConfig config) {
        this.config = config;
    }

    public PipelineConfig getConfig() {
        return config;
    }
}
