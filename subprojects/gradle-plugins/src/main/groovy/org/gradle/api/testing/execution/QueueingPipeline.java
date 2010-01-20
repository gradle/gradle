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

import org.gradle.api.tasks.testing.NativeTest;
import org.gradle.api.testing.execution.control.refork.DefaultReforkControl;
import org.gradle.api.testing.execution.control.refork.ReforkControl;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyInstance;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.reporting.Report;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.util.queues.BlockingQueueItemProducer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Tom Eyckmans
 */
public class QueueingPipeline implements Pipeline {
    private final PipelinesManager manager;
    private final int id;
    private final NativeTest testTask;
    private final BlockingQueue<TestClassRunInfo> runInfoQueue;
    private final BlockingQueueItemProducer<TestClassRunInfo> runInfoQueueProducer;
    private final PipelineConfig config;
    private ForkPolicyInstance forkPolicyInstance;
    private final ReforkControl reforkControl;
    private final AtomicBoolean pipelineSplittingEnded = new AtomicBoolean(Boolean.FALSE);
    private final List<Report> reports;
    private final ListenerBroadcast<PipelineListener> broadcast = new ListenerBroadcast<PipelineListener>(PipelineListener.class);

    public QueueingPipeline(PipelinesManager manager, int id, NativeTest testTask, PipelineConfig config) {
        this.manager = manager;
        this.id = id;
        this.testTask = testTask;
        this.config = config;
        this.runInfoQueue = new ArrayBlockingQueue<TestClassRunInfo>(1000);
        this.runInfoQueueProducer = new BlockingQueueItemProducer<TestClassRunInfo>(runInfoQueue, 100L,
                TimeUnit.MILLISECONDS);
        this.reforkControl = new DefaultReforkControl();
        this.reports = new ArrayList<Report>();
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

    public void processTestClass(TestClassRunInfo testClass) {
        // TODO save to disk when full
        runInfoQueueProducer.produce(testClass);
    }

    public BlockingQueue<TestClassRunInfo> getRunInfoQueue() {
        return runInfoQueue;
    }

    public NativeTest getTestTask() {
        return testTask;
    }

    public ReforkControl getReforkController() {
        return reforkControl;
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
        broadcast.getSource().pipelineStopped(this);
        manager.stopped(this);
    }

    public List<Report> getReports() {
        return reports;
    }

    public void addReport(Report report) {
        this.reports.add(report);
        report.addPipeline(this);
    }

    public void addListener(PipelineListener listener) {
        broadcast.add(listener);
    }
}
