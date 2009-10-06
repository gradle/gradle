package org.gradle.api.tasks.testing;

import org.gradle.api.*;
import org.gradle.api.tasks.*;
import org.gradle.api.testing.TestOrchestrator;
import org.gradle.api.testing.execution.PipelineConfig;
import org.gradle.util.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class NativeTest extends AbstractTestTask {
    private static final Logger logger = LoggerFactory.getLogger(NativeTest.class);

    private long reforkEvery = Long.MAX_VALUE;

    private List<PipelineConfig> pipelineConfigs;

    private int maximumNumberOfForks = 1;

    public NativeTest() {
        super();
        pipelineConfigs = new ArrayList<PipelineConfig>();
        pipelineConfigs.add(new PipelineConfig()); // add default pipeline.
    }

    public void executeTests() {
        final File testClassesDir = getTestClassesDir();

        if (testClassesDir == null)
            throw new InvalidUserDataException("The testClassesDir property is not set, testing can't be triggered!");
        if (getTestResultsDir() == null)
            throw new InvalidUserDataException("The testResultsDir property is not set, testing can't be triggered!");

        existingDirsFilter.checkExistenceAndThrowStopActionIfNot(testClassesDir);

        final TestOrchestrator orchestrator = new TestOrchestrator(this);

        orchestrator.execute();

        // TODO move to a test detection ended listener and zero detected tests
        // logger.debug("skipping test execution, because no tests were found");

        // TODO add reporting
        //if (testReport) {
        //    testFrameworkInstance.report(getProject(), this);
        //}

        // TODO don't use ant project property?
        if (stopAtFailuresOrErrors && GUtil.isTrue(getProject().getAnt().getProject().getProperty(FAILURES_OR_ERRORS_PROPERTY))) {
            if (testReport)
                throw new GradleException("There were failing tests. See the report at " + getTestReportDir() + ".");
            else
                throw new GradleException("There were failing tests.");
        }
    }

    public long getReforkEvery() {
        return reforkEvery;
    }

    public void setReforkEvery(long reforkEvery) {
        if (reforkEvery <= 0) throw new IllegalArgumentException("reforkEvery is equal to or lower than zero!");

        this.reforkEvery = reforkEvery;
    }

    public List<PipelineConfig> getPipelineConfigs() {
        return pipelineConfigs;
    }

    public void setPipelineConfig(PipelineConfig pipelineConfig) {
        if (pipelineConfig == null) throw new IllegalArgumentException("pipelineConfig can't be null!");

        pipelineConfigs.set(0, pipelineConfig);
    }

    public void addPipelineConfig(PipelineConfig pipelineConfig) {
        if (pipelineConfig == null) throw new IllegalArgumentException("pipelineConfig can't be null!");

        pipelineConfigs.add(pipelineConfig);
    }

    public int getMaximumNumberOfForks() {
        return maximumNumberOfForks;
    }

    public void setMaximumNumberOfForks(int maximumNumberOfForks) {
        if (maximumNumberOfForks < 1) throw new IllegalArgumentException("maximumNumberOfForks can't be lower than 1!");

        this.maximumNumberOfForks = maximumNumberOfForks;
    }
}