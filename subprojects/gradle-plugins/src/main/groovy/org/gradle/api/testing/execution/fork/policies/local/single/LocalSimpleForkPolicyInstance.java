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
package org.gradle.api.testing.execution.fork.policies.local.single;

import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.PipelineDispatcherForkInfoListener;
import org.gradle.api.testing.execution.QueueingPipeline;
import org.gradle.api.testing.execution.control.server.ControlServerFactory;
import org.gradle.api.testing.execution.control.server.TestServerClientHandleFactory;
import org.gradle.api.testing.execution.fork.ForkControl;
import org.gradle.api.testing.execution.fork.ForkInfo;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyForkInfo;
import org.gradle.api.testing.execution.fork.policies.ForkPolicyInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tom Eyckmans
 */
public class LocalSimpleForkPolicyInstance implements ForkPolicyInstance {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalSimpleForkPolicyInstance.class);

    private final QueueingPipeline pipeline;
    private final ForkControl forkControl;
    private final ControlServerFactory serverFactory;
    private final PipelineDispatcher pipelineDispatcher;

    public LocalSimpleForkPolicyInstance(QueueingPipeline pipeline, ForkControl forkControl,
                                         ControlServerFactory serverFactory) {
        this.serverFactory = serverFactory;
        this.pipeline = pipeline;
        this.forkControl = forkControl;
        pipelineDispatcher = new PipelineDispatcher(pipeline, new TestServerClientHandleFactory(forkControl));
    }

    public ForkPolicyForkInfo createForkPolicyForkInfo(ForkInfo forkInfo) {
        return new LocalSimpleForkPolicyForkInfo(forkInfo, serverFactory, forkControl, pipelineDispatcher);
    }

    public void initialize() {

        final int pipelineId = pipeline.getId();
        final int amountToStart = ((LocalSimpleForkPolicyConfig) pipeline.getConfig().getForkPolicyConfig())
                .getAmountToStart();

        LOGGER.info("Setting up test server & fork for pipeline {}", pipelineId);

        // TODO [teyck] I think it would be better to start the forks for a pipeline when a certain amount of tests are found.
        for (int i = 0; i < amountToStart; i++) {
            final ForkInfo forkInfo = forkControl.createForkInfo(pipeline);
            forkInfo.addListener(new PipelineDispatcherForkInfoListener(pipelineDispatcher));
            pipelineDispatcher.forkAttach(forkInfo.getId());

            forkControl.requestForkStart(forkInfo);
        }
    }

    public void stop() {
    }
}
