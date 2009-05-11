//========================================================================
//$Id: JettyRunWarExploded.java 3591 2008-09-03 21:31:12Z jesse $
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

import org.gradle.api.Project;
import org.mortbay.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JettyRunWarExploded extends AbstractJettyRunWarTask {
    private static Logger logger = LoggerFactory.getLogger(JettyRunWarExploded.class);

    public JettyRunWarExploded(Project project, String name) {
        super(project, name);
    }

    /**
     * The location of the war file.
     */
    private File webApp;

    /**
     * @see AbstractJettyRunTask#validateConfiguration()
     */
    public void validateConfiguration() {
    }

    /**
     * @see AbstractJettyRunTask#configureScanner()
     */
    public void configureScanner() {
        List<File> scanList = new ArrayList<File>();
        scanList.add(getProject().getBuildFile());
        File webInfDir = new File(webApp, "WEB-INF");
        scanList.add(new File(webInfDir, "web.xml"));
        File jettyWebXmlFile = findJettyWebXmlFile(webInfDir);
        if (jettyWebXmlFile != null)
            scanList.add(jettyWebXmlFile);
        File jettyEnvXmlFile = new File(webInfDir, "jetty-env.xml");
        if (jettyEnvXmlFile.exists())
            scanList.add(jettyEnvXmlFile);
        scanList.add(new File(webInfDir, "classes"));
        scanList.add(new File(webInfDir, "lib"));
        getScanner().setScanDirs(scanList);

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
        logger.info("Restarting webapp");
        logger.debug("Stopping webapp ...");
        getWebAppConfig().stop();
        logger.debug("Reconfiguring webapp ...");

        validateConfiguration();

        // check if we need to reconfigure the scanner
        if (reconfigureScanner) {
            logger.info("Reconfiguring scanner after change to pom.xml ...");
            List<File> scanList = new ArrayList<File>();
            scanList.add(getProject().getBuildFile());
            File webInfDir = new File(webApp, "WEB-INF");
            scanList.add(new File(webInfDir, "web.xml"));
            File jettyWebXmlFile = findJettyWebXmlFile(webInfDir);
            if (jettyWebXmlFile != null)
                scanList.add(jettyWebXmlFile);
            File jettyEnvXmlFile = new File(webInfDir, "jetty-env.xml");
            if (jettyEnvXmlFile.exists())
                scanList.add(jettyEnvXmlFile);
            scanList.add(new File(webInfDir, "classes"));
            scanList.add(new File(webInfDir, "lib"));
            getScanner().setScanDirs(scanList);
        }

        logger.debug("Restarting webapp ...");
        getWebAppConfig().start();
        logger.info("Restart completed.");
    }


    /* (non-Javadoc)
    * @see org.mortbay.jetty.plugin.util.AbstractJettyTask#finishConfigurationBeforeStart()
    */
    public void finishConfigurationBeforeStart() throws Exception {
    }


    public void configureWebApplication() throws Exception {
        super.configureWebApplication();
        getWebAppConfig().setWar(webApp.getCanonicalPath());
        getWebAppConfig().configure();
    }

    public File getWebApp() {
        return webApp;
    }

    public void setWebApp(File webApp) {
        this.webApp = webApp;
    }
}
