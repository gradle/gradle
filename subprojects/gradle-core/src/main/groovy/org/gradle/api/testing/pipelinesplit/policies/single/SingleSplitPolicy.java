package org.gradle.api.testing.pipelinesplit.policies.single;

import org.gradle.api.testing.execution.PipelineConfig;
import org.gradle.api.testing.pipelinesplit.policies.*;

/**
 * @author Tom Eyckmans
 */
public class SingleSplitPolicy implements SplitPolicy {

    public SplitPolicyConfig getSplitPolicyConfigInstance() {
        return new SplitPolicyConfig(getName());
    }

    public SplitPolicyName getName() {
        return SplitPolicyNames.SINGLE;
    }

    public SplitPolicyInstance getSplitPolicyInstance(PipelineConfig config) {
        if (config == null) throw new IllegalArgumentException("config == null!");

        return new SingleSplitPolicyInstance(config);
    }
}
