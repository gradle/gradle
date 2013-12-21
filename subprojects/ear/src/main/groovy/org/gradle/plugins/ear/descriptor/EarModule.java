/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ear.descriptor;

import groovy.util.Node;

/**
 * A module element in a deployment descriptor like application.xml.
 */
public interface EarModule {

    /**
     * The connector element specifies the URI of an archive file, relative to the top level of the application package.
     */
    public String getPath();

    public void setPath(String path);

    /**
     * The alt-dd element specifies an optional URI to the post-assembly version of the deployment descriptor file for a
     * particular Java EE module. The URI must specify the full pathname of the deployment descriptor file relative to
     * the application's root directory. If alt-dd is not specified, the deployer must read the deployment descriptor
     * from the default location and file name required by the respective component specification.
     */
    public String getAltDeployDescriptor();

    public void setAltDeployDescriptor(String altDeployDescriptor);

    /**
     * Convert this object to an XML Node (or two nodes if altDeployDescriptor is not null).
     * 
     * @param parentModule
     *            The parent &lt;module&gt; node to add this node to.
     * @param name
     *            The name of this node.
     * @return The new node. If an &lt;alt-dd&gt; node is created it is not returned.
     */
    public Node toXmlNode(Node parentModule, Object name);
}