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
import org.gradle.api.testing.execution.control.server.TestServerClientHandle;
import org.gradle.api.testing.execution.control.messages.server.InitializeActionMessage;
import org.gradle.api.tasks.testing.NativeTest;

/**
 * @author Tom Eyckmans
 */
public class ForkStartedMessageHandler extends AbstractTestServerControlMessageHandler {

    public ForkStartedMessageHandler(PipelineDispatcher pipelineDispatcher) {
        super(pipelineDispatcher);
    }

    public void handle(IoSession ioSession, Object controlMessage, TestServerClientHandle client) {
        client.started();

        final InitializeActionMessage initializeForkMessage = new InitializeActionMessage(pipeline.getId());
        final NativeTest testTask = pipeline.getTestTask();

        initializeForkMessage.setTestFrameworkId(testTask.getTestFramework().getTestFramework().getId());
        initializeForkMessage.setReforkItemConfigs(pipeline.getConfig().getReforkReasonConfigs());
        // TODO add sandbox classpath ?

        ioSession.write(initializeForkMessage);
    }
}
