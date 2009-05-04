//========================================================================
//$Id: JettyStop.java 4005 2008-11-06 22:31:53Z janb $
//Copyright 2000-2004 Mort Bay Consulting Pty. Ltd.
//------------------------------------------------------------------------
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at 
//http://www.apache.org/licenses/LICENSE-2.0
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
//========================================================================

package org.gradle.api.plugins.jetty;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.internal.ConventionTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;

public class JettyStop extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(JettyStop.class);

    private Integer stopPort;

    private String stopKey;

    public JettyStop(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                stop();
            }
        });
    }

    public void stop() {
        if (getStopPort() == null)
            throw new InvalidUserDataException("Please specify a valid port");
        if (getStopKey() == null)
            throw new InvalidUserDataException("Please specify a valid stopKey");

        try {
            Socket s = new Socket(InetAddress.getByName("127.0.0.1"), getStopPort());
            s.setSoLinger(false, 0);

            OutputStream out = s.getOutputStream();
            out.write((getStopKey() + "\r\nstop\r\n").getBytes());
            out.flush();
            s.close();
        }
        catch (ConnectException e) {
            logger.info("Jetty not running!");
        }
        catch (Exception e) {
            logger.error("Exception during stopping", e);
        }
    }

    /**
     * Returns port to listen to stop jetty on sending stop command
     */
    public Integer getStopPort() {
        return stopPort;
    }

    /**
     * Sets port to listen to stop jetty on sending stop command
     */
    public void setStopPort(Integer stopPort) {
        this.stopPort = stopPort;
    }

    /**
     * Returns stop key.
     *
     * @see #setStopKey(String)
     */
    public String getStopKey() {
        return stopKey;
    }

    /**
     * Sets key to provide when stopping jetty on executing java -DSTOP.KEY=&lt;stopKey&gt;
     * -DSTOP.PORT=&lt;stopPort&gt; -jar start.jar --stop
     */
    public void setStopKey(String stopKey) {
        this.stopKey = stopKey;
    }
}
