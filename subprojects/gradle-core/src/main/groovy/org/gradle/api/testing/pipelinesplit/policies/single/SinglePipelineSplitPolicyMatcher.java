package org.gradle.api.testing.pipelinesplit.policies.single;

import org.gradle.api.testing.execution.PipelineConfig;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.pipelinesplit.policies.AbstractSplitPolicyMatcher;

/**
 * @author Tom Eyckmans
 */
public class SinglePipelineSplitPolicyMatcher extends AbstractSplitPolicyMatcher {

    public SinglePipelineSplitPolicyMatcher(PipelineConfig config) {
        super(config);
    }

    public boolean match(TestClassRunInfo testClassRunInfo) {
        return true;
    }
}
