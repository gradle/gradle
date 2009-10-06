package org.gradle.api.testing.pipelinesplit.policies;

import org.gradle.api.testing.pipelinesplit.policies.single.SingleSplitPolicy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tom Eyckmans
 */
public class SplitPolicyRegister {
    private static final Map<SplitPolicyName, SplitPolicy> splitPolicies = new ConcurrentHashMap<SplitPolicyName, SplitPolicy>();

    static {
        registerSplitPolicy(new SingleSplitPolicy());
    }

    public static void registerSplitPolicy(final SplitPolicy splitPolicy) {
        if (splitPolicy == null) throw new IllegalArgumentException("splitPolicy == null!");

        final SplitPolicyName splitPolicyName = splitPolicy.getName();

        if (splitPolicyName == null) throw new IllegalArgumentException("splitPolicy.name == null!");
        if (splitPolicies.containsKey(splitPolicyName))
            throw new IllegalArgumentException("split polciy (" + splitPolicyName + ") already registered!");

        splitPolicies.put(splitPolicyName, splitPolicy);
    }

    public static SplitPolicy getSplitPolicy(final SplitPolicyName splitPolicyName) {
        if (splitPolicyName == null) throw new IllegalArgumentException("splitPolicyName == null!");

        return splitPolicies.get(splitPolicyName);
    }
}
