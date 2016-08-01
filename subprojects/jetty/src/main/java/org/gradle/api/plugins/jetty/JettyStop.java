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

package org.gradle.api.plugins.jetty;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.internal.ConventionTask;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Stops the embedded Jetty web container, if it is running.
 *
 * @deprecated The Jetty plugin has been deprecated
 */
@Deprecated
public class JettyStop extends ConventionTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(JettyStop.class);

    private Integer stopPort;

    private String stopKey;

    @TaskAction
    public void stop() {
        if (getStopPort() == null) {
            throw new InvalidUserDataException("Please specify a valid port");
        }
        if (getStopKey() == null) {
            throw new InvalidUserDataException("Please specify a valid stopKey");
        }

        ProgressLogger progressLogger = getServices().get(ProgressLoggerFactory.class).newOperation(JettyStop.class)
                .start("Stop Jetty server", "Stopping Jetty");
        try {
            Socket s = new Socket(InetAddress.getByName("127.0.0.1"), getStopPort());
            s.setSoLinger(false, 0);

            OutputStream out = s.getOutputStream();
            out.write((getStopKey() + "\r\nstop\r\n").getBytes());
            out.flush();
            s.close();
        } catch (ConnectException e) {
            LOGGER.info("Jetty not running!");
        } catch (Exception e) {
            LOGGER.error("Exception during stopping", e);
        } finally {
            progressLogger.completed();
        }
    }

    /**
     * Returns the TCP port to use to send stop command.
     */
    @Internal
    public Integer getStopPort() {
        return stopPort;
    }

    /**
     * Sets the TCP port to use to send stop command.
     */
    public void setStopPort(Integer stopPort) {
        this.stopPort = stopPort;
    }

    /**
     * Returns the stop key.
     *
     * @see #setStopKey(String)
     */
    @Internal
    public String getStopKey() {
        return stopKey;
    }

    /**
     * Sets key to provide when stopping jetty.
     */
    public void setStopKey(String stopKey) {
        this.stopKey = stopKey;
    }
}
