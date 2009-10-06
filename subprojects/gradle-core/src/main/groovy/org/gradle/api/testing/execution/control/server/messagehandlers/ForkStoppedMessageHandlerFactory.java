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

import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.control.messages.TestControlMessageHandler;
import org.gradle.api.testing.execution.control.messages.TestControlMessageHandlerFactory;
import org.gradle.api.testing.execution.control.messages.client.ForkStoppedMessage;

import java.util.Arrays;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class ForkStoppedMessageHandlerFactory implements TestControlMessageHandlerFactory {
    private static final List<Class> supportedMessageClasses = Arrays.asList((Class) ForkStoppedMessage.class);

    public List<Class> getMessageClasses() {
        return supportedMessageClasses;
    }

    public TestControlMessageHandler createTestControlMessageHandler(PipelineDispatcher pipelineDispatcher) {
        return new ForkStoppedMessageHandler(pipelineDispatcher);
    }
}
