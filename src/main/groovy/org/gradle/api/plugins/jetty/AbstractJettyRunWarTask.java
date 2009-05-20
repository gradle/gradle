//========================================================================
//$Id: AbstractJettyWarTask.java 2147 2007-10-23 05:08:49Z gregw $
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
import org.mortbay.xml.XmlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractJettyRunWarTask extends AbstractJettyRunTask {
    private static Logger logger = LoggerFactory.getLogger(AbstractJettyRunWarTask.class);

    public AbstractJettyRunWarTask(Project project, String name) {
        super(project, name);
    }

    public void applyJettyXml() throws Exception {

        if (getJettyConfig() == null)
            return;

        logger.info("Configuring Jetty from xml configuration file = {}", getJettyConfig());
        XmlConfiguration xmlConfiguration = new XmlConfiguration(getJettyConfig().toURI().toURL());
        xmlConfiguration.configure(getServer().getProxiedObject());
    }


    /**
     * @see AbstractJettyRunTask#createServer()
     */
    public org.gradle.api.plugins.jetty.util.JettyPluginServer createServer() throws Exception {
        return new JettyPluginServer();
    }



}
