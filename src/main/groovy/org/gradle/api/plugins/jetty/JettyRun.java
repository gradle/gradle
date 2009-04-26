//========================================================================
//$Id: AbstractJettyRunTask.java 3649 2008-09-18 06:36:58Z dyu $
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
import org.gradle.api.plugins.jetty.util.ScanTargetPattern;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.resource.Resource;
import org.mortbay.resource.ResourceCollection;
import org.mortbay.util.Scanner;
import org.mortbay.xml.XmlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

import hidden.org.codehaus.plexus.util.FileUtils;

/**
 * <p>
 * This goal is used in-situ on a Maven project without first requiring that the project
 * is assembled into a war, saving time during the development cycle.
 * The plugin forks a parallel lifecycle to ensure that the "compile" phase has been completed before invoking Jetty. This means
 * that you do not need to explicity execute a "mvn compile" first. It also means that a "mvn clean jetty:run" will ensure that
 * a full fresh compile is done before invoking Jetty.
 * </p>
 * <p>
 * Once invoked, the plugin can be configured to run continuously, scanning for changes in the project and automatically performing a
 * hot redeploy when necessary. This allows the developer to concentrate on coding changes to the project using their IDE of choice and have those changes
 * immediately and transparently reflected in the running web container, eliminating development time that is wasted on rebuilding, reassembling and redeploying.
 * </p>
 * <p>
 * You may also specify the location of a jetty.xml file whose contents will be applied before any plugin configuration.
 * This can be used, for example, to deploy a static webapp that is not part of your maven build.
 * </p>
 * <p>
 * There is a <a href="run-mojo.html">reference guide</a> to the configuration parameters for this plugin, and more detailed information
 * with examples in the <a href="http://docs.codehaus.org/display/JETTY/Maven+Jetty+Plugin">Configuration Guide</a>.
 * </p>
 *
 * @author janb
 */
public class JettyRun extends AbstractJettyRunTask {
    private static Logger logger = LoggerFactory.getLogger(JettyRun.class);

    public JettyRun(Project project, String name) {
        super(project, name);
    }

    /**
     * List of other contexts to set up. Optional.
     */
    private ContextHandler[] contextHandlers;


    /**
     * If true, the &lt;testOutputDirectory&gt;
     * and the dependencies of &lt;scope&gt;test&lt;scope&gt;
     * will be put first on the runtime classpath.
     */
    private boolean useTestClasspath;


    /**
     * The location of a jetty-env.xml file. Optional.
     */
    private File jettyEnvXml;

    /**
     * The location of the web.xml file. If not
     * set then it is assumed it is in ${basedir}/src/main/webapp/WEB-INF
     */
    private File webXml;

    /**
     * The directory containing generated classes.
     */
    private File classesDirectory;

    /**
     * The directory containing generated test classes.
     */
    private File testClassesDirectory;

    /**
     * Root directory for all html/jsp etc files
     */
    private File webAppSourceDirectory;

    /**
     * List of files or directories to additionally periodically scan for changes. Optional.
     */
    private File[] scanTargets;


    /**
     * List of directories with ant-style &lt;include&gt; and &lt;exclude&gt; patterns
     * for extra targets to periodically scan for changes. Can be used instead of,
     * or in conjunction with &lt;scanTargets&gt;.Optional.
     */
    private ScanTargetPattern[] scanTargetPatterns;

    /**
     * web.xml as a File
     */
    private File webXmlFile;


    /**
     * jetty-env.xml as a File
     */
    private File jettyEnvXmlFile;

    /**
     * List of files on the classpath for the webapp
     */
    private List classPathFiles;


    /**
     * Extra scan targets as a list
     */
    private List extraScanTargets;

    private String configuration;

    private String testConfiguration;

