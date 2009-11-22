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
package org.gradle.api.testing.execution;

import org.gradle.api.testing.execution.control.refork.ReforkReasons;
import org.gradle.api.testing.execution.control.refork.ReforkItemConfigs;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyConfig;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyNames;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyRegister;
import org.gradle.api.testing.pipelinesplit.policies.SplitPolicyConfig;
import org.gradle.api.testing.pipelinesplit.policies.SplitPolicyNames;
import org.gradle.api.testing.pipelinesplit.policies.SplitPolicyRegister;
import org.gradle.api.testing.reporting.ReportConfig;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Tom Eyckmans
 */
public class PipelineConfig {
    private String name;
    private SplitPolicyConfig splitPolicyConfig;
    private ForkPolicyConfig forkPolicyConfig;
    private ReforkItemConfigs reforkItemConfigs;
    private List<ReportConfig> reports;

    public PipelineConfig(String name) {
        this(name, SplitPolicyRegister.getSplitPolicy(SplitPolicyNames.SINGLE).getSplitPolicyConfigInstance(),
                ForkPolicyRegister.getForkPolicy(ForkPolicyNames.LOCAL_SIMPLE).getForkPolicyConfigInstance());
    }

    public PipelineConfig(String name, SplitPolicyConfig splitPolicyConfig, ForkPolicyConfig forkPolicyConfig) {
        if (splitPolicyConfig == null) {
            throw new IllegalArgumentException("splitPolicyConfig == null!");
        }
        if (forkPolicyConfig == null) {
            throw new IllegalArgumentException("forkPolicyConfig == null!");
        }

        this.name = name;
        this.splitPolicyConfig = splitPolicyConfig;
        this.forkPolicyConfig = forkPolicyConfig;
        this.reforkItemConfigs = new ReforkItemConfigs();
        reforkItemConfigs.addItemConfig(ReforkReasons.AMOUNT_OF_TESTCASES, null); // TODO clean this up
        this.reports = new ArrayList<ReportConfig>();
    }

    public String getName() {
        return name;
    }

    public SplitPolicyConfig getSplitPolicyConfig() {
        return splitPolicyConfig;
    }

    public void setSplitPolicyConfig(SplitPolicyConfig splitPolicyConfig) {
        if (splitPolicyConfig == null) {
            throw new IllegalArgumentException("splitPolicy == null!");
        }

        this.splitPolicyConfig = splitPolicyConfig;
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

    public ReforkItemConfigs getReforkItemConfigs() {
        return reforkItemConfigs;
    }

    public void setReforkItemConfigs(ReforkItemConfigs reforkItemConfigs) {
        this.reforkItemConfigs = reforkItemConfigs;
    }

    public List<ReportConfig> getReports() {
        return reports;
    }

    public void setReports(List<ReportConfig> reports) {
        this.reports = reports;
    }
}
