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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyModuleDescriptorAuthor;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.IvyModuleDescriptorDescription;
import org.gradle.api.publish.ivy.IvyExtraInfoSpec;
import org.gradle.api.publish.ivy.IvyModuleDescriptorLicense;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependencyInternal;
import org.gradle.api.publish.ivy.internal.dependency.IvyExcludeRule;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;
import org.gradle.internal.MutableActionSet;
import org.gradle.internal.reflect.Instantiator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefaultIvyModuleDescriptorSpec implements IvyModuleDescriptorSpecInternal {

    private final MutableActionSet<XmlProvider> xmlActions = new MutableActionSet<XmlProvider>();
    private final IvyPublicationInternal ivyPublication;
    private final Instantiator instantiator;
    private final ObjectFactory objectFactory;
    private String status = Module.DEFAULT_STATUS;
    private String branch;
    private IvyExtraInfoSpec extraInfo = new DefaultIvyExtraInfoSpec();
    private final List<IvyModuleDescriptorAuthor> authors = new ArrayList<IvyModuleDescriptorAuthor>();
    private final List<IvyModuleDescriptorLicense> licenses = new ArrayList<IvyModuleDescriptorLicense>();
    private IvyModuleDescriptorDescription description;

    public DefaultIvyModuleDescriptorSpec(IvyPublicationInternal ivyPublication, Instantiator instantiator, ObjectFactory objectFactory) {
        this.ivyPublication = ivyPublication;
        this.instantiator = instantiator;
        this.objectFactory = objectFactory;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String getBranch() {
        return branch;
    }

    @Override
    public void setBranch(String branch) {
        this.branch = branch;
    }

    @Override
    public IvyExtraInfoSpec getExtraInfo() {
        return extraInfo;
    }

    @Override
    public void extraInfo(String namespace, String elementName, String value) {
        if (elementName == null) {
            throw new InvalidUserDataException("Cannot add an extra info element with null element name");
        }
        if (namespace == null) {
            throw new InvalidUserDataException("Cannot add an extra info element with null namespace");
        }
        extraInfo.add(namespace, elementName, value);
    }

    @Override
    public IvyPublicationIdentity getProjectIdentity() {
        return ivyPublication.getIdentity();
    }

    @Override
    public void withXml(Action<? super XmlProvider> action) {
        xmlActions.add(new UserCodeAction<XmlProvider>("Could not apply withXml() to Ivy module descriptor", action));
    }

    @Override
    public Action<XmlProvider> getXmlAction() {
        return xmlActions;
    }

    @Override
    public Set<IvyConfiguration> getConfigurations() {
        return ivyPublication.getConfigurations();
    }

    @Override
    public Set<IvyArtifact> getArtifacts() {
        return ivyPublication.getArtifacts();
    }

    @Override
    public Set<IvyDependencyInternal> getDependencies() {
        return ivyPublication.getDependencies();
    }

    @Override
    public Set<IvyExcludeRule> getGlobalExcludes() {
        return ivyPublication.getGlobalExcludes();
    }

    @Override
    public void license(Action<? super IvyModuleDescriptorLicense> action) {
        configureAndAdd(DefaultIvyModuleDescriptorLicense.class, action, licenses);
    }

    @Override
    public List<IvyModuleDescriptorLicense> getLicenses() {
        return licenses;
    }

    @Override
    public void author(Action<? super IvyModuleDescriptorAuthor> action) {
        configureAndAdd(DefaultIvyModuleDescriptorAuthor.class, action, authors);
    }

    @Override
    public List<IvyModuleDescriptorAuthor> getAuthors() {
        return authors;
    }

    @Override
    public void description(Action<? super IvyModuleDescriptorDescription> action) {
        if (description == null) {
            description = instantiator.newInstance(DefaultIvyModuleDescriptorDescription.class, objectFactory);
        }
        action.execute(description);
    }

    @Override
    public IvyModuleDescriptorDescription getDescription() {
        return description;
    }

    @Override
    public VersionMappingStrategyInternal getVersionMappingStrategy() {
        return ivyPublication.getVersionMappingStrategy();
    }

    @Override
    public boolean writeGradleMetadataMarker() {
        return ivyPublication.writeGradleMetadataMarker();
    }

    private <T> void configureAndAdd(Class<? extends T> clazz, Action<? super T> action, List<T> items) {
        T item = instantiator.newInstance(clazz, objectFactory);
        action.execute(item);
        items.add(item);
    }
}
