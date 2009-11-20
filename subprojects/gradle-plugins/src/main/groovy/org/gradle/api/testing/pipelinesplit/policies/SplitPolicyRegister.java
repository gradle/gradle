/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.testing.pipelinesplit.policies;

import org.gradle.api.testing.pipelinesplit.policies.single.SingleSplitPolicy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tom Eyckmans
 */
public class SplitPolicyRegister {
    private static final Map<SplitPolicyName, SplitPolicy> SPLIT_POLICIES
            = new ConcurrentHashMap<SplitPolicyName, SplitPolicy>();

    static {
        registerSplitPolicy(new SingleSplitPolicy());
    }

    public static void registerSplitPolicy(final SplitPolicy splitPolicy) {
        if (splitPolicy == null) {
            throw new IllegalArgumentException("splitPolicy == null!");
        }

        final SplitPolicyName splitPolicyName = splitPolicy.getName();

        if (splitPolicyName == null) {
            throw new IllegalArgumentException("splitPolicy.name == null!");
        }
        if (SPLIT_POLICIES.containsKey(splitPolicyName)) {
            throw new IllegalArgumentException("split polciy (" + splitPolicyName + ") already registered!");
        }

        SPLIT_POLICIES.put(splitPolicyName, splitPolicy);
    }

    public static SplitPolicy getSplitPolicy(final SplitPolicyName splitPolicyName) {
        if (splitPolicyName == null) {
            throw new IllegalArgumentException("splitPolicyName == null!");
        }

        return SPLIT_POLICIES.get(splitPolicyName);
    }
}
