//========================================================================
//$Id: JettyRunWar.java 3591 2008-09-03 21:31:12Z jesse $
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.mortbay.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gradle.api.Project;

/**
 * <p>
 * This goal is used to assemble your webapp into a war and automatically deploy it to Jetty.
 * </p>
 * <p>
 * Once invoked, the plugin can be configured to run continuously, scanning for changes in the project and to the
 * war file and automatically performing a
 * hot redeploy when necessary.
 * </p>
 * <p>
 * You may also specify the location of a jetty.xml file whose contents will be applied before any plugin configuration.
 * This can be used, for example, to deploy a static webapp that is not part of your maven build.
 * </p>
 * <p>
 * There is a <a href="run-war-mojo.html">reference guide</a> to the configuration parameters for this plugin, and more detailed information
 * with examples in the <a href="http://docs.codehaus.org/display/JETTY/Maven+Jetty+Plugin/">Configuration Guide</a>.
 * </p>
 */
public class JettyRunWar extends AbstractJettyRunWarTask {
    private static Logger logger = LoggerFactory.getLogger(JettyRunWar.class);

    public JettyRunWar(Project project, String name) {
        super(project, name);
    }

    /**
     * The location of the war file.
     */
    private File webApp;

    public void configureWebApplication() throws Exception {
        super.configureWebApplication();

        webAppConfig.setWar(getWebApp().getCanonicalPath());
        webAppConfig.configure();
    }


    /**
     * @see AbstractJettyRunTask#checkPomConfiguration()
     */
    public void checkPomConfiguration() {
        return;
    }


    /* (non-Javadoc)
    * @see org.mortbay.jetty.plugin.util.AbstractJettyTask#configureScanner()
    */
    public void configureScanner() {
        final ArrayList scanList = new ArrayList();
        scanList.add(getProject().getBuildFile());
        scanList.add(getWebApp());
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
        logger.info("Restarting webapp ...");
        logger.debug("Stopping webapp ...");
        webAppConfig.stop();
        logger.debug("Reconfiguring webapp ...");

        checkPomConfiguration();

        // check if we need to reconfigure the scanner,
        // which is if the pom changes
        if (reconfigureScanner) {
            logger.info("Reconfiguring scanner after change to pom.xml ...");
            ArrayList scanList = getScanList();
            scanList.clear();
            scanList.add(getProject().getBuildFile());
            scanList.add(getWebApp());
            setScanList(scanList);
            getScanner().setScanDirs(scanList);
        }

        logger.debug("Restarting webapp ...");
        webAppConfig.start();
        logger.info("Restart completed.");
    }


    /**
     * @see AbstractJettyRunTask#finishConfigurationBeforeStart()
     */
    public void finishConfigurationBeforeStart() {
        return;
    }

    public File getWebApp() {
        return webApp;
    }

    public void setWebApp(File webApp) {
        this.webApp = webApp;
    }
}
