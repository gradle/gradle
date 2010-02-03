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
package org.gradle.api.tasks.testing;

import org.gradle.api.GradleException;
import org.gradle.api.testing.TestOrchestrator;
import org.gradle.api.testing.execution.PipelineConfig;
import org.gradle.api.testing.execution.control.refork.*;
import org.gradle.api.testing.execution.fork.policies.local.single.LocalSimpleForkPolicyConfig;
import org.gradle.api.testing.fabric.TestMethodProcessResultStates;
import org.gradle.api.testing.reporting.policies.ReportPolicy;
import org.gradle.api.testing.reporting.policies.console.ConsoleReportPolicy;
import org.gradle.util.GUtil;

import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class NativeTest extends AbstractTestTask {
    private PipelineConfig defaultPipelineConfig;
    private Map<String, PipelineConfig> pipelineConfigs;
    private List<ReportPolicy> reportConfigs;

    private int maximumNumberOfForks = Integer.MAX_VALUE; // +/- no limit
    private int amountOfForksToStart = 1; // default
    private double lowMemoryThreshold = -1;

    public NativeTest() {
        super();
        pipelineConfigs = new HashMap<String, PipelineConfig>();

        // default pipeline
        defaultPipelineConfig = new PipelineConfig("default");

        pipelineConfigs.put(defaultPipelineConfig.getName(), defaultPipelineConfig);

        reportConfigs = new ArrayList<ReportPolicy>();

        // by default we report errors and failures to the console
        ConsoleReportPolicy config = new ConsoleReportPolicy();
        config.addShowStates(TestMethodProcessResultStates.values()); // add all

        addReportConfing(config);
    }

    public void executeTests() {
        ((LocalSimpleForkPolicyConfig) defaultPipelineConfig.getForkPolicyConfig()).setAmountToStart(
                amountOfForksToStart);

        ReforkReasonConfigs reforkReasonConfigs = null;

        Long forkEvery = getForkEvery();
        if (forkEvery != null) {
            if (reforkReasonConfigs == null) {
                reforkReasonConfigs = new ReforkReasonConfigs();
            }

            final AmountOfTestCasesConfig reforkEveryConfig = (AmountOfTestCasesConfig) ReforkReasonRegister
                    .getReforkReason(ReforkReasons.AMOUNT_OF_TESTCASES).getConfig();

            reforkEveryConfig.setReforkEvery(forkEvery);

            reforkReasonConfigs.addOrUpdateReforkReasonConfig(reforkEveryConfig);
        }

        if (lowMemoryThreshold > 0) {
            if (reforkReasonConfigs == null) {
                reforkReasonConfigs = new ReforkReasonConfigs();
            }

            final ForkMemoryLowConfig forkMemoryLowConfig = (ForkMemoryLowConfig) ReforkReasonRegister.getReforkReason(
                    ReforkReasons.FORK_MEMORY_LOW).getConfig();

            forkMemoryLowConfig.setMemoryLowThreshold(lowMemoryThreshold);

            reforkReasonConfigs.addOrUpdateReforkReasonConfig(forkMemoryLowConfig);
        }

        if (reforkReasonConfigs != null) {
            defaultPipelineConfig.setReforkReasonConfigs(reforkReasonConfigs);
        }

        final TestOrchestrator orchestrator = new TestOrchestrator(this);

        orchestrator.execute();

        // TODO move to a test detection ended listener and zero detected tests
        // logger.debug("skipping test execution, because no tests were found");

        // TODO add reporting
        //if (testReport) {
        //    testFrameworkInstance.report(getProject(), this);
        //}

        // TODO don't use ant project property?
        if (!isIgnoreFailures() && GUtil.isTrue(getProject().getAnt().getProject().getProperty(
                FAILURES_OR_ERRORS_PROPERTY))) {
            if (isTestReport()) {
                throw new GradleException("There were failing tests. See the report at " + getTestReportDir() + ".");
            } else {
                throw new GradleException("There were failing tests.");
            }
        }
    }

    public Map<String, PipelineConfig> getPipelineConfigs() {
        return pipelineConfigs;
    }

    public void addPipelineConfig(PipelineConfig pipelineConfig) {
        if (pipelineConfig == null) {
            throw new IllegalArgumentException("pipelineConfig can't be null!");
        }

        pipelineConfigs.put(pipelineConfig.getName(), pipelineConfig);
    }

    public Collection<ReportPolicy> getReportConfigs() {
        return reportConfigs;
    }

    public void addReportConfing(ReportPolicy report) {
        if (report == null) {
            throw new IllegalArgumentException("reportConfig can't be null!");
        }

        reportConfigs.add(report);
    }

    public int getMaximumNumberOfForks() {
        return maximumNumberOfForks;
    }

    public void setMaximumNumberOfForks(int maximumNumberOfForks) {
        if (maximumNumberOfForks < 1) {
            throw new IllegalArgumentException("maximumNumberOfForks can't be lower than 1!");
        }

        this.maximumNumberOfForks = maximumNumberOfForks;
    }

    public int getAmountOfForksToStart() {
        return amountOfForksToStart;
    }

    public void setAmountOfForksToStart(int amountOfForksToStart) {
        if (amountOfForksToStart < 1) {
            throw new IllegalArgumentException("amountOfForksToStart can't be lower than 1!");
        }

        this.amountOfForksToStart = amountOfForksToStart;
    }

    public double getLowMemoryThreshold() {
        return lowMemoryThreshold;
    }

    public void setLowMemoryThreshold(double lowMemoryThreshold) {
        if (lowMemoryThreshold <= 0) {
            throw new IllegalArgumentException("lowMemoryThreshold can't be lower or equal to zero!");
        }

        this.lowMemoryThreshold = lowMemoryThreshold;
    }
}