    /**
     * Verify the configuration given in the pom.
     *
     * @see AbstractJettyRunTask#checkPomConfiguration()
     */
    public void checkPomConfiguration() {
        // check the location of the static content/jsps etc
        try {
            if ((getWebAppSourceDirectory() == null) || !getWebAppSourceDirectory().exists())
                throw new InvalidUserDataException("Webapp source directory "
                        + (getWebAppSourceDirectory() == null ? "null" : getWebAppSourceDirectory().getCanonicalPath())
                        + " does not exist");
            else
                logger.info("Webapp source directory = "
                        + getWebAppSourceDirectory().getCanonicalPath());
        }
        catch (IOException e) {
            throw new InvalidUserDataException("Webapp source directory does not exist", e);
        }

        // check reload mechanic
        if (!"automatic".equalsIgnoreCase(reload) && !"manual".equalsIgnoreCase(reload)) {
            throw new InvalidUserDataException("invalid reload mechanic specified, must be 'automatic' or 'manual'");
        } else {
            logger.info("Reload Mechanic: " + reload);
        }

        // get the web.xml file if one has been provided, otherwise assume it is
        // in the webapp src directory
        if (getWebXml() == null) {
            webXml = new File(new File(getWebAppSourceDirectory(), "WEB-INF"), "web.xml");
        }
        setWebXmlFile(getWebXml());

        try {
            if (!getWebXmlFile().exists())
                throw new InvalidUserDataException("web.xml does not exist at location "
                        + webXmlFile.getCanonicalPath());
            else
                logger.info("web.xml file = "
                        + webXmlFile.getCanonicalPath());
        }
        catch (IOException e) {
            throw new InvalidUserDataException("web.xml does not exist", e);
        }

        //check if a jetty-env.xml location has been provided, if so, it must exist
        if (getJettyEnvXml() != null) {
            setJettyEnvXmlFile(jettyEnvXml);

            try {
                if (!getJettyEnvXmlFile().exists())
                    throw new InvalidUserDataException("jetty-env.xml file does not exist at location " + jettyEnvXml);
                else
                    logger.info(" jetty-env.xml = " + getJettyEnvXmlFile().getCanonicalPath());
            }
            catch (IOException e) {
                throw new InvalidUserDataException("jetty-env.xml does not exist");
            }
        }

        // check the classes to form a classpath with
        try {
            //allow a webapp with no classes in it (just jsps/html)
            if (getClassesDirectory() != null) {
                if (!getClassesDirectory().exists())
                    logger.info("Classes directory " + getClassesDirectory().getCanonicalPath() + " does not exist");
                else
                    logger.info("Classes = " + getClassesDirectory().getCanonicalPath());
            } else
                logger.info("Classes directory not set");
        }
        catch (IOException e) {
            throw new InvalidUserDataException("Location of classesDirectory does not exist");
        }


        setExtraScanTargets(new ArrayList());
        if (scanTargets != null) {
            for (int i = 0; i < scanTargets.length; i++) {
                logger.info("Added extra scan target:" + scanTargets[i]);
                getExtraScanTargets().add(scanTargets[i]);
            }
        }


        if (scanTargetPatterns != null) {
            for (int i = 0; i < scanTargetPatterns.length; i++) {
                Iterator itor = scanTargetPatterns[i].getIncludes().iterator();
                StringBuffer strbuff = new StringBuffer();
                while (itor.hasNext()) {
                    strbuff.append((String) itor.next());
                    if (itor.hasNext())
                        strbuff.append(",");
                }
                String includes = strbuff.toString();

                itor = scanTargetPatterns[i].getExcludes().iterator();
                strbuff = new StringBuffer();
                while (itor.hasNext()) {
                    strbuff.append((String) itor.next());
                    if (itor.hasNext())
                        strbuff.append(",");
                }
                String excludes = strbuff.toString();

                try {
                    List files = FileUtils.getFiles(scanTargetPatterns[i].getDirectory(), includes, excludes);
                    itor = files.iterator();
                    while (itor.hasNext())
                        logger.info("Adding extra scan target from pattern: " + itor.next());
                    List currentTargets = getExtraScanTargets();
                    if (currentTargets != null && !currentTargets.isEmpty())
                        currentTargets.addAll(files);
                    else
                        setExtraScanTargets(files);
                }
                catch (IOException e) {
                    throw new InvalidUserDataException(e.getMessage());
                }
            }


        }
    }

    public void configureWebApplication() throws Exception {
        super.configureWebApplication();
        setClassPathFiles(setUpClassPath());
        if (webAppConfig.getWebXmlFile() == null)
            webAppConfig.setWebXmlFile(getWebXmlFile());
        if (webAppConfig.getJettyEnvXmlFile() == null)
            webAppConfig.setJettyEnvXmlFile(getJettyEnvXmlFile());
        if (webAppConfig.getClassPathFiles() == null)
            webAppConfig.setClassPathFiles(getClassPathFiles());
        if (webAppConfig.getWar() == null)
            webAppConfig.setWar(getWebAppSourceDirectory().getCanonicalPath());
        logger.info("Webapp directory = " + getWebAppSourceDirectory().getCanonicalPath());

        webAppConfig.configure();
    }

