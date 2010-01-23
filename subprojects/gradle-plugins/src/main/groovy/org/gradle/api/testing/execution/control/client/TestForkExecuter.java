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
package org.gradle.api.testing.execution.control.client;

import org.gradle.api.testing.execution.control.messages.client.ForkStartedMessage;
import org.gradle.api.testing.execution.control.messages.client.ForkStoppedMessage;
import org.gradle.api.testing.execution.control.messages.client.TestClientControlMessage;
import org.gradle.api.testing.execution.fork.ForkExecuter;
import org.gradle.messaging.TcpMessagingClient;
import org.gradle.messaging.dispatch.Dispatch;

import java.net.URI;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class TestForkExecuter implements ForkExecuter {
    private ClassLoader sharedClassLoader;
    private ClassLoader controlClassLoader;
    private ClassLoader sandboxClassLoader;
    private List<String> arguments;

    public void execute() {
        try {
            final int forkId = Integer.parseInt(arguments.get(1));
            final URI testServerUri = new URI(arguments.get(2));

            TcpMessagingClient client = new TcpMessagingClient(getClass().getClassLoader(), testServerUri);
            try {
                Dispatch<TestClientControlMessage> outgoing = client.getConnection().addOutgoing(Dispatch.class);
                outgoing.dispatch(new ForkStartedMessage(forkId));

                TestControlMessageDispatcher testControlMessageDispatcher = new TestControlMessageDispatcher(forkId, outgoing, sandboxClassLoader);
                testControlMessageDispatcher.waitForExitReceived();

                outgoing.dispatch(new ForkStoppedMessage(forkId));
            } finally {
                client.stop();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void setSharedClassLoader(ClassLoader sharedClassLoader) {
        this.sharedClassLoader = sharedClassLoader;
    }

    public void setControlClassLoader(ClassLoader controlClassLoader) {
        this.controlClassLoader = controlClassLoader;
    }

    public void setSandboxClassLoader(ClassLoader sandboxClassLoader) {
        this.sandboxClassLoader = sandboxClassLoader;
    }

    public void setArguments(List<String> arguments) {
        this.arguments = arguments;
    }
}
