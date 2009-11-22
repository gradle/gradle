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

import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.api.testing.execution.control.refork.ReforkController;
import org.gradle.api.testing.execution.control.refork.ReforkControllerImpl;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyInstance;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.reporting.Report;
import org.gradle.util.queues.BlockingQueueItemProducer;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Tom Eyckmans
 */
public class Pipeline {
    private final PipelinesManager manager;
    private final int id;
    private final NativeTest testTask;
    private final BlockingQueue<TestClassRunInfo> runInfoQueue;
    private final BlockingQueueItemProducer<TestClassRunInfo> runInfoQueueProducer;
    private final PipelineConfig config;
    private ForkPolicyInstance forkPolicyInstance;
    private PipelineDispatcher dispatcher;
    private final ReforkController reforkController;
    private final AtomicBoolean pipelineSplittingEnded = new AtomicBoolean(Boolean.FALSE);
    private List<Report> reports;
    private List<PipelineListener> listeners;

    public Pipeline(PipelinesManager manager, int id, NativeTest testTask, PipelineConfig config) {
        if ( manager == null ) throw new IllegalArgumentException("manager is null!");
        if ( id <= 0 ) throw new IllegalArgumentException("id <= 0!");
        if ( testTask == null ) throw new IllegalArgumentException("testTask is null!");
        if ( config == null ) throw new IllegalArgumentException("config is null!");

        this.manager = manager;
        this.id = id;
        this.testTask = testTask;
        this.config = config;
        this.runInfoQueue = new ArrayBlockingQueue<TestClassRunInfo>(1000);
        this.runInfoQueueProducer = new BlockingQueueItemProducer<TestClassRunInfo>(runInfoQueue, 100L,
                TimeUnit.MILLISECONDS);
        this.reforkController = new ReforkControllerImpl();
        this.reports = new ArrayList<Report>();
        this.listeners = new ArrayList<PipelineListener>();
    }

    public int getId() {
        return id;
    }

    public String getName()
    {
        return config.getName();
    }

    public PipelineConfig getConfig() {
        return config;
    }

    public void addTestClassRunInfo(final TestClassRunInfo testClassRunInfo) {
        // TODO save to disk when full
        runInfoQueueProducer.produce(testClassRunInfo);
    }

    public BlockingQueue<TestClassRunInfo> getRunInfoQueue() {
        return runInfoQueue;
    }

    public NativeTest getTestTask() {
        return testTask;
    }

    public void setDispatcher(PipelineDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public PipelineDispatcher getDispatcher() {
        return dispatcher;
    }

    public ReforkController getReforkController() {
        return reforkController;
    }

    public ForkPolicyInstance getForkPolicyInstance() {
        return forkPolicyInstance;
    }

    public void setForkPolicyInstance(ForkPolicyInstance forkPolicyInstance) {
        this.forkPolicyInstance = forkPolicyInstance;
    }

    public void pipelineSplittingEnded() {
        pipelineSplittingEnded.set(Boolean.TRUE);
    }

    public boolean isPipelineSplittingEnded() {
        return pipelineSplittingEnded.get();
    }

    public void stopped() {
        forkPolicyInstance.stop();
        manager.stopped(this);
    }

    public List<Report> getReports() {
        return reports;
    }

    public void addReport(Report report) {
        this.reports.add(report);
        report.addPipeline(this);
    }

    public List<PipelineListener> getListeners() {
        return listeners;
    }

    public void addListener(PipelineListener listener) {
        listeners.add(listener);
    }
}
