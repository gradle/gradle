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
package org.gradle.api.testing.execution.control.server.messagehandlers;

import org.apache.mina.core.session.IoSession;
import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.control.messages.client.NextActionRequestMessage;
import org.gradle.api.testing.execution.control.messages.server.ExecuteTestActionMessage;
import org.gradle.api.testing.execution.control.messages.server.StopForkActionMessage;
import org.gradle.api.testing.execution.control.messages.server.WaitActionMesssage;
import org.gradle.api.testing.execution.control.refork.ReforkDecisionContext;
import org.gradle.api.testing.execution.fork.ForkStatus;
import org.gradle.api.testing.fabric.TestClassProcessResult;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.reporting.Report;
import org.gradle.api.testing.reporting.TestClassProcessResultReportInfo;

import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class NextActionRequestMessageHandler extends AbstractTestServerControlMessageHandler {
    protected NextActionRequestMessageHandler(PipelineDispatcher pipelineDispatcher) {
        super(pipelineDispatcher);
    }

    public void handle(IoSession ioSession, Object controlMessage) {
        final NextActionRequestMessage message = (NextActionRequestMessage) controlMessage;
        final int forkId = message.getForkId();

        if (pipelineDispatcher.isStopping()) {
            ioSession.write(new StopForkActionMessage(pipeline.getId()));
        } else {
            if (pipelineDispatcher.isAllTestsExecuted() && pipelineDispatcher.isPipelineSplittingEnded()) {
                ioSession.write(new StopForkActionMessage(pipeline.getId()));

                pipelineDispatcher.stop();
            } else {
                final TestClassProcessResult previousProcessedTestResult = message.getPreviousProcessedTestResult();
                if (previousProcessedTestResult != null) {
                    // TODO dispatch previous test result to handle reporting
                    final List<Report> reports = pipeline.getReports();
                    for ( final Report report : reports ) {
                        report.addReportInfo(new TestClassProcessResultReportInfo(pipeline, previousProcessedTestResult));
                    }
                }

                final ReforkDecisionContext reforkDecisionContext = message.getReforkDecisionContext();
                boolean reforkNeeded = false;
                if (reforkDecisionContext != null) {
                    reforkNeeded = pipelineDispatcher.determineReforkNeeded(forkId, reforkDecisionContext);
                }

                if (reforkNeeded) {
                    pipelineDispatcher.scheduleForkRestart(forkId);

                    ioSession.write(new StopForkActionMessage(pipeline.getId()));
                } else {
                    boolean wait = false;

                    if (pipelineDispatcher.getClientHandle(forkId).getStatus() == ForkStatus.TESTING) {
                        TestClassRunInfo nextTest = pipelineDispatcher.getNextTest();

                        if (nextTest != null) {
                            ioSession.write(new ExecuteTestActionMessage(pipeline.getId(), nextTest));
                        } else { // no tests for pipeline yet
                            wait = true;
                        }
                    } else { // fork not in RUN_TEST mode
                        wait = !reforkNeeded;
                    }

                    if (wait) {
                        ioSession.write(new WaitActionMesssage(pipeline.getId(), 1000));
                    }
                }
            }
        }
    }
}
