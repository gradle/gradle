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

import java.io.File;
import java.util.List;

import org.mortbay.jetty.plus.webapp.EnvConfiguration;
import org.mortbay.jetty.webapp.Configuration;
import org.mortbay.jetty.webapp.JettyWebXmlConfiguration;
import org.mortbay.jetty.webapp.TagLibConfiguration;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.jetty.webapp.WebInfConfiguration;

/**
 * Jetty6PluginWebAppContext
 */
public class JettyPluginWebAppContext extends WebAppContext {
    private List<File> classpathFiles;
    private File jettyEnvXmlFile;
    private File webXmlFile;
    private WebInfConfiguration webInfConfig = new WebInfConfiguration();
    private EnvConfiguration envConfig = new EnvConfiguration();
    private JettyConfiguration mvnConfig = new JettyConfiguration();
    private JettyWebXmlConfiguration jettyWebConfig = new JettyWebXmlConfiguration();
    private TagLibConfiguration tagConfig = new TagLibConfiguration();
    private Configuration[] configs = new Configuration[]{
            webInfConfig, envConfig, mvnConfig, jettyWebConfig, tagConfig
    };

    public JettyPluginWebAppContext() {
        super();
        setConfigurations(configs);
    }

    public void setClassPathFiles(List<File> classpathFiles) {
        this.classpathFiles = classpathFiles;
    }

    public List<File> getClassPathFiles() {
        return this.classpathFiles;
    }

    public void setWebXmlFile(File webXmlFile) {
        this.webXmlFile = webXmlFile;
    }

    public File getWebXmlFile() {
        return this.webXmlFile;
    }

    public void setJettyEnvXmlFile(File jettyEnvXmlFile) {
        this.jettyEnvXmlFile = jettyEnvXmlFile;
    }

    public File getJettyEnvXmlFile() {
        return this.jettyEnvXmlFile;
    }

    public void configure() {
        setConfigurations(configs);
        mvnConfig.setClassPathConfiguration(classpathFiles);
        mvnConfig.setWebXml(webXmlFile);
        try {
            if (this.jettyEnvXmlFile != null) {
                envConfig.setJettyEnvXml(this.jettyEnvXmlFile.toURI().toURL());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        /*
        Configuration[] configurations = getConfigurations();
        for (int i=0;i<configurations.length; i++)
        {
            if (configurations[i] instanceof JettyConfiguration)
            {
                ((JettyConfiguration)configurations[i]).setClassPathConfiguration (classpathFiles);
                ((JettyConfiguration)configurations[i]).setWebXml (webXmlFile);
            }
            else if (configurations[i] instanceof EnvConfiguration)
            {
                try
                {
                    if (this.jettyEnvXmlFile != null)
                        ((EnvConfiguration)configurations[i]).setJettyEnvXml(this.jettyEnvXmlFile.toURL());
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
        */
    }

    public void doStart() throws Exception {
        setShutdown(false);
        super.doStart();
    }

    public void doStop() throws Exception {
        setShutdown(true);
        //just wait a little while to ensure no requests are still being processed
        Thread.sleep(500L);
        super.doStop();
    }
}
