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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.IvyExtraInfoSpec;
import org.gradle.api.publish.ivy.IvyModuleDescriptorAuthor;
import org.gradle.api.publish.ivy.IvyModuleDescriptorDescription;
import org.gradle.api.publish.ivy.IvyModuleDescriptorLicense;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependency;
import org.gradle.api.publish.ivy.internal.dependency.IvyExcludeRule;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates;
import org.gradle.internal.MutableActionSet;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class DefaultIvyModuleDescriptorSpec implements IvyModuleDescriptorSpecInternal {

    private final MutableActionSet<XmlProvider> xmlActions = new MutableActionSet<>();
    private final ObjectFactory objectFactory;
    private final IvyPublicationCoordinates ivyPublicationCoordinates;
    private String status;
    private String branch;
    private final IvyExtraInfoSpec extraInfo = new DefaultIvyExtraInfoSpec();
    private final List<IvyModuleDescriptorAuthor> authors = new ArrayList<>();
    private final List<IvyModuleDescriptorLicense> licenses = new ArrayList<>();
    private IvyModuleDescriptorDescription description;

    @Inject
    public DefaultIvyModuleDescriptorSpec(ObjectFactory objectFactory, IvyPublicationCoordinates ivyPublicationCoordinates) {
        this.objectFactory = objectFactory;
        this.ivyPublicationCoordinates = ivyPublicationCoordinates;
    }

    @Nullable
    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public void setStatus(@Nullable String status) {
        this.status = status;
    }

    @Nullable
    @Override
    public String getBranch() {
        return branch;
    }

    @Override
    public void setBranch(@Nullable String branch) {
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
    public IvyPublicationCoordinates getCoordinates() {
        return ivyPublicationCoordinates;
    }

    @Override
    public void withXml(Action<? super XmlProvider> action) {
        xmlActions.add(new UserCodeAction<>("Could not apply withXml() to Ivy module descriptor", action));
    }

    @Override
    public Action<XmlProvider> getXmlAction() {
        return xmlActions;
    }

    @Override
    public abstract SetProperty<IvyConfiguration> getConfigurations();

    @Override
    public abstract SetProperty<IvyArtifact> getArtifacts();

    @Override
    public abstract SetProperty<IvyDependency> getDependencies();

    @Override
    public abstract SetProperty<IvyExcludeRule> getGlobalExcludes();

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
            description = objectFactory.newInstance(DefaultIvyModuleDescriptorDescription.class, objectFactory);
        }
        action.execute(description);
    }

    @Override
    public IvyModuleDescriptorDescription getDescription() {
        return description;
    }

    @Override
    public abstract Property<Boolean> getWriteGradleMetadataMarker();

    private <T> void configureAndAdd(Class<? extends T> clazz, Action<? super T> action, List<T> items) {
        T item = objectFactory.newInstance(clazz, objectFactory);
        action.execute(item);
        items.add(item);
    }
}