    public void configureScanner() {
        // start the scanner thread (if necessary) on the main webapp
        final ArrayList scanList = new ArrayList();
        scanList.add(getWebXmlFile());
        if (getJettyEnvXmlFile() != null)
            scanList.add(getJettyEnvXmlFile());
        File jettyWebXmlFile = findJettyWebXmlFile(new File(getWebAppSourceDirectory(), "WEB-INF"));
        if (jettyWebXmlFile != null)
            scanList.add(jettyWebXmlFile);
        scanList.addAll(getExtraScanTargets());
        scanList.add(getProject().getBuildFile());
        scanList.addAll(getClassPathFiles());
        setScanList(scanList);
        ArrayList listeners = new ArrayList();
        listeners.add(new Scanner.BulkListener() {
            public void filesChanged(List changes) {
                try {
                    boolean reconfigure = changes.contains(getProject().getBuildFile().getCanonicalPath());
                    restartWebApp(reconfigure);
                }
                catch (Exception e) {
                    logger.error("Error reconfiguring/restarting webapp after change in watched files", e);
                }
            }


        });
        setScannerListeners(listeners);
    }

    public void restartWebApp(boolean reconfigureScanner) throws Exception {
        logger.info("restarting " + webAppConfig);
        logger.debug("Stopping webapp ...");
        webAppConfig.stop();
        logger.debug("Reconfiguring webapp ...");

        checkPomConfiguration();
        configureWebApplication();

        // check if we need to reconfigure the scanner,
        // which is if the pom changes
        if (reconfigureScanner) {
            logger.info("Reconfiguring scanner after change to pom.xml ...");
            scanList.clear();
            scanList.add(getWebXmlFile());
            if (getJettyEnvXmlFile() != null)
                scanList.add(getJettyEnvXmlFile());
            scanList.addAll(getExtraScanTargets());
            scanList.add(getProject().getBuildFile());
            scanList.addAll(getClassPathFiles());
            getScanner().setScanDirs(scanList);
        }

        logger.debug("Restarting webapp ...");
        webAppConfig.start();
        logger.info("Restart completed at " + new Date().toString());
    }

    private Set getDependencyFiles() {
        List overlays = new ArrayList();

        Set<File> dependencies;
        if (useTestClasspath) {
            dependencies = getProject().getConfigurations().getByName(testConfiguration).resolve();
        } else {
            dependencies = getProject().getConfigurations().getByName(configuration).resolve();
        }
        logger.debug("Adding dependencies {} for WEB-INF/lib ", dependencies);

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
                Resource resource = webAppConfig.getBaseResource();
                ResourceCollection rc = new ResourceCollection();
                if (resource == null) {
                    // nothing configured, so we automagically enable the overlays                    
                    int size = overlays.size() + 1;
                    Resource[] resources = new Resource[size];
                    resources[0] = Resource.newResource(getWebAppSourceDirectory().toURL());
                    for (int i = 1; i < size; i++) {
                        resources[i] = (Resource) overlays.get(i - 1);
                        logger.info("Adding overlay: " + resources[i]);
                    }
                    rc.setResources(resources);
                } else {
                    if (resource instanceof ResourceCollection) {
                        // there was a preconfigured ResourceCollection ... append the artifact wars
                        Resource[] old = ((ResourceCollection) resource).getResources();
                        int size = old.length + overlays.size();
                        Resource[] resources = new Resource[size];
                        System.arraycopy(old, 0, resources, 0, old.length);
                        for (int i = old.length, j = 0; i < size; i++, j++) {
                            resources[i] = (Resource) overlays.get(j);
                            logger.info("Adding overlay: " + resources[i]);
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
                            resources[i] = (Resource) overlays.get(i - 1);
                            logger.info("Adding overlay: " + resources[i]);
                        }
                        rc.setResources(resources);
                    }
                }
                webAppConfig.setBaseResource(rc);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return dependencies;
    }


    private List setUpClassPath() {
        List classPathFiles = new ArrayList();

        //if using the test classes, make sure they are first
        //on the list
        if (useTestClasspath && (getTestClassesDirectory() != null))
            classPathFiles.add(getTestClassesDirectory());

        if (getClassesDirectory() != null)
            classPathFiles.add(getClassesDirectory());

        //now add all of the dependencies
        classPathFiles.addAll(getDependencyFiles());

        if (logger.isDebugEnabled()) {
            for (int i = 0; i < classPathFiles.size(); i++) {
                logger.debug("classpath element: " + ((File) classPathFiles.get(i)).getName());
            }
        }
        return classPathFiles;
    }

