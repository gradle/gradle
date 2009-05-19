//========================================================================
//$Id: Monitor.java 4005 2008-11-06 22:31:53Z janb $
//Copyright 2008 Mort Bay Consulting Pty. Ltd.
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


package org.gradle.api.plugins.jetty.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import org.mortbay.jetty.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Monitor
 * <p/>
 * Listens for stop commands eg via mvn jetty:stop and
 * causes jetty to stop either by exiting the jvm, or
 * by stopping the Server instances. The choice of
 * behaviour is controlled by either passing true
 * (exit jvm) or false (stop Servers) in the constructor.
 */
public class Monitor extends Thread {
    private static Logger logger = LoggerFactory.getLogger(Monitor.class);
    
    private String _key;
    private Server[] _servers;

    ServerSocket _serverSocket;
    boolean _kill;

    public Monitor(int port, String key, Server[] servers, boolean kill)
            throws IOException {
        if (port <= 0)
            throw new IllegalStateException("Bad stop port");
        if (key == null)
            throw new IllegalStateException("Bad stop key");

        _key = key;
        _servers = servers;
        _kill = kill;
        setDaemon(true);
        setName("StopJettyPluginMonitor");
        _serverSocket = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"));
        _serverSocket.setReuseAddress(true);
    }

    public void run() {
        while (_serverSocket != null) {
            Socket socket = null;
            try {
                socket = _serverSocket.accept();
                socket.setSoLinger(false, 0);
                LineNumberReader lin = new LineNumberReader(new InputStreamReader(socket.getInputStream()));

                String key = lin.readLine();
                if (!_key.equals(key)) continue;
                String cmd = lin.readLine();
                if ("stop".equals(cmd)) {
                    try {
                        socket.close();
                    } catch (Exception e) {
                        logger.debug("Exception when stopping server", e);
                    }
                    try {
                        socket.close();
                    } catch (Exception e) {
                        logger.debug("Exception when stopping server", e);
                    }
                    try {
                        _serverSocket.close();
                    } catch (Exception e) {
                        logger.debug("Exception when stopping server", e);
                    }

                    _serverSocket = null;

                    if (_kill) {
                        logger.info("Killing Jetty");
                        System.exit(0);
                    } else {
                        for (int i = 0; _servers != null && i < _servers.length; i++) {
                            try {
                                logger.info("Stopping server " + i);
                                _servers[i].stop();
                            }
                            catch (Exception e) {
                                logger.error("Exception when stopping server", e);
                            }
                        }
                    }
                } else
                    logger.info("Unsupported monitor operation");
            }
            catch (Exception e) {
                logger.error("Exception during monitoring Server", e);
            }
            finally {
                if (socket != null) {
                    try {
                        socket.close();
                    }
                    catch (Exception e) {
                        logger.debug("Exception when stopping server", e);
                    }
                }
                socket = null;
            }
        }
    }
}
