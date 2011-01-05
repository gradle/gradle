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

package org.gradle.api.plugins.jetty.internal;

import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.RequestLog;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mortbay.jetty.handler.RequestLogHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.UserRealm;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jetty6PluginServer <p/> Jetty6 version of a wrapper for the Server class.
 */
public class Jetty6PluginServer implements JettyPluginServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Jetty6PluginServer.class);

    public static final int DEFAULT_MAX_IDLE_TIME = 30000;
    private Server server;
    private ContextHandlerCollection contexts; //the list of ContextHandlers
    HandlerCollection handlers; //the list of lists of Handlers
    private RequestLogHandler requestLogHandler; //the request log handler
    private DefaultHandler defaultHandler; //default handler

    private RequestLog requestLog; //the particular request log implementation

    public Jetty6PluginServer() {
        this.server = new Server();
        this.server.setStopAtShutdown(true);
        //make sure Jetty does not use URLConnection caches with the plugin
        Resource.setDefaultUseCaches(false);
    }

    /**
     * @see Jetty6PluginServer#setConnectors(Object[])
     */
    public void setConnectors(Object[] connectors) {
        if (connectors == null || connectors.length == 0) {
            return;
        }

        for (int i = 0; i < connectors.length; i++) {
            Connector connector = (Connector) connectors[i];
            LOGGER.debug("Setting Connector: " + connector.getClass().getName() + " on port " + connector.getPort());
            this.server.addConnector(connector);
        }
    }

    /**
     * @see org.gradle.api.plugins.jetty.internal.JettyPluginServer#getConnectors()
     */
    public Object[] getConnectors() {
        return this.server.getConnectors();
    }

    /**
     * @see Jetty6PluginServer#setUserRealms(Object[])
     */
    public void setUserRealms(Object[] realms) throws Exception {
        if (realms == null) {
            return;
        }

        for (int i = 0; i < realms.length; i++) {
            this.server.addUserRealm((UserRealm) realms[i]);
        }
    }

    /**
     * @see org.gradle.api.plugins.jetty.internal.JettyPluginServer#getUserRealms()
     */
    public Object[] getUserRealms() {
        return this.server.getUserRealms();
    }

    public void setRequestLog(Object requestLog) {
        this.requestLog = (RequestLog) requestLog;
    }

    public Object getRequestLog() {
        return this.requestLog;
    }

    /**
     * @see org.gradle.api.plugins.jetty.internal.JettyPluginServer#start()
     */
    public void start() throws Exception {
        LOGGER.info("Starting jetty " + this.server.getClass().getPackage().getImplementationVersion() + " ...");
        this.server.start();
    }

    /**
     * @see org.gradle.api.plugins.jetty.internal.Proxy#getProxiedObject()
     */
    public Object getProxiedObject() {
        return this.server;
    }

    /**
     * @see Jetty6PluginServer#addWebApplication
     */
    public void addWebApplication(WebAppContext webapp) throws Exception {
        contexts.addHandler(webapp);
    }

    /**
     * Set up the handler structure to receive a webapp. Also put in a DefaultHandler so we get a nice page than a 404
     * if we hit the root and the webapp's context isn't at root.
     */
    public void configureHandlers() throws Exception {
        this.defaultHandler = new DefaultHandler();
        this.requestLogHandler = new RequestLogHandler();
        if (this.requestLog != null) {
            this.requestLogHandler.setRequestLog(this.requestLog);
        }

        this.contexts = (ContextHandlerCollection) server.getChildHandlerByClass(ContextHandlerCollection.class);
        if (this.contexts == null) {
            this.contexts = new ContextHandlerCollection();
            this.handlers = (HandlerCollection) server.getChildHandlerByClass(HandlerCollection.class);
            if (this.handlers == null) {
                this.handlers = new HandlerCollection();
                this.server.setHandler(handlers);
                this.handlers.setHandlers(new Handler[]{this.contexts, this.defaultHandler, this.requestLogHandler});
            } else {
                this.handlers.addHandler(this.contexts);
            }
        }
    }

    public Object createDefaultConnector(int port) throws Exception {
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(port);
        connector.setMaxIdleTime(DEFAULT_MAX_IDLE_TIME);

        return connector;
    }

    public void join() throws Exception {
        this.server.getThreadPool().join();
    }
}