    public void finishConfigurationBeforeStart() throws Exception {
        Handler[] handlers = getConfiguredContextHandlers();
        org.gradle.api.plugins.jetty.util.JettyPluginServer plugin = getServer();
        Server server = (Server) plugin.getProxiedObject();

        HandlerCollection contexts = (HandlerCollection) server.getChildHandlerByClass(ContextHandlerCollection.class);
        if (contexts == null)
            contexts = (HandlerCollection) server.getChildHandlerByClass(HandlerCollection.class);

        for (int i = 0; (handlers != null) && (i < handlers.length); i++) {
            contexts.addHandler(handlers[i]);
        }
    }


    public void applyJettyXml() throws Exception {
        if (getJettyConfig() == null)
            return;

        logger.info("Configuring Jetty from xml configuration file = " + getJettyConfig());
        XmlConfiguration xmlConfiguration = new XmlConfiguration(getJettyConfig().toURL());
        xmlConfiguration.configure(getServer().getProxiedObject());
    }

    /**
     * @see JettyRun#createServer()
     */
    public org.gradle.api.plugins.jetty.util.JettyPluginServer createServer() {
        return new JettyPluginServer();
    }


    public File getClassesDirectory() {
        return (File) conv(classesDirectory, "classesDirectory");
    }

    public void setClassesDirectory(File classesDirectory) {
        this.classesDirectory = classesDirectory;
    }

    public File getJettyEnvXml() {
        return jettyEnvXml;
    }

    public void setJettyEnvXml(File jettyEnvXml) {
        this.jettyEnvXml = jettyEnvXml;
    }

    public File getWebXml() {
        return (File) conv(webXml, "webXml");
    }

    public void setWebXml(File webXml) {
        this.webXml = webXml;
    }

    public File getWebAppSourceDirectory() {
        return (File) conv(webAppSourceDirectory, "webAppSourceDirectory");
    }

    public void setWebAppSourceDirectory(File webAppSourceDirectory) {
        this.webAppSourceDirectory = webAppSourceDirectory;
    }

    public File getTestClassesDirectory() {
        return (File) conv(testClassesDirectory, "testClassesDirectory");
    }

    public void setTestClassesDirectory(File testClassesDirectory) {
        this.testClassesDirectory = testClassesDirectory;
    }

    public boolean isUseTestClasspath() {
        return useTestClasspath;
    }

    public void setUseTestClasspath(boolean useTestClasspath) {
        this.useTestClasspath = useTestClasspath;
    }

    public File[] getScanTargets() {
        return scanTargets;
    }

    public void setScanTargets(File[] scanTargets) {
        this.scanTargets = scanTargets;
    }

    public List getExtraScanTargets() {
        return extraScanTargets;
    }

    public void setExtraScanTargets(List extraScanTargets) {
        this.extraScanTargets = extraScanTargets;
    }

    public File getJettyEnvXmlFile() {
        return jettyEnvXmlFile;
    }

    public void setJettyEnvXmlFile(File jettyEnvXmlFile) {
        this.jettyEnvXmlFile = jettyEnvXmlFile;
    }

    public File getWebXmlFile() {
        return webXmlFile;
    }

    public void setWebXmlFile(File webXmlFile) {
        this.webXmlFile = webXmlFile;
    }

    public List getClassPathFiles() {
        return classPathFiles;
    }

    public void setClassPathFiles(List classPathFiles) {
        this.classPathFiles = classPathFiles;
    }

    public ScanTargetPattern[] getScanTargetPatterns() {
        return scanTargetPatterns;
    }

    public void setScanTargetPatterns(ScanTargetPattern[] scanTargetPatterns) {
        this.scanTargetPatterns = scanTargetPatterns;
    }

    /**
     * @return Returns the contextHandlers.
     */
    public ContextHandler[] getConfiguredContextHandlers() {
        return this.contextHandlers;
    }

    public void setContextHandlers(ContextHandler[] contextHandlers) {
        this.contextHandlers = contextHandlers;
    }

    /**
     * Returns the configuration to resolve the dependencies of the web application from.
     *
     * @see #getTestConfiguration()
     */
    public String getConfiguration() {
        return configuration;
    }

    /**
     * Set the configuration to resolve the dependencies of the web application from.
     * 
     * @see #setTestConfiguration(String)
     */
    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    /**
     * Returns the configuration to resolve the dependencies of the web application from, if
     * {@link #isUseTestClasspath()} is true.
     */
    public String getTestConfiguration() {
        return testConfiguration;
    }

    /**
     * Sets the configuration to resolve the dependencies of the web application from, if
     * {@link #isUseTestClasspath()} is true.
     */
    public void setTestConfiguration(String testConfiguration) {
        this.testConfiguration = testConfiguration;
    }
}
