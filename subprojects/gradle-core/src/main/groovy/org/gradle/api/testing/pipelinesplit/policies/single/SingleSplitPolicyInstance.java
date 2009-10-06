package org.gradle.api.testing.pipelinesplit.policies.single;

import org.gradle.api.testing.execution.PipelineConfig;
import org.gradle.api.testing.pipelinesplit.policies.AbstractSplitPolicyInstance;
import org.gradle.api.testing.pipelinesplit.policies.SplitPolicyMatcher;

/**
 * @author Tom Eyckmans
 */
public class SingleSplitPolicyInstance extends AbstractSplitPolicyInstance {
    protected SingleSplitPolicyInstance(PipelineConfig config) {
        super(config);
    }

    public SplitPolicyMatcher createSplitPolicyMatcher() {
        return new SinglePipelineSplitPolicyMatcher(config);
    }
}
