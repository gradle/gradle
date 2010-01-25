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
package org.gradle.api.testing.execution.control.server.messagehandlers;

import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.QueueingPipeline;
import org.gradle.api.testing.execution.control.messages.TestControlMessageHandler;
import org.gradle.api.testing.execution.control.messages.server.StopForkActionMessage;
import org.gradle.api.testing.execution.control.messages.server.TestServerControlMessage;
import org.gradle.api.testing.execution.control.server.TestServerClientHandle;
import org.gradle.messaging.dispatch.Dispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CompositeMessageHandler implements TestControlMessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompositeMessageHandler.class);
    private final Map<Class<?>, TestControlMessageHandler> handlers
            = new ConcurrentHashMap<Class<?>, TestControlMessageHandler>();
    private final PipelineDispatcher pipelineDispatcher;

    public CompositeMessageHandler(PipelineDispatcher pipelineDispatcher) {
        this.pipelineDispatcher = pipelineDispatcher;
    }

    public Set<Class<?>> getMessageClasses() {
        return handlers.keySet();
    }

    public void handle(Object controlMessage, TestServerClientHandle client,
                       Dispatch<TestServerControlMessage> clientConnection) {
        Class<?> messageClass = controlMessage.getClass();
        TestControlMessageHandler handler = handlers.get(messageClass);
        if (handler != null) {
            handler.handle(controlMessage, client, clientConnection);
        } else {
            QueueingPipeline pipeline = pipelineDispatcher.getPipeline();
            LOGGER.error("received unknown message of type {} on pipeline ", messageClass, pipeline.getName());
            clientConnection.dispatch(new StopForkActionMessage(pipeline.getId()));
        }
    }

    public void add(TestControlMessageHandler handler) {
        for (Class<?> messageClass : handler.getMessageClasses()) {
            handlers.put(messageClass, handler);
        }
    }
}
