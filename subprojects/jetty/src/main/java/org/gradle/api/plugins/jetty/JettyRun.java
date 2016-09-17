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

import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.jetty.internal.Jetty6PluginServer;
import org.gradle.api.plugins.jetty.internal.JettyPluginServer;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mortbay.resource.Resource;
import org.mortbay.resource.ResourceCollection;
import org.mortbay.util.Scanner;
import org.mortbay.xml.XmlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * <p>Deploys an exploded web application to an embedded Jetty web container. Does not require that the web application
 * be assembled into a war, saving time during the development cycle.</p>
 *
 * <p>Once started, the web container can be configured to run continuously, scanning for changes in the project and
 * automatically performing a hot redeploy when necessary. This allows the developer to concentrate on coding changes to
 * the project using their IDE of choice and have those changes immediately and transparently reflected in the running
 * web container, eliminating development time that is wasted on rebuilding, reassembling and redeploying. </p>
 *
 * @deprecated The Jetty plugin has been deprecated
 */
@Deprecated
public class JettyRun extends AbstractJettyRunTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(JettyRun.class);

    /**
     * List of other contexts to set up. Optional.
     */
    private ContextHandler[] contextHandlers;

    /**
     * The location of a jetty-env.xml file. Optional.
     */
    private File jettyEnvXml;

    /**
     * The location of the web.xml file. If not set then it is assumed it is in ${basedir}/src/main/webapp/WEB-INF
     */
    private File webXml;

    /**
     * Root directory for all HTML/JSP etc files.
     */
    private File webAppSourceDirectory;

    /**
     * List of files or directories to additionally periodically scan for changes. Optional.
     */
    private File[] scanTargets;

    /**
     * List of directories with ant-style &lt;include&gt; and &lt;exclude&gt; patterns for extra targets to periodically
     * scan for changes. Can be used instead of, or in conjunction with &lt;scanTargets&gt;.Optional.
     */
    private ScanTargetPattern[] scanTargetPatterns;

    /**
     * jetty-env.xml as a File.
     */
    private File jettyEnvXmlFile;

    /**
     * List of files on the classpath for the webapp.
     */
    private List<File> classPathFiles;

    /**
     * Extra scan targets as a list.
     */
    private Set<File> extraScanTargets;

    private FileCollection classpath;

    public void validateConfiguration() {
        // check the location of the static content/jsps etc
        try {
            if ((getWebAppSourceDirectory() == null) || !getWebAppSourceDirectory().exists()) {
                throw new InvalidUserDataException("Webapp source directory "
                        + (getWebAppSourceDirectory() == null ? "null" : getWebAppSourceDirectory().getCanonicalPath())
                        + " does not exist");
            } else {
                LOGGER.info("Webapp source directory = " + getWebAppSourceDirectory().getCanonicalPath());
            }
        } catch (IOException e) {
            throw new InvalidUserDataException("Webapp source directory does not exist", e);
        }

        // check reload mechanic
        if (!"automatic".equalsIgnoreCase(reload) && !"manual".equalsIgnoreCase(reload)) {
            throw new InvalidUserDataException("invalid reload mechanic specified, must be 'automatic' or 'manual'");
        } else {
            LOGGER.info("Reload Mechanic: " + reload);
        }

        // get the web.xml file if one has been provided, otherwise assume it is in the webapp src directory
        if (getWebXml() == null) {
            setWebXml(new File(new File(getWebAppSourceDirectory(), "WEB-INF"), "web.xml"));
        }
        LOGGER.info("web.xml file = " + getWebXml());

        //check if a jetty-env.xml location has been provided, if so, it must exist
        if (getJettyEnvXml() != null) {
            setJettyEnvXmlFile(jettyEnvXml);

            try {
                if (!getJettyEnvXmlFile().exists()) {
                    throw new InvalidUserDataException("jetty-env.xml file does not exist at location " + jettyEnvXml);
                } else {
                    LOGGER.info(" jetty-env.xml = " + getJettyEnvXmlFile().getCanonicalPath());
                }
            } catch (IOException e) {
                throw new InvalidUserDataException("jetty-env.xml does not exist");
            }
        }

        setExtraScanTargets(new ArrayList<File>());
        if (scanTargets != null) {
            for (File scanTarget : scanTargets) {
                LOGGER.info("Added extra scan target:" + scanTarget);
                getExtraScanTargets().add(scanTarget);
            }
        }

        if (scanTargetPatterns != null) {
            for (ScanTargetPattern scanTargetPattern : scanTargetPatterns) {
                ConfigurableFileTree files = getProject().fileTree(scanTargetPattern.getDirectory());
                files.include(scanTargetPattern.getIncludes());
                files.exclude(scanTargetPattern.getExcludes());
                Set<File> currentTargets = getExtraScanTargets();
                if (currentTargets != null && !currentTargets.isEmpty()) {
                    currentTargets.addAll(files.getFiles());
                } else {
                    setExtraScanTargets(files.getFiles());
                }
            }
        }
    }

    public void configureWebApplication() throws Exception {
        super.configureWebApplication();
        setClassPathFiles(setUpClassPath());
        if (getWebAppConfig().getWebXmlFile() == null) {
            getWebAppConfig().setWebXmlFile(getWebXml());
        }
        if (getWebAppConfig().getJettyEnvXmlFile() == null) {
            getWebAppConfig().setJettyEnvXmlFile(getJettyEnvXmlFile());
        }
        if (getWebAppConfig().getClassPathFiles() == null) {
            getWebAppConfig().setClassPathFiles(getClassPathFiles());
        }
        if (getWebAppConfig().getWar() == null) {
            getWebAppConfig().setWar(getWebAppSourceDirectory().getCanonicalPath());
        }
        LOGGER.info("Webapp directory = " + getWebAppSourceDirectory().getCanonicalPath());

        getWebAppConfig().configure();
    }

    public void configureScanner() {
        // start the scanner thread (if necessary) on the main webapp
        List<File> scanList = new ArrayList<File>();
        scanList.add(getWebXml());
        if (getJettyEnvXmlFile() != null) {
            scanList.add(getJettyEnvXmlFile());
        }
        File jettyWebXmlFile = findJettyWebXmlFile(new File(getWebAppSourceDirectory(), "WEB-INF"));
        if (jettyWebXmlFile != null) {
            scanList.add(jettyWebXmlFile);
        }
        scanList.addAll(getExtraScanTargets());
        scanList.add(getProject().getBuildFile());
        scanList.addAll(getClassPathFiles());
        getScanner().setScanDirs(scanList);
        List<Scanner.Listener> listeners = new ArrayList<Scanner.Listener>();
        listeners.add(new Scanner.BulkListener() {
            public void filesChanged(List changes) {
                try {
                    boolean reconfigure = changes.contains(getProject().getBuildFile().getCanonicalPath());
                    restartWebApp(reconfigure);
                } catch (Exception e) {
                    LOGGER.error("Error reconfiguring/restarting webapp after change in watched files", e);
                }
            }
        });
        setScannerListeners(listeners);
    }

    public void restartWebApp(boolean reconfigureScanner) throws Exception {
        LOGGER.info("restarting " + getWebAppConfig());
        LOGGER.debug("Stopping webapp ...");
        getWebAppConfig().stop();
        LOGGER.debug("Reconfiguring webapp ...");

        validateConfiguration();
        configureWebApplication();

        // check if we need to reconfigure the scanner
        if (reconfigureScanner) {
            LOGGER.info("Reconfiguring scanner ...");
            List<File> scanList = new ArrayList<File>();
            scanList.add(getWebXml());
            if (getJettyEnvXmlFile() != null) {
                scanList.add(getJettyEnvXmlFile());
            }
            scanList.addAll(getExtraScanTargets());
            scanList.add(getProject().getBuildFile());
            scanList.addAll(getClassPathFiles());
            getScanner().setScanDirs(scanList);
        }

        LOGGER.debug("Restarting webapp ...");
        getWebAppConfig().start();
        LOGGER.info("Restart completed at " + new Date().toString());
    }

    @Internal
    private Set<File> getDependencyFiles() {
        List<Resource> overlays = new ArrayList<Resource>();

        Set<File> dependencies = getClasspath().getFiles();
        LOGGER.debug("Adding dependencies {} for WEB-INF/lib ", dependencies);

        //todo incorporate overlays when our resolved dependencies provide type information
//            if (artifact.getType().equals("war")) {
//                try {
//                    Resource r = Resource.newResource("jar:" + artifact.getFile().toURL().toString() + "!/");
//                    overlays.add(r);
//                    getExtraScanTargets().add(artifact.getFile());
//                }
//                catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//                continue;
//            }
        if (!overlays.isEmpty()) {
            try {
                Resource resource = getWebAppConfig().getBaseResource();
                ResourceCollection rc = new ResourceCollection();
                if (resource == null) {
                    // nothing configured, so we automagically enable the overlays
                    int size = overlays.size() + 1;
                    Resource[] resources = new Resource[size];
                    resources[0] = Resource.newResource(getWebAppSourceDirectory().toURI().toURL());
                    for (int i = 1; i < size; i++) {
                        resources[i] = overlays.get(i - 1);
                        LOGGER.info("Adding overlay: " + resources[i]);
                    }
                    rc.setResources(resources);
                } else {
                    if (resource instanceof ResourceCollection) {
                        // there was a preconfigured ResourceCollection ... append the artifact wars
                        Resource[] old = ((ResourceCollection) resource).getResources();
                        int size = old.length + overlays.size();
                        Resource[] resources = new Resource[size];
                        System.arraycopy(old, 0, resources, 0, old.length);
                        for (int i = old.length; i < size; i++) {
                            resources[i] = overlays.get(i - old.length);
                            LOGGER.info("Adding overlay: " + resources[i]);
                        }
                        rc.setResources(resources);
                    } else {
                        // baseResource was already configured w/c could be src/main/webapp
                        if (!resource.isDirectory() && String.valueOf(resource.getFile()).endsWith(".war")) {
                            // its a war
                            resource = Resource.newResource("jar:" + resource.getURL().toString() + "!/");
                        }
                        int size = overlays.size() + 1;
                        Resource[] resources = new Resource[size];
                        resources[0] = resource;
                        for (int i = 1; i < size; i++) {
                            resources[i] = overlays.get(i - 1);
                            LOGGER.info("Adding overlay: " + resources[i]);
                        }
                        rc.setResources(resources);
                    }
                }
                getWebAppConfig().setBaseResource(rc);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return dependencies;
    }

    private List<File> setUpClassPath() {
        List<File> classPathFiles = new ArrayList<File>();

        classPathFiles.addAll(getDependencyFiles());

        if (LOGGER.isDebugEnabled()) {
            for (File classPathFile : classPathFiles) {
                LOGGER.debug("classpath element: " + classPathFile.getName());
            }
        }
        return classPathFiles;
    }

    public void finishConfigurationBeforeStart() throws Exception {
        Handler[] handlers = getConfiguredContextHandlers();
        org.gradle.api.plugins.jetty.internal.JettyPluginServer plugin = getServer();
        Server server = (Server) plugin.getProxiedObject();

        HandlerCollection contexts = (HandlerCollection) server.getChildHandlerByClass(ContextHandlerCollection.class);
        if (contexts == null) {
            contexts = (HandlerCollection) server.getChildHandlerByClass(HandlerCollection.class);
        }

        for (int i = 0; (handlers != null) && (i < handlers.length); i++) {
            contexts.addHandler(handlers[i]);
        }
    }

    public void applyJettyXml() throws Exception {
        if (getJettyConfig() == null) {
            return;
        }

        LOGGER.info("Configuring Jetty from xml configuration file = " + getJettyConfig());
        XmlConfiguration xmlConfiguration = new XmlConfiguration(getJettyConfig().toURI().toURL());
        xmlConfiguration.configure(getServer().getProxiedObject());
    }

    public JettyPluginServer createServer() {
        return new Jetty6PluginServer();
    }

    @InputFile
    @Optional
    public File getJettyEnvXml() {
        return jettyEnvXml;
    }

    public void setJettyEnvXml(File jettyEnvXml) {
        this.jettyEnvXml = jettyEnvXml;
    }

    /**
     * Returns the {@code web.xml} file to use. When {@code null}, no {@code web.xml} file is used.
     */
    @Internal("See webXmlIfExists")
    public File getWebXml() {
        return webXml;
    }

    // Workaround for non-existent web.xml passed to this task
    @Optional @InputFile
    protected File getWebXmlIfExists() {
        File webXml = getWebXml();
        if (webXml != null && webXml.exists()) {
            return webXml;
        } else {
            return null;
        }
    }

    public void setWebXml(File webXml) {
        this.webXml = webXml;
    }

    /**
     * Returns the directory containing the web application source files.
     */
    @InputDirectory
    public File getWebAppSourceDirectory() {
        return webAppSourceDirectory;
    }

    public void setWebAppSourceDirectory(File webAppSourceDirectory) {
        this.webAppSourceDirectory = webAppSourceDirectory;
    }

    @Internal
    public File[] getScanTargets() {
        return scanTargets;
    }

    public void setScanTargets(File[] scanTargets) {
        this.scanTargets = scanTargets;
    }

    @Internal
    public Set<File> getExtraScanTargets() {
        return extraScanTargets;
    }

    public void setExtraScanTargets(Iterable<File> extraScanTargets) {
        this.extraScanTargets = Sets.newLinkedHashSet(extraScanTargets);
    }

    @InputFile
    @Optional
    public File getJettyEnvXmlFile() {
        return jettyEnvXmlFile;
    }

    public void setJettyEnvXmlFile(File jettyEnvXmlFile) {
        this.jettyEnvXmlFile = jettyEnvXmlFile;
    }

    @Internal
    public List<File> getClassPathFiles() {
        return classPathFiles;
    }

    public void setClassPathFiles(List<File> classPathFiles) {
        this.classPathFiles = classPathFiles;
    }

    @Internal
    public ScanTargetPattern[] getScanTargetPatterns() {
        return scanTargetPatterns;
    }

    public void setScanTargetPatterns(ScanTargetPattern[] scanTargetPatterns) {
        this.scanTargetPatterns = scanTargetPatterns;
    }

    @Internal
    public ContextHandler[] getConfiguredContextHandlers() {
        return this.contextHandlers;
    }

    public void setContextHandlers(ContextHandler[] contextHandlers) {
        this.contextHandlers = contextHandlers;
    }

    /**
     * Returns the classpath for the web application.
     */
    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    /**
     * Set the classpath for the web application.
     */
    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }
}
