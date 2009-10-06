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
package org.gradle.api.testing.execution.fork.policies;

import org.gradle.api.testing.execution.fork.policies.local.single.LocalSimpleForkPolicy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tom Eyckmans
 */
public class ForkPolicyRegister {
    private static final Map<ForkPolicyName, ForkPolicy> splitPolicies = new ConcurrentHashMap<ForkPolicyName, ForkPolicy>();

    static {
        registerForkPolicy(new LocalSimpleForkPolicy());
    }

    public static void registerForkPolicy(final ForkPolicy forkPolicy) {
        if (forkPolicy == null) throw new IllegalArgumentException("forkPolicy == null!");

        final ForkPolicyName forkPolicyName = forkPolicy.getName();

        if (forkPolicyName == null) throw new IllegalArgumentException("forkPolicy.name == null!");
        if (splitPolicies.containsKey(forkPolicyName))
            throw new IllegalArgumentException("split polciy (" + forkPolicyName + ") already registered!");

        splitPolicies.put(forkPolicyName, forkPolicy);
    }

    public static ForkPolicy getForkPolicy(final ForkPolicyName forkPolicyName) {
        if (forkPolicyName == null) throw new IllegalArgumentException("forkPolicyName == null!");

        return splitPolicies.get(forkPolicyName);
    }
}
