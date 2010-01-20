/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.testing.execution;

import org.gradle.api.testing.execution.control.refork.ReforkReasonConfigs;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyConfig;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyNames;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyRegister;
import org.gradle.api.testing.pipelinesplit.policies.SinglePipelineSplitPolicy;
import org.gradle.api.testing.pipelinesplit.policies.SplitPolicy;

/**
 * @author Tom Eyckmans
 */
public class PipelineConfig {
    private String name;
    private SplitPolicy splitPolicy;
    private ForkPolicyConfig forkPolicyConfig;
    private ReforkReasonConfigs reforkReasonConfigs;

    public PipelineConfig(String name) {
        this(name, new SinglePipelineSplitPolicy(), 
                ForkPolicyRegister.getForkPolicy(ForkPolicyNames.LOCAL_SIMPLE).getForkPolicyConfigInstance());
    }

    public PipelineConfig(String name, SplitPolicy splitPolicy, ForkPolicyConfig forkPolicyConfig) {
        if (splitPolicy == null) {
            throw new IllegalArgumentException("splitPolicyConfig == null!");
        }
        if (forkPolicyConfig == null) {
            throw new IllegalArgumentException("forkPolicyConfig == null!");
        }

        this.name = name;
        this.splitPolicy = splitPolicy;
        this.forkPolicyConfig = forkPolicyConfig;
        this.reforkReasonConfigs = new ReforkReasonConfigs();
    }

    public String getName() {
        return name;
    }

    public SplitPolicy getSplitPolicyConfig() {
        return splitPolicy;
    }

    public void setSplitPolicyConfig(SplitPolicy splitPolicy) {
        if (splitPolicy == null) {
            throw new IllegalArgumentException("splitPolicy == null!");
        }

        this.splitPolicy = splitPolicy;
    }

    public ForkPolicyConfig getForkPolicyConfig() {
        return forkPolicyConfig;
    }

    public void setForkPolicyConfig(ForkPolicyConfig forkPolicyConfig) {
        if (forkPolicyConfig == null) {
            throw new IllegalArgumentException("forkPolicyConfig == null!");
        }

        this.forkPolicyConfig = forkPolicyConfig;
    }

    public ReforkReasonConfigs getReforkReasonConfigs() {
        return reforkReasonConfigs;
    }

    public void setReforkReasonConfigs(ReforkReasonConfigs reforkReasonConfigs) {
        this.reforkReasonConfigs = reforkReasonConfigs;
    }
}
