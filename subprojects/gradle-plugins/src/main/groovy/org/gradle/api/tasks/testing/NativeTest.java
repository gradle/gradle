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
package org.gradle.api.tasks.testing;

import org.gradle.api.GradleException;
import org.gradle.api.testing.TestOrchestrator;
import org.gradle.api.testing.fabric.TestMethodProcessResultStates;
import org.gradle.api.testing.execution.PipelineConfig;
import org.gradle.api.testing.execution.fork.policies.local.single.LocalSimpleForkPolicyConfig;
import org.gradle.api.testing.execution.control.refork.*;
import org.gradle.api.testing.reporting.ReportConfig;
import org.gradle.api.testing.reporting.policies.ReportPolicyNames;
import org.gradle.api.testing.reporting.policies.ReportPolicyRegister;
import org.gradle.api.testing.reporting.policies.console.ConsoleReportPolicyConfig;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public class NativeTest extends AbstractTestTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeTest.class);

    private long reforkEvery = -1; // Don't refork

    private PipelineConfig defaultPipelineConfig;
    private Map<String, PipelineConfig> pipelineConfigs;
    private ReportConfig consoleReport;
    private Map<String, ReportConfig> reportConfigs;

    private int maximumNumberOfForks = Integer.MAX_VALUE; // +/- no limit
    private int amountOfForksToStart = 1; // default
    private double lowMemoryThreshold = -1;

    public NativeTest() {
        super();
        pipelineConfigs = new HashMap<String, PipelineConfig>();

        // default pipeline
        defaultPipelineConfig = new PipelineConfig("default");

        pipelineConfigs.put(defaultPipelineConfig.getName(), defaultPipelineConfig);

        reportConfigs = new HashMap<String, ReportConfig>();

        // by default we report errors and failures to the console
        consoleReport = new ReportConfig("console");
        consoleReport.setPolicyConfig(ReportPolicyRegister.getReportPolicy(
                ReportPolicyNames.CONSOLE).createReportPolicyConfigInstance());
        final ConsoleReportPolicyConfig reportPolicyConfig = (ConsoleReportPolicyConfig) consoleReport
                .getPolicyConfig();
        reportPolicyConfig.addShowStates(TestMethodProcessResultStates.values()); // add all 

        reportConfigs.put(consoleReport.getName(), consoleReport);

        defaultPipelineConfig.getReports().add(consoleReport);
    }

    public void executeTests() {
        ((LocalSimpleForkPolicyConfig) defaultPipelineConfig.getForkPolicyConfig()).setAmountToStart(
                amountOfForksToStart);

        ReforkReasonConfigs reforkReasonConfigs = null;

        if (reforkEvery >= 1) {
            if (reforkReasonConfigs == null) {
                reforkReasonConfigs = new ReforkReasonConfigs();
            }

            final AmountOfTestCasesConfig reforkEveryConfig = (AmountOfTestCasesConfig) ReforkReasonRegister
                    .getReforkReason(ReforkReasons.AMOUNT_OF_TESTCASES).getConfig();

            reforkEveryConfig.setReforkEvery(reforkEvery);

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
            if (testReport) {
                throw new GradleException("There were failing tests. See the report at " + getTestReportDir() + ".");
            } else {
                throw new GradleException("There were failing tests.");
            }
        }
    }

    public long getReforkEvery() {
        return reforkEvery;
    }

    public void setReforkEvery(long reforkEvery) {
        if (reforkEvery <= 0) {
            throw new IllegalArgumentException("reforkEvery is equal to or lower than zero!");
        }

        this.reforkEvery = reforkEvery;
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

    public Map<String, ReportConfig> getReportConfigs() {
        return reportConfigs;
    }

    public ReportConfig getReportConfig(String name) {
        return reportConfigs.get(name);
    }

    public void addReportConfing(ReportConfig reportConfig) {
        if (reportConfig == null) {
            throw new IllegalArgumentException("reportConfig can't be null!");
        }

        reportConfigs.put(reportConfig.getName(), reportConfig);
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