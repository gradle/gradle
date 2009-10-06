package org.gradle.api.testing.pipelinesplit.policies;

import org.gradle.api.testing.execution.PipelineConfig;

/**
 * @author Tom Eyckmans
 */
public abstract class AbstractSplitPolicyInstance implements SplitPolicyInstance {
    protected final PipelineConfig config;

    protected AbstractSplitPolicyInstance(PipelineConfig config) {
        if (config == null) throw new IllegalArgumentException("config == null!");

        this.config = config;
    }

    public void prepare() {

    }
}
