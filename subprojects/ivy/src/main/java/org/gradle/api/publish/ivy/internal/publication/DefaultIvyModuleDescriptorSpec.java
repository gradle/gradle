/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publication;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.XmlProvider;
import org.gradle.api.internal.UserCodeAction;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfiguration;

import org.gradle.api.publish.ivy.IvyExtraInfoSpec;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependencyInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;
import org.gradle.listener.ActionBroadcast;

import java.util.Set;

public class DefaultIvyModuleDescriptorSpec implements IvyModuleDescriptorSpecInternal {

    private final ActionBroadcast<XmlProvider> xmlActions = new ActionBroadcast<XmlProvider>();
    private final IvyPublicationInternal ivyPublication;
    private String status = Module.DEFAULT_STATUS;
    private String branch;
    private IvyExtraInfoSpec extraInfo = new DefaultIvyExtraInfoSpec();

    public DefaultIvyModuleDescriptorSpec(IvyPublicationInternal ivyPublication) {
        this.ivyPublication = ivyPublication;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public IvyExtraInfoSpec getExtraInfo() {
        return extraInfo;
    }

    public void extraInfo(String namespace, String elementName, String value) {
        if (elementName == null) {
            throw new InvalidUserDataException("Cannot add an extra info element with null element name");
        }
        if (namespace == null) {
            throw new InvalidUserDataException("Cannot add an extra info element with null namespace");
        }
        extraInfo.add(namespace, elementName, value);
    }

    public IvyPublicationIdentity getProjectIdentity() {
        return ivyPublication.getIdentity();
    }

    public void withXml(Action<? super XmlProvider> action) {
        xmlActions.add(new UserCodeAction<XmlProvider>("Could not apply withXml() to Ivy module descriptor", action));
    }

    public Action<XmlProvider> getXmlAction() {
        return xmlActions;
    }

    public Set<IvyConfiguration> getConfigurations() {
        return ivyPublication.getConfigurations();
    }

    public Set<IvyArtifact> getArtifacts() {
        return ivyPublication.getArtifacts();
    }

    public Set<IvyDependencyInternal> getDependencies() {
        return ivyPublication.getDependencies();
    }
}
