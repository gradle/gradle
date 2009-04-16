//========================================================================
//$Id: AbstractJettyTask.java 4001 2008-11-06 07:54:23Z janb $
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


import org.gradle.api.*;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.plugins.jetty.util.*;
import org.gradle.api.plugins.jetty.util.JettyPluginServer;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.RequestLog;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.security.UserRealm;
import org.mortbay.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public abstract class AbstractJettyRunTask extends ConventionTask {
    private static Logger logger = LoggerFactory.getLogger(AbstractJettyRunTask.class);

    public AbstractJettyRunTask(Project project, String name) {
        super(project, name);
        doFirst(new TaskAction() {
            public void execute(Task task) {
                ClassLoader originalClassloader = Thread.currentThread().getContextClassLoader();
                List<URL> additionalClasspath = new ArrayList<URL>();
                for (File additionalRuntimeJar : additionalRuntimeJars) {
                    try {
                        additionalClasspath.add(additionalRuntimeJar.toURI().toURL());
                    } catch (MalformedURLException e) {
                        throw new InvalidUserDataException(e);
                    }
                }
                URLClassLoader jettyClassloader = new URLClassLoader(additionalClasspath.toArray(new URL[additionalClasspath.size()]), originalClassloader);
                try {
                    Thread.currentThread().setContextClassLoader(jettyClassloader);
                    startJetty();
                } finally {
                    Thread.currentThread().setContextClassLoader(originalClassloader);
                }
            }
        });
    }

    private List<File> additionalRuntimeJars = new ArrayList<File>();

    /**
     * The proxy for the Server object
     */
    protected JettyPluginServer server;


    /**
     * The "virtual" webapp created by the plugin
     */
    protected JettyPluginWebAppContext webAppConfig;

    /**
     * The context path for the webapp. Defaults to the
     * name of the webapp's artifact.
     */
    protected String contextPath;


    /**
     * The temporary directory to use for the webapp.
     * Defaults to target/jetty-tmp
     */
    protected File tmpDirectory;


    /**
     * A webdefault.xml file to use instead
     * of the default for the webapp. Optional.
     */
    protected File webDefaultXml;

    /**
     * A web.xml file to be applied AFTER
     * the webapp's web.xml file. Useful for
     * applying different build profiles, eg
     * test, production etc. Optional.
     */
    protected File overrideWebXml;


    /**
     * The interval in seconds to scan the webapp for changes
     * and restart the context if necessary. Ignored if reload
     * is enabled. Disabled by default.
     */
    protected int scanIntervalSeconds;


    /**
     * reload can be set to either 'automatic' or 'manual'
     * <p/>
     * if 'manual' then the context can be reloaded by a linefeed in the console
     * if 'automatic' then traditional reloading on changed files is enabled.
     */
    protected String reload;


    /**
     * System properties to set before execution.
     * Note that these properties will NOT override System properties
     * that have been set on the command line or by the JVM. Optional.
     */
    protected SystemProperties systemProperties;

    /**
     * Location of a jetty xml configuration file whose contents
     * will be applied before any plugin configuration. Optional.
     */
    protected File jettyConfig;

    /**
     * Port to listen to stop jetty on executing -DSTOP.PORT=&lt;stopPort&gt;
     * -DSTOP.KEY=&lt;stopKey&gt; -jar start.jar --stop
     */
    protected int stopPort;

    /**
     * Key to provide when stopping jetty on executing java -DSTOP.KEY=&lt;stopKey&gt;
     * -DSTOP.PORT=&lt;stopPort&gt; -jar start.jar --stop
     */
    protected String stopKey;

    /**
     * <p>
     * Determines whether or not the server blocks when started. The default
     * behavior (daemon = false) will cause the server to pause other processes
     * while it continues to handle web requests. This is useful when starting the
     * server with the intent to work with it interactively.
     * </p><p>
     * Often, it is desirable to let the server start and continue running subsequent
     * processes in an automated build environment. This can be facilitated by setting
     * daemon to true.
     * </p>
     */
    protected boolean daemon;

    /**
     * List of connectors to use. If none are configured
     * then we use a single SelectChannelConnector at port 8080
     */
    private Connector[] connectors;


    /**
     * List of security realms to set up. Optional.
     */
    private UserRealm[] userRealms;


    /**
     * A RequestLog implementation to use for the webapp at runtime.
     * Optional.
     */
    private RequestLog requestLog;

    /**
     * A scanner to check for changes to the webapp
     */
    protected Scanner scanner;

    /**
     * List of files and directories to scan
     */
    protected ArrayList scanList;


    /**
     * List of Listeners for the scanner
     */
    protected ArrayList scannerListeners;


    /**
     * A scanner to check ENTER hits on the console
     */
    protected Thread consoleScanner;

    public String PORT_SYSPROPERTY = "jetty.port";


    public abstract void checkPomConfiguration();


    public abstract void configureScanner();


    public abstract void applyJettyXml() throws Exception;


    /**
     * create a proxy that wraps a particular jetty version Server object
     *
     * @return The Jetty Plugin Server
     */
    public abstract JettyPluginServer createServer() throws Exception;


    public abstract void finishConfigurationBeforeStart() throws Exception;

    public JettyPluginServer getServer() {
        return this.server;
    }

    public void setServer(JettyPluginServer server) {
        this.server = server;
    }


    public void setScanList(ArrayList list) {
        this.scanList = new ArrayList(list);
    }

    public ArrayList getScanList() {
        return this.scanList;
    }


    public void setScannerListeners(ArrayList listeners) {
        this.scannerListeners = new ArrayList(listeners);
    }

    public ArrayList getScannerListeners() {
        return this.scannerListeners;
    }

    public Scanner getScanner() {
        return scanner;
    }

    public void startJetty() {
        logger.info("Configuring Jetty for " + getProject());
        checkPomConfiguration();
        startJettyInternal();
    }


    public void startJettyInternal() {
        try {
            logger.debug("Starting Jetty Server ...");

            printSystemProperties();
            setServer(createServer());

            //apply any config from a jetty.xml file first which is able to
            //be overwritten by config in the pom.xml
            applyJettyXml();

            JettyPluginServer plugin = getServer();

            // if the user hasn't configured their project's pom to use a
            // different set of connectors,
            // use the default
            Object[] configuredConnectors = getConnectors();

            plugin.setConnectors(configuredConnectors);
            Object[] connectors = plugin.getConnectors();

            if (connectors == null || connectors.length == 0) {
                //if a SystemProperty -Djetty.port=<portnum> has been supplied, use that as the default port
                configuredConnectors = new Object[]{plugin.createDefaultConnector(System.getProperty(PORT_SYSPROPERTY, null))};
                plugin.setConnectors(configuredConnectors);
            }

            //set up a RequestLog if one is provided
            if (getRequestLog() != null)
                getServer().setRequestLog(getRequestLog());

            //set up the webapp and any context provided
            getServer().configureHandlers();
            configureWebApplication();
            getServer().addWebApplication(webAppConfig);

            // set up security realms
            Object[] configuredRealms = getUserRealms();
            for (int i = 0; (configuredRealms != null) && i < configuredRealms.length; i++)
                logger.debug(configuredRealms[i].getClass().getName() + ": " + configuredRealms[i].toString());

            plugin.setUserRealms(configuredRealms);

            //do any other configuration required by the
            //particular Jetty version
            finishConfigurationBeforeStart();

            // start Jetty
            server.start();

            logger.info("Started Jetty Server");

            if (stopPort > 0 && stopKey != null) {
                Monitor monitor = new Monitor(stopPort, stopKey, new Server[]{(Server) server.getProxiedObject()}, !daemon);
                monitor.start();
            }

            // start the scanner thread (if necessary) on the main webapp
            configureScanner();
            startScanner();

            // start the new line scanner thread if necessary
            startConsoleScanner();

            // keep the thread going if not in daemon mode
            if (!daemon) {
                server.join();
            }
        }
        catch (Exception e) {
            throw new GradleException("Failure", e);
        }
        finally {
            if (!daemon) {
                logger.info("Jetty server exiting.");
            }
        }

    }


    public abstract void restartWebApp(boolean reconfigureScanner) throws Exception;

    /**
     * Subclasses should invoke this to setup basic info
     * on the webapp
     */
    public void configureWebApplication() throws Exception {
        //use EITHER a <webAppConfig> element or the now deprecated <contextPath>, <tmpDirectory>, <webDefaultXml>, <overrideWebXml>
        //way of doing things
        if (webAppConfig == null) {
            webAppConfig = new JettyPluginWebAppContext();
        }
        webAppConfig.setContextPath((getContextPath().startsWith("/") ? getContextPath() : "/" + getContextPath()));
        if (getTmpDirectory() != null) {
            webAppConfig.setTempDirectory(getTmpDirectory());
        }
        if (getWebDefaultXml() != null) {
            webAppConfig.setDefaultsDescriptor(getWebDefaultXml().getCanonicalPath());
        }
        if (getOverrideWebXml() != null) {
            webAppConfig.setOverrideDescriptor(getOverrideWebXml().getCanonicalPath());
        }

        logger.info("Context path = " + webAppConfig.getContextPath());
        logger.info("Tmp directory = " + " determined at runtime");
        logger.info("Web defaults = " + (webAppConfig.getDefaultsDescriptor() == null ? " jetty default" : webAppConfig.getDefaultsDescriptor()));
        logger.info("Web overrides = " + (webAppConfig.getOverrideDescriptor() == null ? " none" : webAppConfig.getOverrideDescriptor()));

    }

    /**
     * Run a scanner thread on the given list of files and directories, calling
     * stop/start on the given list of LifeCycle objects if any of the watched
     * files change.
     */
    private void startScanner() {

        // check if scanning is enabled
        if (getScanIntervalSeconds() <= 0) return;

        // check if reload is manual. It disables file scanning
        if ("manual".equalsIgnoreCase(reload)) {
            // issue a warning if both scanIntervalSeconds and reload
            // are enabled
            logger.warn("scanIntervalSeconds is set to " + scanIntervalSeconds + " but will be IGNORED due to manual reloading");
            return;
        }

        scanner = new Scanner();
        scanner.setReportExistingFilesOnStartup(false);
        scanner.setScanInterval(getScanIntervalSeconds());
        scanner.setScanDirs(getScanList());
        scanner.setRecursive(true);
        List listeners = getScannerListeners();
        Iterator itor = (listeners == null ? null : listeners.iterator());
        while (itor != null && itor.hasNext())
            scanner.addListener((Scanner.Listener) itor.next());
        logger.info("Starting scanner at interval of " + getScanIntervalSeconds() + " seconds.");
        scanner.start();
    }

    /**
     * Run a thread that monitors the console input to detect ENTER hits.
     */
    protected void startConsoleScanner() {
        if ("manual".equalsIgnoreCase(reload)) {
            logger.info("Console reloading is ENABLED. Hit ENTER on the console to restart the context.");
            consoleScanner = new ConsoleScanner(this);
            consoleScanner.start();
        }

    }

    private void printSystemProperties() {
        // print out which system properties were set up
        if (logger.isDebugEnabled()) {
            if (systemProperties != null) {
                Iterator itor = systemProperties.getSystemProperties().iterator();
                while (itor.hasNext()) {
                    SystemProperty prop = (SystemProperty) itor.next();
                    logger.debug("Property " + prop.getName() + "=" + prop.getValue() + " was " + (prop.isSet() ? "set" : "skipped"));
                }
            }
        }
    }

    /**
     * Try and find a jetty-web.xml file, using some
     * historical naming conventions if necessary.
     *
     * @param webInfDir
     * @return File object to the location of the jetty-web.xml
     */
    public File findJettyWebXmlFile(File webInfDir) {
        if (webInfDir == null)
            return null;
        if (!webInfDir.exists())
            return null;

        File f = new File(webInfDir, "jetty-web.xml");
        if (f.exists())
            return f;

        //try some historical alternatives
        f = new File(webInfDir, "web-jetty.xml");
        if (f.exists())
            return f;
        f = new File(webInfDir, "jetty6-web.xml");
        if (f.exists())
            return f;

        return null;
    }

    public File getTmpDirectory() {
        return (File) conv(tmpDirectory, "tmpDirectory");
    }

    public void setTmpDirectory(File tmpDirectory) {
        this.tmpDirectory = tmpDirectory;
    }

    public File getWebDefaultXml() {
        return webDefaultXml;
    }

    public void setWebDefaultXml(File webDefaultXml) {
        this.webDefaultXml = webDefaultXml;
    }

    public File getOverrideWebXml() {
        return overrideWebXml;
    }

    public void setOverrideWebXml(File overrideWebXml) {
        this.overrideWebXml = overrideWebXml;
    }

    public int getScanIntervalSeconds() {
        return scanIntervalSeconds;
    }

    public void setScanIntervalSeconds(int scanIntervalSeconds) {
        this.scanIntervalSeconds = scanIntervalSeconds;
    }

    public String getContextPath() {
        return (String) conv(contextPath, "contextPath");
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public JettyPluginWebAppContext getWebAppConfig() {
        return webAppConfig;
    }

    public void setWebAppConfig(JettyPluginWebAppContext webAppConfig) {
        this.webAppConfig = webAppConfig;
    }

    public String getReload() {
        return reload;
    }

    public void setReload(String reload) {
        this.reload = reload;
    }

    public SystemProperties getSystemProperties() {
        return systemProperties;
    }

    public void setSystemProperties(SystemProperties systemProperties) {
        this.systemProperties = systemProperties;
    }

    public File getJettyConfig() {
        return jettyConfig;
    }

    public void setJettyConfig(File jettyConfig) {
        this.jettyConfig = jettyConfig;
    }

    public int getStopPort() {
        return stopPort;
    }

    public void setStopPort(int stopPort) {
        this.stopPort = stopPort;
    }

    public String getStopKey() {
        return stopKey;
    }

    public void setStopKey(String stopKey) {
        this.stopKey = stopKey;
    }

    public boolean isDaemon() {
        return daemon;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public Connector[] getConnectors() {
        return connectors;
    }

    public void setConnectors(Connector[] connectors) {
        this.connectors = connectors;
    }

    public UserRealm[] getUserRealms() {
        return userRealms;
    }

    public void setUserRealms(UserRealm[] userRealms) {
        this.userRealms = userRealms;
    }

    public RequestLog getRequestLog() {
        return requestLog;
    }

    public void setRequestLog(RequestLog requestLog) {
        this.requestLog = requestLog;
    }

    public List<File> getAdditionalRuntimeJars() {
        return additionalRuntimeJars;
    }

    public void setAdditionalRuntimeJars(List<File> additionalRuntimeJars) {
        this.additionalRuntimeJars = additionalRuntimeJars;
    }
}
