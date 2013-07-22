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
package org.gradle.foundation.ipc.gradle;

import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Just some convenience functions to startup a GradleClient. See GradleClient for more information.
 */
public class IPCUtilities {
    private static final Logger LOGGER = Logging.getLogger(IPCUtilities.class);

    /**
     * This starts a gradle client for doing regular execution of a command. It expects the port number to set as a system property. Note: this is using gradle to find the port. See getPort().
     *
     * @param gradle the gradle object.
     */
    public static void invokeExecuteGradleClient(Gradle gradle) {
        Integer port = getPort(gradle);
        if (port == null) {
            return;
        }

        ExecuteGradleCommandClientProtocol protocol = new ExecuteGradleCommandClientProtocol(gradle);
        GradleClient client = new GradleClient();
        client.start(protocol, port);
    }

    /**
     * This gets the port out of the start parameters. Why? Because this is meant to be run from the init script and the system properties haven't been set yet. That is due to how gradle is run from
     * the bat file/shell script. It has to manually set the java system properties (-D). I don't this is a desired side-effect.
     *
     * @param gradle the gradle object
     * @return an integer or null if we didn't get the port.
     */
    private static Integer getPort(Gradle gradle) {
        String portText = gradle.getStartParameter().getSystemPropertiesArgs().get(ProtocolConstants.PORT_NUMBER_SYSTEM_PROPERTY);
        if (portText == null) {
            LOGGER.error("Failed to set " + ProtocolConstants.PORT_NUMBER_SYSTEM_PROPERTY + " system property");
            return null;
        }

        try {
            return Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            LOGGER.error("Invalid " + ProtocolConstants.PORT_NUMBER_SYSTEM_PROPERTY + " system property", e);
            return null;
        }
    }

    /**
     * This starts a gradle client that sends a task list back to the server. It expects the port number to set as a system property. You probably should be executing the "tasks" command. Note: this
     * is using gradle to find the port. See getPort().
     *
     * @param gradle the gradle launcher object.
     */
    public static void invokeTaskListGradleClient(Gradle gradle) {
        Integer port = getPort(gradle);
        if (port == null) {
            return;
        }

        TaskListClientProtocol protocol = new TaskListClientProtocol(gradle);
        GradleClient client = new GradleClient();
        client.start(protocol, port);
    }
}
